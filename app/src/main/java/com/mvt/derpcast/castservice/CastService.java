package com.mvt.derpcast.castservice;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;
import com.mvt.derpcast.R;
import com.mvt.derpcast.activities.MainActivity;
import com.mvt.derpcast.device.DeviceAdapter;
import com.mvt.derpcast.media.MediaAdapter;
import com.mvt.derpcast.media.MediaInfo;

public class CastService extends IntentService {

    public static final String ACTION_START = "com.mvt.derpcast.action.START";
    public static final String ACTION_NOTIFICATION_PLAY = "com.mvt.derpcast.action.NOTIFICATION_PLAY";
    public static final String ACTION_NOTIFICATION_PAUSE = "com.mvt.derpcast.action.NOTIFICATION_PAUSE";
    public static final String ACTION_NOTIFICATION_STOP = "com.mvt.derpcast.action.NOTIFICATION_STOP";
    public static final int PLAY_NOTIFICATION = 1;
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";

    private CastServiceBinder mCastServiceBinder;
    private RemoteControlClient mRemoteControlClient;
    private BroadcastReceiver mBroadcastReceiver;
    private WifiManager.WifiLock mWifiLock;
    private ConnectableDevice mDevice;
    private MediaInfo mMediaInfo;
    private MediaPlayer.MediaLaunchObject mMediaLaunchObject;
    private MediaPlayer.LaunchListener mLaunchListener;

    private DeviceAdapter mDeviceAdapter;
    private MediaAdapter mMediaAdapter;

    private boolean mServiceStarted;
    private boolean mPlaying;
    private String _mTitle;

    public CastService() {
        super("CastService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!mServiceStarted) {
            mServiceStarted = true;

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "DerpCastWifiLock");

            mDeviceAdapter = new DeviceAdapter(CastService.this);
            mMediaAdapter = new MediaAdapter();

            mCastServiceBinder = new CastServiceBinder(CastService.this);
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_NOTIFICATION_PLAY.equals(action)) {
                        play();
                    }
                    else if (ACTION_NOTIFICATION_PAUSE.equals(action)) {
                        pause();
                    }
                    else if (ACTION_NOTIFICATION_STOP.equals(action)) {
                        stop();
                    }
                    else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                        KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                                if (mPlaying) {
                                    pause();
                                }
                                else {
                                    play();
                                }
                            }
                        }
                    }
                }
            };
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mCastServiceBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mMediaInfo == null) {
            // Stop service when no clients are bound and no media is playing
            stopSelf();
        }

        return super.onUnbind(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    @Override
    public void onDestroy() {
        stop();
        DiscoveryManager.destroy();
        super.onDestroy();
    }

    public void setLaunchListener(MediaPlayer.LaunchListener launchListener) {
        mLaunchListener = launchListener;
    }

    public void play(ConnectableDevice device, MediaInfo mediaInfo, String title) {

        if (device == null ||
                device.getMediaControl() == null ||
                device.getMediaPlayer() == null ||
                mediaInfo == null) {
            return;
        }

        mPlaying = true;
        mMediaInfo = mediaInfo;
        mDevice = device;
        _mTitle = title;

        MediaPlayer mediaPlayer = device.getMediaPlayer();
        mediaPlayer.playMedia(mediaInfo.getUrl(), mediaInfo.getFormat(), title, mediaInfo.getUrl(),
                MEDIA_VIDEO_ART_URL, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject mediaLaunchObject) {
                mMediaLaunchObject = mediaLaunchObject;
                if (mLaunchListener != null) {
                    mLaunchListener.onSuccess(mMediaLaunchObject);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                mMediaLaunchObject = null;
                if (mLaunchListener != null) {
                    mLaunchListener.onError(error);
                }
            }
        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
        intentFilter.addAction(ACTION_NOTIFICATION_PLAY);
        intentFilter.addAction(ACTION_NOTIFICATION_PAUSE);
        intentFilter.addAction(ACTION_NOTIFICATION_STOP);

        LocalBroadcastManager
                .getInstance(CastService.this)
                .registerReceiver(mBroadcastReceiver, intentFilter);

        Context context = getApplicationContext();
        setLockScreenControls(context, title);
        Notification notification = getNotification(context, title);
        startForeground(PLAY_NOTIFICATION, notification);
    }

    public ConnectableDevice getPlayingDevice() {
        return mDevice;
    }

    public MediaInfo getPlayingMediaInfo() {
        return mMediaInfo;
    }

    public DeviceAdapter getDeviceAdapter() {
        return mDeviceAdapter;
    }

    public MediaAdapter getMediaAdapter() {
        return mMediaAdapter;
    }

    public String getTitle() {
        return _mTitle;
    }

    public void play() {
        if (mDevice != null && mDevice.getMediaControl() != null) {
            mDevice.getMediaControl().play(null);
            mPlaying = true;

            if (mRemoteControlClient != null) {
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
            }
        }
    }

    public void pause() {
        if (mDevice != null && mDevice.getMediaControl() != null) {
            mDevice.getMediaControl().pause(null);
            mPlaying = false;

            if (mRemoteControlClient != null) {
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
            }
        }
    }

    public void stop() {
        if (mDevice != null && mDevice.getMediaControl() != null) {
            mDevice.getMediaControl().stop(null);
            mDevice = null;
        }

        mPlaying = false;
        mMediaInfo = null;
        mMediaLaunchObject = null;
        _mTitle = null;

        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }

        LocalBroadcastManager
                .getInstance(CastService.this)
                .unregisterReceiver(mBroadcastReceiver);
        removeLockScreenControls(getApplicationContext());
        stopForeground(true);
    }

    private void setLockScreenControls(Context context, String title) {
        ComponentName eventReceiver = new ComponentName(context, MediaButtonEventReceiver.class);

        if (mRemoteControlClient == null) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

            mRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
            mRemoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE);
        }

        mRemoteControlClient
                .editMetadata(false)
                .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                .apply();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        audioManager.registerMediaButtonEventReceiver(eventReceiver);
        audioManager.registerRemoteControlClient(mRemoteControlClient);

        mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    }

    private void removeLockScreenControls(Context context) {
        if (mRemoteControlClient != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterRemoteControlClient(mRemoteControlClient);
            audioManager.abandonAudioFocus(null);
        }
    }

    private Notification getNotification(Context context, String title) {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, mainActivityIntent, 0);

        ComponentName eventReceiver = new ComponentName(context, MediaButtonEventReceiver.class);
        Intent playIntent = new Intent(ACTION_NOTIFICATION_PLAY);
        playIntent.setComponent(eventReceiver);
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(context, 1, playIntent, 0);

        Intent pauseIntent = new Intent(ACTION_NOTIFICATION_PAUSE);
        pauseIntent.setComponent(eventReceiver);
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(context, 2, pauseIntent, 0);

        Intent stopIntent = new Intent(ACTION_NOTIFICATION_STOP);
        stopIntent.setComponent(eventReceiver);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(context, 3, stopIntent, 0);

        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.view_notification);
        contentView.setTextViewText(R.id.title_text_view, title);
        contentView.setOnClickPendingIntent(R.id.play_button, playPendingIntent);
        contentView.setOnClickPendingIntent(R.id.pause_button, pausePendingIntent);
        contentView.setOnClickPendingIntent(R.id.stop_button, stopPendingIntent);

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_cast_notification)
                .setContentIntent(contentIntent)
                .setContent(contentView)
                .setOngoing(true);

        return notificationBuilder.build();
    }
}
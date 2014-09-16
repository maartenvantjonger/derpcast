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

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.mvt.derpcast.R;
import com.mvt.derpcast.activities.MainActivity;
import com.mvt.derpcast.media.MediaInfo;

public class CastService extends IntentService {

    public static final String ACTION_START = "com.mvt.derpcast.action.START";
    public static final String ACTION_STOP = "com.mvt.derpcast.action.STOP";
    public static final int PLAY_NOTIFICATION = 1;

    private static final String MEDIA_LOGO_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/app_logo.png";
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";

    private CastServiceBinder _castServiceBinder;
    private RemoteControlClient _remoteControlClient;
    private BroadcastReceiver _broadcastReceiver;
    private WifiManager.WifiLock _wifiLock;
    private ConnectableDevice _device;

    private MediaPlayer.LaunchListener _launchListener;

    public CastService(String name) {
        super(name);
        _castServiceBinder = new CastServiceBinder(CastService.this);
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                KeyEvent keyEvent = (KeyEvent)intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyEvent.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            play();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            pause();
                            break;
                        case KeyEvent.KEYCODE_VOLUME_UP:
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                            changeVolume(keyEvent.getKeyCode());
                            break;
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_STOP.equals(action)) {
                Context context = getApplicationContext();
                removeLockScreenControls(context);
                stopForeground(true);
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return _castServiceBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    public void setLaunchListener(MediaPlayer.LaunchListener launchListener) {
        _launchListener = launchListener;
    }

    public void play(ConnectableDevice device, MediaInfo mediaInfo, String title) {

        if (device == null || device.getMediaControl() == null || device.getMediaPlayer() == null) {
            return;
        }

        _device = device;

        String imageUrl = mediaInfo.format.startsWith("video/") ? MEDIA_VIDEO_ART_URL : MEDIA_LOGO_URL;
        MediaPlayer mediaPlayer = device.getMediaPlayer();
        mediaPlayer.playMedia(mediaInfo.url, mediaInfo.format, title, mediaInfo.title, imageUrl, false, _launchListener);

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        _wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "DerpCastWifiLock");

        LocalBroadcastManager
                .getInstance(CastService.this)
                .registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

        Context context = getApplicationContext();
        setLockScreenControls(context, title, mediaInfo.title);
        Notification notification = getNotification(context, title, mediaInfo.title);
        startForeground(PLAY_NOTIFICATION, notification);
    }


    public void play() {
        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    }

    public void pause() {
        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
    }

    public void stop() {
        if (_wifiLock.isHeld()) {
            _wifiLock.release();
        }

        LocalBroadcastManager
                .getInstance(CastService.this)
                .unregisterReceiver(_broadcastReceiver);
    }

    public void changeVolume(int keyCode) {
        if (_device != null && _device.hasCapability(VolumeControl.Volume_Up_Down)) {
            VolumeControl volumeControl = _device.getVolumeControl();
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeControl.volumeUp(null);
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeControl.volumeDown(null);
            }
        }
    }

    private void setLockScreenControls(Context context, String title, String description) {
        ComponentName eventReceiver = new ComponentName(context, MediaButtonEventReceiver.class);

        if (_remoteControlClient == null) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

            _remoteControlClient = new RemoteControlClient(mediaPendingIntent);
            _remoteControlClient.setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE);
        }

        _remoteControlClient
                .editMetadata(false)
                .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, description)
                .apply();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        audioManager.registerMediaButtonEventReceiver(eventReceiver);
        audioManager.registerRemoteControlClient(_remoteControlClient);

        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    }

    private void removeLockScreenControls(Context context) {
        if (_remoteControlClient != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterRemoteControlClient(_remoteControlClient);
            audioManager.abandonAudioFocus(null);
        }
    }

    private Notification getNotification(Context context, String title, String description) {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(description)
                .setSmallIcon(R.drawable.main_icon)
                .setContentIntent(contentIntent)
                .setOngoing(true);

        return notificationBuilder.build();
    }
}
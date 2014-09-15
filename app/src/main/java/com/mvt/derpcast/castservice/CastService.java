package com.mvt.derpcast.castservice;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.wifi.WifiManager;
import android.os.IBinder;

import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;
import com.mvt.derpcast.R;
import com.mvt.derpcast.activities.MainActivity;
import com.mvt.derpcast.media.MediaInfo;

public class CastService extends IntentService {

    public static final String ACTION_SETUP = "com.mvt.derpcast.action.SETUP";
    public static final String ACTION_PLAY = "com.mvt.derpcast.action.PLAY";
    public static final String ACTION_PAUSE = "com.mvt.derpcast.action.PAUSE";
    public static final String ACTION_REMOVE = "com.mvt.derpcast.action.REMOVE";
    public static final int PLAY_NOTIFICATION = 1;

    private static final String MEDIA_LOGO_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/app_logo.png";
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";

    private CastServiceBinder _castServiceBinder;
    private RemoteControlClient _remoteControlClient;
    private MainActivity _mainActivity;
    private BroadcastReceiver _broadcastReceiver;
    private WifiManager.WifiLock _wifiLock;

    public CastService(String name) {
        super(name);
        _castServiceBinder = new CastServiceBinder(CastService.this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SETUP.equals(action)) {
                String title = intent.getStringExtra("title");
                String description = intent.getStringExtra("description");

                Context context = getApplicationContext();
                setLockScreenControls(context, title, description);
                Notification notification = getNotification(context, title, description);
                startForeground(PLAY_NOTIFICATION, notification);
            }
            else if (ACTION_REMOVE.equals(action)) {
                Context context = getApplicationContext();
                removeLockScreenControls(context);
                stopForeground(true);
            }
            else if (ACTION_PLAY.equals(action)) {
                _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
            }
            else if (ACTION_PAUSE.equals(action)) {
                _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
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

    public void setLockScreenControls(Context context, String title, String description) {
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

        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        audioManager.registerMediaButtonEventReceiver(eventReceiver);
        audioManager.registerRemoteControlClient(_remoteControlClient);

        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    }

    public void removeLockScreenControls(Context context) {
        if (_remoteControlClient != null) {
            AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
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

    public void setMainActivity(MainActivity mainActivity) {
        _mainActivity = mainActivity;
    }

    public void play(MediaPlayer mediaPlayer, MediaInfo mediaInfo, String title) {
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        _wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "DerpCastWifiLock");

        String imageUrl = mediaInfo.format.startsWith("video/") ? MEDIA_VIDEO_ART_URL : MEDIA_LOGO_URL;
        mediaPlayer.playMedia(mediaInfo.url, mediaInfo.format, title,
                mediaInfo.title, imageUrl, false, new MediaPlayer.LaunchListener() {
                    @Override
                    public void onSuccess(MediaPlayer.MediaLaunchObject object) {

                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        error.printStackTrace();
                    }
                });
    }

    public void stop () {
        if (_wifiLock.isHeld()) {
            _wifiLock.release();
        }
    }
}

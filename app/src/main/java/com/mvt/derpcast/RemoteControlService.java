package com.mvt.derpcast;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.IBinder;

public class RemoteControlService extends Service {

    public static final String ACTION_PLAY = "com.mvt.derpcast.action.PLAY";
    public static final String ACTION_PAUSE = "com.mvt.derpcast.action.PAUSE";
    public static final String ACTION_STOP = "com.mvt.derpcast.action.STOP";

    private static RemoteControlClient _remoteControlClient;

    public void setLockScreenControls(Context context, String title, String description) {

        ComponentName eventReceiver = new ComponentName(context, RemoteControlEventReceiver.class);

        if (_remoteControlClient == null) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

            _remoteControlClient = new RemoteControlClient(mediaPendingIntent);
            _remoteControlClient.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE);
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
    }

    public void removeLockScreenControls(Context context) {
        if (_remoteControlClient != null) {
            AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterRemoteControlClient(_remoteControlClient);
            audioManager.abandonAudioFocus(null);
        }
    }

    private void setNotification(Context context, String title, String description) {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent eventReceiverIntent = new Intent(context, RemoteControlEventReceiver.class);
        PendingIntent deleteIntent = PendingIntent.getBroadcast(context, 0, eventReceiverIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(context)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.main_icon)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .build();

        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                Context context = getApplicationContext();
                String title = intent.getStringExtra("title");
                String description = intent.getStringExtra("description");

                setLockScreenControls(context, title, description);
                setNotification(context, title, description);

                _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
            }
            else if (ACTION_PAUSE.equals(action)) {
                _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        removeLockScreenControls(getApplicationContext());
        stopForeground(true);
        super.onDestroy();
    }
}

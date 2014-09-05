package com.mvt.derpcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class RemoteControlEventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent keyEvent = (KeyEvent)intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyEvent.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        context.startService(new Intent(RemoteControlService.ACTION_PLAY));
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        context.startService(new Intent(RemoteControlService.ACTION_PAUSE));
                        break;
                }

                context.sendBroadcast(new Intent("com.mvt.derpcast.action.test"));
            }
        }
    }
}

package com.mvt.derpcast.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;

import com.mvt.derpcast.castservice.CastService;
import com.mvt.derpcast.castservice.CastServiceBinder;

public class NewMainActivity extends ActionBarActivity {

    private ServiceConnection _serviceConnection;
    private CastService _castService;

    @Override
    protected void onStart() {
        super.onStart();

        _serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder binder) {
                CastServiceBinder castServiceBinder = (CastServiceBinder)binder;
                _castService = castServiceBinder.getCastService();
                _castService.setMainActivity(NewMainActivity.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                _castService = null;
            }
        };


        Intent intent = new Intent(this, CastService.class);
        bindService(intent, _serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (_castService != null) {
            _castService = null;
            unbindService(_serviceConnection);
        }
    }
}

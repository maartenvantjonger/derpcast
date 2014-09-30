package com.mvt.derpcast.castservice;

import android.os.Binder;

public class CastServiceBinder extends Binder {
    private CastService _castService;

    public CastServiceBinder(CastService castService) {
        _castService = castService;
    }

    public CastService getCastService() {
        return _castService;
    }
}

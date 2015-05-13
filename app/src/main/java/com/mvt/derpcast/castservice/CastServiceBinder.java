package com.mvt.derpcast.castservice;

import android.os.Binder;

public class CastServiceBinder extends Binder {
    private CastService mCastService;

    public CastServiceBinder(CastService castService) {
        mCastService = castService;
    }

    public CastService getCastService() {
        return mCastService;
    }
}

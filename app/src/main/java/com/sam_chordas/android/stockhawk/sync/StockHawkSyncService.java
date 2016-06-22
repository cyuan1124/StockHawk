package com.sam_chordas.android.stockhawk.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class StockHawkSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static StockHawkSyncAdapter sSunshineSyncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                sSunshineSyncAdapter = new StockHawkSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }
}
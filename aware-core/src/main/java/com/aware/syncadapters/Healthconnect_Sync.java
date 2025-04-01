package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import com.aware.providers.Healthconnect_Provider;

/**
 * Healthconnect sync service, responsible for background data synchronization
 */
public class Healthconnect_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Healthconnect_Provider.DATABASE_TABLES,
                        Healthconnect_Provider.TABLES_FIELDS,
                        new Uri[]{ Healthconnect_Provider.Healthconnect_Data.CONTENT_URI }
                );
                Log.d("healthconnect", "healthconnect Sync adapter initialized");
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}

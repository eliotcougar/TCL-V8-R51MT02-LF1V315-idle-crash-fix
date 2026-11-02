package com.eliotcougar.tvidlefix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        try {
            context.startForegroundService(new Intent(context, FixService.class));
        } catch (RuntimeException e) {
            Log.e("TVIdleFix", "Could not start after " + action, e);
        }
    }
}

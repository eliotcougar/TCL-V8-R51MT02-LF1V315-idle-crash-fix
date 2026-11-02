package com.eliotcougar.tvidlefix;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public final class FixService extends Service {
    private static final String TAG = "TVIdleFix";
    private static final String FINISH = "com.google.android.tvlauncher.intent.action.FINISH_EMPTY_ACTIVITY";
    private static final long[] RETRIES_MS = {500, 1500, 3500, 7000, 15000};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IdleSession session = new IdleSession();
    private boolean registered;
    private boolean initialized;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                schedule(session.enterDream());
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                schedule(session.enterScreenOff());
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                boolean interactive = getSystemService(PowerManager.class).isInteractive();
                int token = session.exitDream(interactive);
                if (interactive) cancelForWake();
                else schedule(token);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                session.wake();
                cancelForWake();
            }
        }
    };

    private void cancelForWake() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("idle_fix", "TV Idle Fix", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, "idle_fix")
                .setContentTitle("TV Idle Fix")
                .setContentText("Idle helper workaround active")
                .setSmallIcon(R.drawable.ic_stat_fix)
                .setShowWhen(false).setLocalOnly(true).setOnlyAlertOnce(true)
                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
        startForeground(1, notification);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // These are protected system broadcasts; no custom inbound command is exported.
        registerReceiver(receiver, filter);
        registered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!initialized) {
            initialized = true;
            PowerManager power = getSystemService(PowerManager.class);
            if (!power.isInteractive()) {
                schedule(session.enterScreenOff());
            } else {
                // The service may be restarted after DREAMING_STARTED has already passed.
                // One catch-up broadcast closes an existing helper without persistent polling.
                final int generation = session.generation();
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (session.generation() == generation) sendFinish();
                    }
                }, 1000);
            }
        }
        return START_STICKY;
    }

    private void schedule(final int token) {
        if (token < 0) return;
        handler.removeCallbacksAndMessages(null);
        for (long delay : RETRIES_MS) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (session.accepts(token)) sendFinish();
                }
            }, delay);
        }
    }

    private void sendFinish() {
        try {
            sendBroadcast(new Intent(FINISH).setPackage("com.google.android.tvlauncher")
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
        } catch (RuntimeException e) {
            Log.e(TAG, "Finish broadcast failed", e);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        session.wake();
        if (registered) unregisterReceiver(receiver);
        stopForeground(true);
        super.onDestroy();
    }
}

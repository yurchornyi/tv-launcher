package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps Home alive while an app is in front, and brings it back when the TV wakes.
 *
 * TCL's AppCleanService (com.tcl.framework.custom) force-stops every third-party process of importance CACHED once a
 * minute; a launcher in the background is CACHED, so it died about a minute into every app. With Home's task gone,
 * Back out of the app landed on whatever task was left, usually Netflix, which TCL never stops, and a TV waking from
 * standby had no launcher to show. A foreground service lifts the process out of CACHED, so the cleaner passes it by.
 */
public class HomeKeeper extends Service {

    private static final String TAG = "TvHome.Keeper";
    private static final String CHANNEL = "home_keeper";

    /** The screen coming on means the TV woke from standby: it opens on Home, not on the app left before. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(home);
                Log.i(TAG, "screen on: Home");
            } catch (RuntimeException e) {
                Log.w(TAG, "screen on: could not open Home: " + e);
            }
        }
    };

    /** Called by Home while it is in front, the one moment Android lets an app start a foreground service. */
    static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, HomeKeeper.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start: " + e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.home_keeper),
                NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_apps)
                .setContentTitle(getString(R.string.home_keeper))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(screenOn, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOn, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenOn);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

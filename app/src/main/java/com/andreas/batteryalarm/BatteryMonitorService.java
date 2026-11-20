package com.andreas.batteryalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BatteryMonitorService extends Service {

    private static final String CHANNEL_MONITOR_ID = "battery_monitor_channel";
    private static final String CHANNEL_ALARM_ID = "battery_alarm_trigger_channel";
    private static final int MONITOR_NOTIF_ID = 101;
    private static final int ALARM_NOTIF_ID = 102;

    private boolean isAlarmTriggered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAlarmTriggered) return;

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = Math.round((level / (float) scale) * 100f);

            SharedPreferences prefs = getSharedPreferences("AlarmPrefs", MODE_PRIVATE);
            int targetPct = prefs.getInt("TARGET_PERCENTAGE", 100);

            // Update teks persentase di notifikasi status bar
            updateMonitorNotification(batteryPct, targetPct);

            // Jika baterai sudah mencapai atau melewati target
            if (batteryPct >= targetPct) {
                isAlarmTriggered = true;
                triggerFullScreenAlarm(batteryPct);
                stopSelf(); // Hentikan service pemantau karena alarm sudah aktif
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isAlarmTriggered = false;

        SharedPreferences prefs = getSharedPreferences("AlarmPrefs", MODE_PRIVATE);
        int targetPct = prefs.getInt("TARGET_PERCENTAGE", 100);

        // Jalankan Foreground Service agar tidak di-kill saat layar mati selama ngecas
        startForeground(MONITOR_NOTIF_ID, buildMonitorNotification("Memantau baterai (Target: " + targetPct + "%)"));

        // Daftarkan listener perubahan baterai
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // START_NOT_STICKY: Jangan hidupkan ulang service jika aplikasi ditutup
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Otomatis terpanggil saat user swipe/clear aplikasi di Recent Apps
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver sudah dilepas sebelumnya
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void triggerFullScreenAlarm(int currentPct) {
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.putExtra("BATTERY_LEVEL", currentPct);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                0,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ALARM_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Baterai Sudah " + currentPct + "%")
                .setContentText("Silahkan lepaskan daya dengan device anda")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ALARM_NOTIF_ID, builder.build());
        }

        // Coba buka langsung juga sebagai fallback jika layar sedang menyala/tidak terkunci
        try {
            startActivity(alarmIntent);
        } catch (Exception ignored) {
        }
    }

    private Notification buildMonitorNotification(String contentText) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Battery Alarm Aktif")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateMonitorNotification(int currentPct, int targetPct) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            String text = "Baterai saat ini: " + currentPct + "% • Target alarm: " + targetPct + "%";
            manager.notify(MONITOR_NOTIF_ID, buildMonitorNotification(text));
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Channel 1: Notifikasi senyap selama memantau
            NotificationChannel monitorChannel = new NotificationChannel(
                    CHANNEL_MONITOR_ID,
                    "Status Pemantauan Baterai",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(monitorChannel);

            // Channel 2: Prioritas tinggi khusus untuk menyalakan layar saat alarm berbunyi
            NotificationChannel alarmChannel = new NotificationChannel(
                    CHANNEL_ALARM_ID,
                    "Alarm Baterai Penuh",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(alarmChannel);
        }
    }
}
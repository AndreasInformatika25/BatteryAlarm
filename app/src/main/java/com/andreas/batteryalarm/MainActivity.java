package com.andreas.batteryalarm;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvCurrentBattery, tvTargetLabel, tvMediaStatus;
    private SeekBar seekBarTarget;
    private Button btnToggleService;
    private boolean isMonitoring = false;

    // Launcher pemilih Gambar Background
    private final ActivityResultLauncher<String[]> pickBgLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    savePersistableUri("BG_IMAGE_URI", uri);
                    updateMediaStatusText();
                }
            });

    // Launcher pemilih Gambar Utama
    private final ActivityResultLauncher<String[]> pickMainLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    savePersistableUri("MAIN_IMAGE_URI", uri);
                    updateMediaStatusText();
                }
            });

    // Launcher pemilih Lagu Alarm
    private final ActivityResultLauncher<String[]> pickAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    savePersistableUri("AUDIO_URI", uri);
                    updateMediaStatusText();
                }
            });

    // Receiver lokal untuk menampilkan persentase baterai live di halaman utama
    private final BroadcastReceiver uiBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int pct = Math.round((level / (float) scale) * 100f);
            tvCurrentBattery.setText("Baterai saat ini: " + pct + "%");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("AlarmPrefs", MODE_PRIVATE);

        tvCurrentBattery = findViewById(R.id.tvCurrentBattery);
        tvTargetLabel = findViewById(R.id.tvTargetLabel);
        tvMediaStatus = findViewById(R.id.tvMediaStatus);
        seekBarTarget = findViewById(R.id.seekBarTarget);
        btnToggleService = findViewById(R.id.btnToggleService);

        Button btnPickBg = findViewById(R.id.btnPickBg);
        Button btnPickMain = findViewById(R.id.btnPickMain);
        Button btnPickAudio = findViewById(R.id.btnPickAudio);
        Button btnResetMedia = findViewById(R.id.btnResetMedia);

        // Muat target tersimpan (default 100%, offset SeekBar +50)
        int savedTarget = prefs.getInt("TARGET_PERCENTAGE", 100);
        seekBarTarget.setProgress(savedTarget - 50);
        tvTargetLabel.setText("Target Alarm: " + savedTarget + "%");

        seekBarTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int target = progress + 50;
                tvTargetLabel.setText("Target Alarm: " + target + "%");
                prefs.edit().putInt("TARGET_PERCENTAGE", target).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPickBg.setOnClickListener(v -> pickBgLauncher.launch(new String[]{"image/*"}));
        btnPickMain.setOnClickListener(v -> pickMainLauncher.launch(new String[]{"image/*"}));
        btnPickAudio.setOnClickListener(v -> pickAudioLauncher.launch(new String[]{"audio/*"}));

        btnResetMedia.setOnClickListener(v -> {
            prefs.edit()
                    .remove("BG_IMAGE_URI")
                    .remove("MAIN_IMAGE_URI")
                    .remove("AUDIO_URI")
                    .apply();
            updateMediaStatusText();
            Toast.makeText(this, "Direset ke tampilan hitam polos", Toast.LENGTH_SHORT).show();
        });

        btnToggleService.setOnClickListener(v -> {
            if (!isMonitoring) {
                if (checkAndRequestPermissions()) {
                    startMonitoringService();
                }
            } else {
                stopMonitoringService();
            }
        });

        updateMediaStatusText();
    }

    private void savePersistableUri(String key, Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            prefs.edit().putString(key, uri.toString()).apply();
            Toast.makeText(this, "Berhasil disimpan!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal mengunci izin file", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMediaStatusText() {
        boolean hasBg = prefs.getString("BG_IMAGE_URI", null) != null;
        boolean hasMain = prefs.getString("MAIN_IMAGE_URI", null) != null;
        boolean hasAudio = prefs.getString("AUDIO_URI", null) != null;

        String status = "Konfigurasi saat ini:\n"
                + "• Background: " + (hasBg ? "Kustom Terpasang" : "Hitam Polos") + "\n"
                + "• Gambar Utama: " + (hasMain ? "Kustom Terpasang" : "Tidak Ada") + "\n"
                + "• Lagu Alarm: " + (hasAudio ? "Lagu Pilihan" : "Nada Bawaan HP");
        tvMediaStatus.setText(status);
    }

    private boolean checkAndRequestPermissions() {
        // 1. Izin Notifikasi (Wajib di Android 13, 14, 15)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
                return false;
            }
        }

        // 2. Izin Full Screen Intent untuk APK manual di Android 14 & 15
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "Aktifkan izin 'Full screen notifications' agar layar bisa menyala otomatis", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return false;
            }
        }
        return true;
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        isMonitoring = true;
        btnToggleService.setText("Hentikan Pemantauan");
        btnToggleService.setBackgroundColor(0xFFE53935);
        Toast.makeText(this, "Pemantauan baterai aktif", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoringService() {
        stopService(new Intent(this, BatteryMonitorService.class));
        isMonitoring = false;
        btnToggleService.setText("Aktifkan Pemantau Baterai");
        btnToggleService.setBackgroundColor(0xFF4CAF50);
        Toast.makeText(this, "Pemantauan dihentikan", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(uiBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(uiBatteryReceiver);
    }
}
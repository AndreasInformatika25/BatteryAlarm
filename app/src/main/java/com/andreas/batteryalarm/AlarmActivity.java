package com.andreas.batteryalarm;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class AlarmActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Paksa layar menyala & tampil di atas Lock Screen (Android 14 & 15)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm);

        ImageView imgBackground = findViewById(R.id.imgBackground);
        ImageView imgMain = findViewById(R.id.imgMain);
        CardView cardMainImage = findViewById(R.id.cardMainImage);
        TextView tvPercentage = findViewById(R.id.tvPercentage);
        Button btnDismiss = findViewById(R.id.btnDismiss);

        int currentLevel = getIntent().getIntExtra("BATTERY_LEVEL", 100);
        tvPercentage.setText(currentLevel + "%");

        // 2. Muat pengaturan kustom (Gambar & Lagu)
        SharedPreferences prefs = getSharedPreferences("AlarmPrefs", MODE_PRIVATE);
        String bgUriStr = prefs.getString("BG_IMAGE_URI", null);
        String mainUriStr = prefs.getString("MAIN_IMAGE_URI", null);
        String audioUriStr = prefs.getString("AUDIO_URI", null);

        // Set Background (Jika kosong, otomatis hitam polos #000000 dari XML)
        if (bgUriStr != null) {
            try {
                imgBackground.setImageURI(Uri.parse(bgUriStr));
                // Efek blur bawaan Android 12+ persis seperti di mockup
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    imgBackground.setRenderEffect(
                            RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
                    );
                }
            } catch (Exception e) {
                imgBackground.setVisibility(View.GONE);
            }
        }

        // Set Gambar Utama (Jika kosong, sembunyikan CardView-nya)
        if (mainUriStr != null) {
            try {
                imgMain.setImageURI(Uri.parse(mainUriStr));
            } catch (Exception e) {
                cardMainImage.setVisibility(View.GONE);
            }
        } else {
            cardMainImage.setVisibility(View.GONE);
        }

        // 3. Putar Lagu Alarm
        playAlarmSound(audioUriStr);

        // 4. Tombol Matikan Alarm & Hentikan Monitoring
        btnDismiss.setOnClickListener(v -> {
            stopAlarmSound();
            stopService(new Intent(this, BatteryMonitorService.class));
            finish();
        });
    }

    private void playAlarmSound(String audioUriStr) {
        try {
            Uri alarmUri = (audioUriStr != null)
                    ? Uri.parse(audioUriStr)
                    : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
            );
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAlarmSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopAlarmSound();
        super.onDestroy();
    }
}
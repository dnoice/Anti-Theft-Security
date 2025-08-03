// File: app/src/main/java/com/antitheft/security/AlarmManager.java

package com.antitheft.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import android.content.res.AssetFileDescriptor;
import java.io.IOException;

public class AlarmManager {
    private static final String TAG = "AntiTheft_AlarmManager";
    private static final String PREFS_NAME = "AlarmPrefs";
    
    private Context context;
    private SharedPreferences preferences;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private AudioManager audioManager;
    
    private boolean isAlarmActive = false;
    private boolean isVibrationEnabled = true;
    private boolean isSoundEnabled = true;
    private int alarmVolume = 100; // 0-100
    private String selectedAlarmSound = "siren"; // siren, beep, horn, bell
    private int alarmDuration = 60; // seconds, 0 = infinite
    
    private Handler alarmHandler = new Handler();
    private Runnable stopAlarmRunnable;
    
    // Vibration patterns
    private static final long[] VIBRATION_PATTERN_ALARM = {0, 1000, 500, 1000, 500, 1000};
    private static final long[] VIBRATION_PATTERN_WARNING = {0, 200, 100, 200};
    private static final long[] VIBRATION_PATTERN_BRIEF = {0, 300};
    
    public AlarmManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        initializeComponents();
        loadSettings();
    }
    
    private void initializeComponents() {
        // Initialize vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        
        // Initialize audio manager
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    public void startAlarm() {
        if (isAlarmActive) {
            Log.w(TAG, "Alarm already active");
            return;
        }
        
        Log.e(TAG, "STARTING SECURITY ALARM!");
        isAlarmActive = true;
        
        // Start sound alarm
        if (isSoundEnabled) {
            startSoundAlarm();
        }
        
        // Start vibration alarm
        if (isVibrationEnabled && vibrator != null) {
            startVibrationAlarm();
        }
        
        // Set maximum volume for alarm
        setAlarmVolume();
        
        // Schedule automatic stop if duration is set
        if (alarmDuration > 0) {
            scheduleAlarmStop();
        }
        
        // Record alarm trigger
        recordAlarmTrigger();
    }
    
    private void startSoundAlarm() {
        try {
            // Release any existing media player
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();

            // Set audio attributes for alarm
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            // Set data source based on selected sound
            int soundResource = getAlarmSoundResource();
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(soundResource);
            if (afd == null) {
                Log.e(TAG, "Failed to open alarm sound resource");
                mediaPlayer.release();
                mediaPlayer = null;
                return;
            }

            try {
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } finally {
                afd.close();
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(alarmVolume / 100f, alarmVolume / 100f);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.i(TAG, "Alarm sound started: " + selectedAlarmSound);

        } catch (Exception e) {
            Log.e(TAG, "Error starting alarm sound", e);
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }
    
    private int getAlarmSoundResource() {
        switch (selectedAlarmSound) {
            case "beep":
                return R.raw.beep_sound;
            case "horn":
                return R.raw.horn_sound;
            case "bell":
                return R.raw.bell_sound;
            case "siren":
            default:
                return R.raw.siren_sound;
        }
    }
    
    private void startVibrationAlarm() {
        if (vibrator == null) return;
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(VIBRATION_PATTERN_ALARM, 0);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(VIBRATION_PATTERN_ALARM, 0);
            }
            
            Log.i(TAG, "Alarm vibration started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }
    
    private void setAlarmVolume() {
        try {
            // Save current volume levels
            int originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            
            preferences.edit()
                .putInt("original_alarm_volume", originalAlarmVolume)
                .putInt("original_music_volume", originalMusicVolume)
                .apply();
            
            // Set to maximum volume for alarm
            int maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int targetVolume = (int) (maxAlarmVolume * (alarmVolume / 100f));
            
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            
            Log.i(TAG, "Alarm volume set to: " + targetVolume + "/" + maxAlarmVolume);
        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm volume", e);
        }
    }
    
    private void scheduleAlarmStop() {
        if (stopAlarmRunnable != null) {
            alarmHandler.removeCallbacks(stopAlarmRunnable);
        }
        
        stopAlarmRunnable = this::stopAlarm;
        alarmHandler.postDelayed(stopAlarmRunnable, alarmDuration * 1000L);
        
        Log.d(TAG, "Alarm scheduled to stop in " + alarmDuration + " seconds");
    }
    
    public void stopAlarm() {
        if (!isAlarmActive) {
            return;
        }
        
        Log.i(TAG, "Stopping security alarm");
        isAlarmActive = false;
        
        // Stop sound
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "Alarm sound stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping alarm sound", e);
            }
        }
        
        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
            Log.d(TAG, "Alarm vibration stopped");
        }
        
        // Restore original volume levels
        restoreOriginalVolume();
        
        // Cancel scheduled stop
        if (stopAlarmRunnable != null) {
            alarmHandler.removeCallbacks(stopAlarmRunnable);
            stopAlarmRunnable = null;
        }
        
        // Record alarm stop
        recordAlarmStop();
    }
    
    private void restoreOriginalVolume() {
        try {
            int originalAlarmVolume = preferences.getInt("original_alarm_volume", -1);
            int originalMusicVolume = preferences.getInt("original_music_volume", -1);
            
            if (originalAlarmVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
            }
            if (originalMusicVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0);
            }
            
            Log.d(TAG, "Original volume levels restored");
        } catch (Exception e) {
            Log.e(TAG, "Error restoring volume", e);
        }
    }
    
    // Quick alert methods for non-alarm notifications
    public void playWarningSound() {
        playBriefAlert(R.raw.beep_sound, VIBRATION_PATTERN_WARNING);
    }
    
    public void playBriefAlert() {
        playBriefAlert(R.raw.beep_sound, VIBRATION_PATTERN_BRIEF);
    }
    
    private void playBriefAlert(int soundResource, long[] vibrationPattern) {
        // Play brief sound
        try {
            MediaPlayer briefPlayer = MediaPlayer.create(context, soundResource);
            if (briefPlayer != null) {
                briefPlayer.setOnCompletionListener(MediaPlayer::release);
                briefPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing brief alert sound", e);
        }
        
        // Play brief vibration
        if (vibrator != null && isVibrationEnabled) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createWaveform(vibrationPattern, -1);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(vibrationPattern, -1);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing brief vibration", e);
            }
        }
    }
    
    // Settings management
    public boolean isSoundEnabled() {
        return isSoundEnabled;
    }
    
    public void setSoundEnabled(boolean enabled) {
        this.isSoundEnabled = enabled;
    }
    
    public boolean isVibrationEnabled() {
        return isVibrationEnabled;
    }
    
    public void setVibrationEnabled(boolean enabled) {
        this.isVibrationEnabled = enabled;
    }
    
    public int getAlarmVolume() {
        return alarmVolume;
    }
    
    public void setAlarmVolume(int volume) {
        this.alarmVolume = Math.max(0, Math.min(100, volume));
    }
    
    public String getSelectedAlarmSound() {
        return selectedAlarmSound;
    }
    
    public void setSelectedAlarmSound(String sound) {
        this.selectedAlarmSound = sound;
    }
    
    public int getAlarmDuration() {
        return alarmDuration;
    }
    
    public void setAlarmDuration(int duration) {
        this.alarmDuration = Math.max(0, duration);
    }
    
    public String[] getAvailableAlarmSounds() {
        return new String[]{"siren", "beep", "horn", "bell"};
    }
    
    public boolean isAlarmActive() {
        return isAlarmActive;
    }
    
    // Persistence
    public void saveSettings() {
        preferences.edit()
            .putBoolean("sound_enabled", isSoundEnabled)
            .putBoolean("vibration_enabled", isVibrationEnabled)
            .putInt("alarm_volume", alarmVolume)
            .putString("alarm_sound", selectedAlarmSound)
            .putInt("alarm_duration", alarmDuration)
            .apply();
        
        Log.d(TAG, "Alarm settings saved");
    }
    
    public void loadSettings() {
        isSoundEnabled = preferences.getBoolean("sound_enabled", true);
        isVibrationEnabled = preferences.getBoolean("vibration_enabled", true);
        alarmVolume = preferences.getInt("alarm_volume", 100);
        selectedAlarmSound = preferences.getString("alarm_sound", "siren");
        alarmDuration = preferences.getInt("alarm_duration", 60);
        
        Log.d(TAG, "Alarm settings loaded");
    }
    
    // Statistics
    private void recordAlarmTrigger() {
        long timestamp = System.currentTimeMillis();
        int triggerCount = getTotalAlarmTriggers() + 1;
        
        preferences.edit()
            .putInt("total_alarm_triggers", triggerCount)
            .putLong("last_alarm_trigger", timestamp)
            .apply();
        
        Log.i(TAG, "Alarm trigger recorded (#" + triggerCount + ")");
    }
    
    private void recordAlarmStop() {
        long timestamp = System.currentTimeMillis();
        
        preferences.edit()
            .putLong("last_alarm_stop", timestamp)
            .apply();
    }
    
    public int getTotalAlarmTriggers() {
        return preferences.getInt("total_alarm_triggers", 0);
    }
    
    public long getLastAlarmTrigger() {
        return preferences.getLong("last_alarm_trigger", 0);
    }
    
    public long getLastAlarmStop() {
        return preferences.getLong("last_alarm_stop", 0);
    }
    
    public String getAlarmStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Alarm Status: ").append(isAlarmActive ? "ACTIVE" : "Inactive").append("\n");
        stats.append("Total Triggers: ").append(getTotalAlarmTriggers()).append("\n");
        stats.append("Sound: ").append(isSoundEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Vibration: ").append(isVibrationEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Volume: ").append(alarmVolume).append("%\n");
        stats.append("Sound Type: ").append(selectedAlarmSound).append("\n");
        stats.append("Duration: ").append(alarmDuration == 0 ? "Infinite" : alarmDuration + "s");
        
        return stats.toString();
    }
    
    // Test alarm functionality
    public void testAlarm(int durationSeconds) {
        Log.i(TAG, "Testing alarm for " + durationSeconds + " seconds");
        
        int originalDuration = alarmDuration;
        alarmDuration = durationSeconds;
        
        startAlarm();
        
        // Restore original duration after test
        alarmHandler.postDelayed(() -> {
            alarmDuration = originalDuration;
        }, durationSeconds * 1000L + 1000);
    }
    
    // Cleanup
    public void cleanup() {
        stopAlarm();
        
        if (alarmHandler != null) {
            alarmHandler.removeCallbacksAndMessages(null);
        }
        
        Log.d(TAG, "AlarmManager cleanup completed");
    }
}

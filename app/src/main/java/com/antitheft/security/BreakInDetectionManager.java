// File: app/src/main/java/com/antitheft/security/BreakInDetectionManager.java

package com.antitheft.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.app.KeyguardManager;
import android.os.Handler;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BreakInDetectionManager {
    private static final String TAG = "AntiTheft_BreakIn";
    private static final String PREFS_NAME = "BreakInDetectionPrefs";
    private static final int MAX_FAILED_ATTEMPTS_BEFORE_ACTION = 3;
    
    private Context context;
    private SharedPreferences preferences;
    private KeyguardManager keyguardManager;
    private BreakInDetectionCallback callback;
    
    private ScreenStateReceiver screenStateReceiver;
    private boolean isMonitoring = false;
    private boolean wasDeviceLocked = false;
    private int consecutiveFailedAttempts = 0;
    
    public interface BreakInDetectionCallback {
        void onUnlockAttemptDetected(int attemptNumber);
        void onSuspiciousActivity(String description, int failedAttempts);
        void onSuccessfulBreakIn(long breakInTime);
        void onDeviceLocked();
        void onDeviceUnlocked(boolean wasBreakIn);
    }
    
    public BreakInDetectionManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        this.screenStateReceiver = new ScreenStateReceiver();
    }
    
    public void startMonitoring(BreakInDetectionCallback callback) {
        if (isMonitoring) return;
        
        this.callback = callback;
        this.isMonitoring = true;
        this.wasDeviceLocked = isDeviceLocked();
        
        // Register screen state receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT); // Device unlocked
        
        context.registerReceiver(screenStateReceiver, filter);
        
        Log.i(TAG, "Break-in detection monitoring started");
    }
    
    public void stopMonitoring() {
        if (!isMonitoring) return;
        
        try {
            context.unregisterReceiver(screenStateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        
        isMonitoring = false;
        Log.i(TAG, "Break-in detection monitoring stopped");
    }
    
    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case Intent.ACTION_SCREEN_OFF:
                    handleScreenOff();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    handleScreenOn();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    handleDeviceUnlocked();
                    break;
            }
        }
    }
    
    private void handleScreenOff() {
        Log.d(TAG, "Screen turned off");
        wasDeviceLocked = true;
        
        if (callback != null) {
            callback.onDeviceLocked();
        }
        
        // Reset failed attempts when device is properly locked
        if (isDeviceLocked()) {
            resetFailedAttempts();
        }
    }
    
    private void handleScreenOn() {
        Log.d(TAG, "Screen turned on");
        
        if (wasDeviceLocked && isDeviceLocked()) {
            // Device was locked and screen turned on - potential unlock attempt
            recordUnlockAttempt();
        }
    }
    
    private void handleDeviceUnlocked() {
        Log.d(TAG, "Device unlocked");
        
        boolean wasBreakIn = false;
        
        if (wasDeviceLocked) {
            // Check if this was a legitimate unlock or a break-in
            if (consecutiveFailedAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_ACTION) {
                // Successful break-in after multiple failed attempts
                recordSuccessfulBreakIn();
                wasBreakIn = true;
            }
            
            wasDeviceLocked = false;
            resetFailedAttempts();
        }
        
        if (callback != null) {
            callback.onDeviceUnlocked(wasBreakIn);
        }
    }
    
    private void recordUnlockAttempt() {
        consecutiveFailedAttempts++;
        long timestamp = System.currentTimeMillis();
        
        // Store unlock attempt
        String attemptsKey = "unlock_attempts";
        String currentAttempts = preferences.getString(attemptsKey, "");
        String newAttempt = timestamp + ":" + consecutiveFailedAttempts;
        String updatedAttempts = currentAttempts.isEmpty() ? newAttempt : currentAttempts + "," + newAttempt;
        
        preferences.edit()
            .putString(attemptsKey, updatedAttempts)
            .putInt("total_unlock_attempts", getTotalUnlockAttempts() + 1)
            .putLong("last_unlock_attempt", timestamp)
            .apply();
        
        Log.w(TAG, "Unlock attempt #" + consecutiveFailedAttempts + " detected");
        
        if (callback != null) {
            callback.onUnlockAttemptDetected(consecutiveFailedAttempts);
            
            // Trigger suspicious activity detection
            if (consecutiveFailedAttempts >= 2) {
                String description = "Multiple failed unlock attempts detected (" + consecutiveFailedAttempts + " attempts)";
                callback.onSuspiciousActivity(description, consecutiveFailedAttempts);
            }
        }
        
        // Clean up old attempts (keep only last 50)
        cleanupOldAttempts();
    }
    
    private void recordSuccessfulBreakIn() {
        long timestamp = System.currentTimeMillis();
        
        preferences.edit()
            .putLong("last_successful_breakin", timestamp)
            .putInt("total_successful_breakins", getTotalSuccessfulBreakIns() + 1)
            .putInt("breakin_failed_attempts", consecutiveFailedAttempts)
            .apply();
        
        Log.e(TAG, "SUCCESSFUL BREAK-IN DETECTED after " + consecutiveFailedAttempts + " failed attempts");
        
        if (callback != null) {
            callback.onSuccessfulBreakIn(timestamp);
        }
    }
    
    private void resetFailedAttempts() {
        if (consecutiveFailedAttempts > 0) {
            Log.d(TAG, "Resetting failed attempts counter");
            consecutiveFailedAttempts = 0;
        }
    }
    
    private void cleanupOldAttempts() {
        String attemptsKey = "unlock_attempts";
        String attempts = preferences.getString(attemptsKey, "");
        
        if (!attempts.isEmpty()) {
            String[] attemptArray = attempts.split(",");
            if (attemptArray.length > 50) {
                // Keep only the last 50 attempts
                StringBuilder sb = new StringBuilder();
                for (int i = attemptArray.length - 50; i < attemptArray.length; i++) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(attemptArray[i]);
                }
                preferences.edit().putString(attemptsKey, sb.toString()).apply();
            }
        }
    }
    
    private boolean isDeviceLocked() {
        return keyguardManager.isKeyguardLocked();
    }
    
    // Statistics and data retrieval methods
    public int getTotalUnlockAttempts() {
        return preferences.getInt("total_unlock_attempts", 0);
    }
    
    public int getTotalSuccessfulBreakIns() {
        return preferences.getInt("total_successful_breakins", 0);
    }
    
    public long getLastUnlockAttemptTime() {
        return preferences.getLong("last_unlock_attempt", 0);
    }
    
    public long getLastSuccessfulBreakInTime() {
        return preferences.getLong("last_successful_breakin", 0);
    }
    
    public int getCurrentFailedAttempts() {
        return consecutiveFailedAttempts;
    }
    
    public List<UnlockAttempt> getRecentUnlockAttempts(int count) {
        List<UnlockAttempt> attempts = new ArrayList<>();
        String attemptsStr = preferences.getString("unlock_attempts", "");
        
        if (!attemptsStr.isEmpty()) {
            String[] attemptArray = attemptsStr.split(",");
            int startIndex = Math.max(0, attemptArray.length - count);
            
            for (int i = startIndex; i < attemptArray.length; i++) {
                String[] parts = attemptArray[i].split(":");
                if (parts.length == 2) {
                    try {
                        long timestamp = Long.parseLong(parts[0]);
                        int attemptNumber = Integer.parseInt(parts[1]);
                        attempts.add(new UnlockAttempt(timestamp, attemptNumber));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing unlock attempt data", e);
                    }
                }
            }
        }
        
        return attempts;
    }
    
    public String getBreakInStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Total Unlock Attempts: ").append(getTotalUnlockAttempts()).append("\n");
        stats.append("Successful Break-ins: ").append(getTotalSuccessfulBreakIns()).append("\n");
        stats.append("Current Failed Attempts: ").append(getCurrentFailedAttempts()).append("\n");
        
        long lastAttempt = getLastUnlockAttemptTime();
        if (lastAttempt > 0) {
            stats.append("Last Attempt: ").append(
                new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                    .format(new Date(lastAttempt))
            ).append("\n");
        }
        
        long lastBreakIn = getLastSuccessfulBreakInTime();
        if (lastBreakIn > 0) {
            stats.append("Last Break-in: ").append(
                new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                    .format(new Date(lastBreakIn))
            );
        } else {
            stats.append("Last Break-in: Never");
        }
        
        return stats.toString();
    }
    
    // Clear all break-in data
    public void clearBreakInData() {
        preferences.edit()
            .remove("unlock_attempts")
            .remove("total_unlock_attempts")
            .remove("total_successful_breakins")
            .remove("last_unlock_attempt")
            .remove("last_successful_breakin")
            .remove("breakin_failed_attempts")
            .apply();
        
        consecutiveFailedAttempts = 0;
        Log.i(TAG, "Break-in detection data cleared");
    }
    
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    // Data class for unlock attempts
    public static class UnlockAttempt {
        public final long timestamp;
        public final int attemptNumber;
        
        public UnlockAttempt(long timestamp, int attemptNumber) {
            this.timestamp = timestamp;
            this.attemptNumber = attemptNumber;
        }
        
        public String getFormattedTime() {
            return new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
        }
    }
}

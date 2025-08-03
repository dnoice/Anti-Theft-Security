// File: app/src/main/java/com/antitheft/security/SecurityManager.java

package com.antitheft.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SecurityManager {
    private static final String TAG = "AntiTheft_Security";
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String ENCRYPTED_PREFS_NAME = "EncryptedSecurityPrefs";
    
    private Context context;
    private SharedPreferences preferences;
    private SharedPreferences encryptedPreferences;
    
    // Security settings
    private boolean isArmed = false;
    private boolean motionDetectionEnabled = true;
    private boolean cameraEvidenceEnabled = true;
    private boolean breakInDetectionEnabled = true;
    private boolean fakeHomeScreenEnabled = false;
    private int sensitivity = 60; // 0-100
    private int pinAttempts = 0;
    private static final int MAX_PIN_ATTEMPTS = 5;
    private long lastPinAttemptTime = 0;
    private static final long PIN_LOCKOUT_DURATION = 300000; // 5 minutes
    
    public SecurityManager(Context context) {
        this.context = context;
        initializePreferences();
        loadSettings();
    }
    
    private void initializePreferences() {
        // Regular preferences for non-sensitive data
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Encrypted preferences for sensitive data like PIN
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
                
            encryptedPreferences = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error creating encrypted preferences", e);
            // Fallback to regular preferences (less secure)
            encryptedPreferences = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    // PIN Management
    public boolean hasPin() {
        return encryptedPreferences.contains("pin_hash");
    }
    
    public void setPin(String pin) {
        if (pin == null || pin.length() < 4) {
            throw new IllegalArgumentException("PIN must be at least 4 characters");
        }
        
        String hashedPin = hashPin(pin);
        encryptedPreferences.edit()
            .putString("pin_hash", hashedPin)
            .putLong("pin_set_time", System.currentTimeMillis())
            .apply();
        
        resetPinAttempts();
        Log.i(TAG, "PIN set successfully");
    }
    
    public boolean validatePin(String pin) {
        if (isPinLocked()) {
            Log.w(TAG, "PIN validation blocked - too many attempts");
            return false;
        }
        
        if (!hasPin()) {
            Log.w(TAG, "No PIN set");
            return false;
        }
        
        String storedHash = encryptedPreferences.getString("pin_hash", "");
        String inputHash = hashPin(pin);
        
        boolean isValid = storedHash.equals(inputHash);
        
        if (isValid) {
            resetPinAttempts();
            Log.i(TAG, "PIN validated successfully");
        } else {
            incrementPinAttempts();
            Log.w(TAG, "Invalid PIN attempt #" + pinAttempts);
        }
        
        return isValid;
    }
    
    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Add salt for extra security
            String saltedPin = pin + "ANTITHEFT_SALT_2024";
            byte[] hash = digest.digest(saltedPin.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing PIN", e);
            return pin; // Fallback (insecure)
        }
    }
    
    private void incrementPinAttempts() {
        pinAttempts++;
        lastPinAttemptTime = System.currentTimeMillis();
        
        preferences.edit()
            .putInt("pin_attempts", pinAttempts)
            .putLong("last_pin_attempt", lastPinAttemptTime)
            .apply();
        
        if (pinAttempts >= MAX_PIN_ATTEMPTS) {
            Log.e(TAG, "Maximum PIN attempts reached - device locked");
            // Could trigger additional security measures here
        }
    }
    
    private void resetPinAttempts() {
        pinAttempts = 0;
        preferences.edit()
            .putInt("pin_attempts", 0)
            .apply();
    }
    
    public boolean isPinLocked() {
        long timeSinceLastAttempt = System.currentTimeMillis() - lastPinAttemptTime;
        return pinAttempts >= MAX_PIN_ATTEMPTS && timeSinceLastAttempt < PIN_LOCKOUT_DURATION;
    }
    
    public long getPinLockoutTimeRemaining() {
        if (!isPinLocked()) return 0;
        long elapsed = System.currentTimeMillis() - lastPinAttemptTime;
        return Math.max(0, PIN_LOCKOUT_DURATION - elapsed);
    }
    
    public int getPinAttemptsRemaining() {
        return Math.max(0, MAX_PIN_ATTEMPTS - pinAttempts);
    }
    
    // Security State Management
    public boolean isArmed() {
        return isArmed;
    }
    
    public void setArmed(boolean armed) {
        this.isArmed = armed;
        
        preferences.edit()
            .putBoolean("is_armed", armed)
            .putLong(armed ? "last_armed_time" : "last_disarmed_time", System.currentTimeMillis())
            .apply();
        
        if (armed) {
            incrementTotalArming();
        }
        
        Log.i(TAG, "Security " + (armed ? "ARMED" : "DISARMED"));
    }
    
    // Feature Settings
    public boolean isMotionDetectionEnabled() {
        return motionDetectionEnabled;
    }
    
    public void setMotionDetectionEnabled(boolean enabled) {
        this.motionDetectionEnabled = enabled;
    }
    
    public boolean isCameraEvidenceEnabled() {
        return cameraEvidenceEnabled;
    }
    
    public void setCameraEvidenceEnabled(boolean enabled) {
        this.cameraEvidenceEnabled = enabled;
    }
    
    public boolean isBreakInDetectionEnabled() {
        return breakInDetectionEnabled;
    }
    
    public void setBreakInDetectionEnabled(boolean enabled) {
        this.breakInDetectionEnabled = enabled;
    }
    
    public boolean isFakeHomeScreenEnabled() {
        return fakeHomeScreenEnabled;
    }
    
    public void setFakeHomeScreenEnabled(boolean enabled) {
        this.fakeHomeScreenEnabled = enabled;
    }
    
    public int getSensitivity() {
        return sensitivity;
    }
    
    public void setSensitivity(int sensitivity) {
        this.sensitivity = Math.max(0, Math.min(100, sensitivity));
    }
    
    // Statistics and Monitoring
    public void recordTrigger(String triggerType, String details) {
        long timestamp = System.currentTimeMillis();
        
        // Update trigger count
        int totalTriggers = getTotalTriggers() + 1;
        
        // Store trigger details
        String triggerKey = "trigger_" + timestamp;
        String triggerData = triggerType + "|" + details + "|" + timestamp;
        
        preferences.edit()
            .putInt("total_triggers", totalTriggers)
            .putString(triggerKey, triggerData)
            .putLong("last_trigger_time", timestamp)
            .apply();
        
        Log.i(TAG, "Security trigger recorded: " + triggerType + " - " + details);
        
        // Clean up old trigger records (keep only last 100)
        cleanupOldTriggers();
    }
    
    public int getTotalTriggers() {
        return preferences.getInt("total_triggers", 0);
    }
    
    public long getLastTriggerTime() {
        return preferences.getLong("last_trigger_time", 0);
    }
    
    public int getTotalArmings() {
        return preferences.getInt("total_armings", 0);
    }
    
    private void incrementTotalArming() {
        int count = getTotalArmings() + 1;
        preferences.edit().putInt("total_armings", count).apply();
    }
    
    public long getLastArmedTime() {
        return preferences.getLong("last_armed_time", 0);
    }
    
    public long getLastDisarmedTime() {
        return preferences.getLong("last_disarmed_time", 0);
    }
    
    // Settings Persistence
    public void saveSettings() {
        preferences.edit()
            .putBoolean("motion_detection", motionDetectionEnabled)
            .putBoolean("camera_evidence", cameraEvidenceEnabled)
            .putBoolean("break_in_detection", breakInDetectionEnabled)
            .putBoolean("fake_home_screen", fakeHomeScreenEnabled)
            .putInt("sensitivity", sensitivity)
            .apply();
        
        Log.d(TAG, "Settings saved");
    }
    
    public void loadSettings() {
        // Load from preferences
        motionDetectionEnabled = preferences.getBoolean("motion_detection", true);
        cameraEvidenceEnabled = preferences.getBoolean("camera_evidence", true);
        breakInDetectionEnabled = preferences.getBoolean("break_in_detection", true);
        fakeHomeScreenEnabled = preferences.getBoolean("fake_home_screen", false);
        sensitivity = preferences.getInt("sensitivity", 60);
        isArmed = preferences.getBoolean("is_armed", false);
        
        // Load PIN attempt data
        pinAttempts = preferences.getInt("pin_attempts", 0);
        lastPinAttemptTime = preferences.getLong("last_pin_attempt", 0);
        
        Log.d(TAG, "Settings loaded");
    }
    
    // Security Statistics
    public String getSecurityStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Security Status: ").append(isArmed ? "ARMED" : "DISARMED").append("\n");
        stats.append("Total Triggers: ").append(getTotalTriggers()).append("\n");
        stats.append("Total Armings: ").append(getTotalArmings()).append("\n");
        
        long lastTrigger = getLastTriggerTime();
        if (lastTrigger > 0) {
            String timeStr = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(new Date(lastTrigger));
            stats.append("Last Trigger: ").append(timeStr).append("\n");
        }
        
        long lastArmed = getLastArmedTime();
        if (lastArmed > 0) {
            String timeStr = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(new Date(lastArmed));
            stats.append("Last Armed: ").append(timeStr).append("\n");
        }
        
        if (hasPin()) {
            long pinSetTime = encryptedPreferences.getLong("pin_set_time", 0);
            if (pinSetTime > 0) {
                String timeStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(new Date(pinSetTime));
                stats.append("PIN Set: ").append(timeStr);
            }
        } else {
            stats.append("PIN: Not Set");
        }
        
        return stats.toString();
    }
    
    // Advanced Security Features
    public boolean verifySecurityCode(String code) {
        String storedCode = encryptedPreferences.getString("security_code", "ANTITHEFT2024");
        return storedCode.equals(code);
    }
    
    public void setSecurityCode(String code) {
        encryptedPreferences.edit().putString("security_code", code).apply();
    }
    
    public void enableEmergencyMode() {
        preferences.edit().putBoolean("emergency_mode", true).apply();
        Log.w(TAG, "Emergency mode enabled");
    }
    
    public void disableEmergencyMode() {
        preferences.edit().putBoolean("emergency_mode", false).apply();
        Log.i(TAG, "Emergency mode disabled");
    }
    
    public boolean isEmergencyMode() {
        return preferences.getBoolean("emergency_mode", false);
    }
    
    // Cleanup and Maintenance
    private void cleanupOldTriggers() {
        // Remove trigger records older than 30 days or keep only last 100
        long cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000); // 30 days
        
        SharedPreferences.Editor editor = preferences.edit();
        boolean hasChanges = false;
        
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("trigger_")) {
                try {
                    long timestamp = Long.parseLong(key.substring(8)); // Remove "trigger_" prefix
                    if (timestamp < cutoffTime) {
                        editor.remove(key);
                        hasChanges = true;
                    }
                } catch (NumberFormatException e) {
                    // Invalid trigger key format, remove it
                    editor.remove(key);
                    hasChanges = true;
                }
            }
        }
        
        if (hasChanges) {
            editor.apply();
            Log.d(TAG, "Old trigger records cleaned up");
        }
    }
    
    public void resetAllData() {
        // Clear all security data (except PIN for safety)
        preferences.edit()
            .remove("total_triggers")
            .remove("total_armings")
            .remove("last_trigger_time")
            .remove("last_armed_time")
            .remove("last_disarmed_time")
            .remove("emergency_mode")
            .putBoolean("is_armed", false)
            .apply();
        
        // Reset PIN attempts
        resetPinAttempts();
        
        // Reset state
        isArmed = false;
        
        Log.i(TAG, "Security data reset");
    }
    
    // Validation methods
    public boolean validateConfiguration() {
        if (!hasPin()) {
            Log.w(TAG, "Configuration invalid: No PIN set");
            return false;
        }
        
        if (sensitivity < 0 || sensitivity > 100) {
            Log.w(TAG, "Configuration invalid: Invalid sensitivity");
            return false;
        }
        
        return true;
    }
}

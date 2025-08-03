// File: app/src/main/java/com/antitheft/security/AppDisguiseManager.java

package com.antitheft.security;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

public class AppDisguiseManager {
    private static final String TAG = "AntiTheft_Disguise";
    private static final String PREFS_NAME = "AppDisguisePrefs";
    
    private Context context;
    private SharedPreferences preferences;
    private PackageManager packageManager;
    
    // Disguise options
    public enum DisguiseType {
        NONE("Anti-Theft Security", "com.antitheft.security.MainActivity"),
        FILE_MANAGER("File Manager", "com.antitheft.security.FileManagerAlias"),
        CALCULATOR("Calculator", "com.antitheft.security.CalculatorAlias"),
        NOTES("Notes", "com.antitheft.security.NotesAlias"),
        WEATHER("Weather", "com.antitheft.security.WeatherAlias"),
        FLASHLIGHT("Flashlight", "com.antitheft.security.FlashlightAlias");
        
        private final String displayName;
        private final String aliasName;
        
        DisguiseType(String displayName, String aliasName) {
            this.displayName = displayName;
            this.aliasName = aliasName;
        }
        
        public String getDisplayName() { return displayName; }
        public String getAliasName() { return aliasName; }
    }
    
    public AppDisguiseManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.packageManager = context.getPackageManager();
    }
    
    public void applyDisguise(DisguiseType disguiseType) {
        Log.i(TAG, "Applying disguise: " + disguiseType.getDisplayName());
        
        try {
            // Disable the current active alias
            DisguiseType currentDisguise = getCurrentDisguise();
            if (currentDisguise != DisguiseType.NONE) {
                disableActivityAlias(currentDisguise.getAliasName());
            }
            
            // Enable the new disguise alias (if not NONE)
            if (disguiseType != DisguiseType.NONE) {
                enableActivityAlias(disguiseType.getAliasName());
                
                // Disable the original main activity
                disableActivityAlias("com.antitheft.security.MainActivity");
            } else {
                // Enable the original main activity
                enableActivityAlias("com.antitheft.security.MainActivity");
            }
            
            // Save the current disguise setting
            preferences.edit()
                .putString("current_disguise", disguiseType.name())
                .apply();
            
            Log.i(TAG, "Disguise applied successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying disguise", e);
        }
    }
    
    private void enableActivityAlias(String aliasName) {
        ComponentName componentName = new ComponentName(context, aliasName);
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        );
        Log.d(TAG, "Enabled activity alias: " + aliasName);
    }
    
    private void disableActivityAlias(String aliasName) {
        ComponentName componentName = new ComponentName(context, aliasName);
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
        Log.d(TAG, "Disabled activity alias: " + aliasName);
    }
    
    public DisguiseType getCurrentDisguise() {
        String disguiseName = preferences.getString("current_disguise", DisguiseType.NONE.name());
        try {
            return DisguiseType.valueOf(disguiseName);
        } catch (IllegalArgumentException e) {
            return DisguiseType.NONE;
        }
    }
    
    public boolean isDisguiseActive() {
        return getCurrentDisguise() != DisguiseType.NONE;
    }
    
    public void removeDisguise() {
        applyDisguise(DisguiseType.NONE);
    }
    
    // Get all available disguise options
    public DisguiseType[] getAvailableDisguises() {
        return DisguiseType.values();
    }
    
    // Check if disguise is properly applied
    public boolean verifyDisguise(DisguiseType disguiseType) {
        try {
            if (disguiseType == DisguiseType.NONE) {
                // Check if main activity is enabled
                ComponentName mainActivity = new ComponentName(context, "com.antitheft.security.MainActivity");
                int state = packageManager.getComponentEnabledSetting(mainActivity);
                return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                       state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            } else {
                // Check if disguise alias is enabled
                ComponentName aliasActivity = new ComponentName(context, disguiseType.getAliasName());
                int state = packageManager.getComponentEnabledSetting(aliasActivity);
                return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying disguise", e);
            return false;
        }
    }
    
    // Get disguise statistics
    public String getDisguiseStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Current Disguise: ").append(getCurrentDisguise().getDisplayName()).append("\n");
        stats.append("Disguise Active: ").append(isDisguiseActive() ? "Yes" : "No").append("\n");
        stats.append("Times Changed: ").append(getDisguiseChangeCount()).append("\n");
        
        long lastChanged = getLastDisguiseChangeTime();
        if (lastChanged > 0) {
            stats.append("Last Changed: ").append(
                new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(lastChanged))
            );
        } else {
            stats.append("Last Changed: Never");
        }
        
        return stats.toString();
    }
    
    private int getDisguiseChangeCount() {
        return preferences.getInt("disguise_change_count", 0);
    }
    
    private void incrementDisguiseChangeCount() {
        int count = getDisguiseChangeCount() + 1;
        preferences.edit()
            .putInt("disguise_change_count", count)
            .putLong("last_disguise_change", System.currentTimeMillis())
            .apply();
    }
    
    private long getLastDisguiseChangeTime() {
        return preferences.getLong("last_disguise_change", 0);
    }
    
    // Security method to verify app identity for advanced users
    public boolean verifyAppIdentity(String secretCode) {
        String storedCode = preferences.getString("app_identity_code", "ANTITHEFT2024");
        return storedCode.equals(secretCode);
    }
    
    public void setAppIdentityCode(String code) {
        preferences.edit().putString("app_identity_code", code).apply();
    }
}

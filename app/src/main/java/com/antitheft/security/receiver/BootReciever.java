// File Path: app/src/main/java/com/antitheft/security/receiver/BootReceiver.java
package com.antitheft.security.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.antitheft.security.service.SecurityService;
import com.antitheft.security.utils.EvidenceCollector;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            "android.intent.action.PACKAGE_REPLACED".equals(action)) {
            
            Log.d(TAG, "Boot completed or package replaced: " + action);
            
            SharedPreferences preferences = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE);
            
            // Log boot event
            EvidenceCollector evidenceCollector = new EvidenceCollector(context);
            evidenceCollector.collectSystemEvents("DEVICE_BOOT", "Device was rebooted or app was updated");
            
            // Check if anti-theft was previously enabled
            boolean wasAntiTheftEnabled = preferences.getBoolean("anti_theft_enabled", false);
            
            if (wasAntiTheftEnabled) {
                // Restart security service
                Intent serviceIntent = new Intent(context, SecurityService.class);
                serviceIntent.setAction("AUTO_START");
                context.startForegroundService(serviceIntent);
                
                Log.d(TAG, "Anti-theft service restarted after boot");
                
                // Send alert if enabled
                if (preferences.getBoolean("boot_alert_enabled", true)) {
                    String emergencyContact = preferences.getString("emergency_contact", "");
                    if (!emergencyContact.isEmpty()) {
                        // Note: SMS sending from boot receiver might be restricted on newer Android versions
                        evidenceCollector.collectSecurityEvent("BOOT_ALERT", "Device rebooted - Anti-theft reactivated");
                    }
                }
            }
            
            // Mark service state
            preferences.edit().putBoolean("service_auto_started", true).apply();
        }
    }
}

// File Path: app/src/main/java/com/antitheft/security/receiver/DeviceAdminReceiver.java
package com.antitheft.security.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.antitheft.security.utils.EvidenceCollector;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    
    private static final String TAG = "DeviceAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device admin enabled");
        
        EvidenceCollector evidenceCollector = new EvidenceCollector(context);
        evidenceCollector.collectSecurityEvent("DEVICE_ADMIN_ENABLED", "Device admin privileges granted");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device admin disabled");
        
        EvidenceCollector evidenceCollector = new EvidenceCollector(context);
        evidenceCollector.collectSecurityEvent("DEVICE_ADMIN_DISABLED", "Device admin privileges revoked");
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
        Log.d(TAG, "Password changed");
        
        EvidenceCollector evidenceCollector = new EvidenceCollector(context);
        evidenceCollector.collectSecurityEvent("PASSWORD_CHANGED", "Device password/PIN was changed");
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Password failed");
        
        EvidenceCollector evidenceCollector = new EvidenceCollector(context);
        evidenceCollector.collectSecurityEvent("PASSWORD_FAILED", "Incorrect password/PIN entered");
        
        // Notify SecurityService of failed attempt
        Intent serviceIntent = new Intent(context, com.antitheft.security.service.SecurityService.class);
        serviceIntent.setAction("PASSWORD_FAILED");
        context.startForegroundService(serviceIntent);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Password succeeded");
        
        // Reset wrong attempt counter
        context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
               .edit()
               .putInt("wrong_attempts", 0)
               .apply();
    }
}

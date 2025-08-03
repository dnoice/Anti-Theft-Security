// File: app/src/main/java/com/antitheft/security/BootReceiver.java

package com.antitheft.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "AntiTheft_BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "Received broadcast: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                handleBootCompleted(context);
                break;
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_PACKAGE_REPLACED:
                handlePackageReplaced(context);
                break;
        }
    }

    private void handleBootCompleted(Context context) {
        Log.i(TAG, "Device boot completed - checking security settings");
        
        try {
            SecurityManager securityManager = new SecurityManager(context);
            
            // Check if security was previously armed
            if (securityManager.isArmed()) {
                Log.i(TAG, "Security was armed before reboot - restarting monitoring");
                
                // Restart motion detection service
                Intent serviceIntent = new Intent(context, SensorService.class);
                context.startService(serviceIntent);
                
                // Start break-in detection if enabled
                if (securityManager.isBreakInDetectionEnabled()) {
                    BreakInDetectionManager breakInManager = new BreakInDetectionManager(context);
                    breakInManager.startMonitoring(new BreakInDetectionManager.BreakInDetectionCallback() {
                        @Override
                        public void onUnlockAttemptDetected(int attemptNumber) {
                            Log.w(TAG, "Boot receiver detected unlock attempt: " + attemptNumber);
                        }
                        
                        @Override
                        public void onSuspiciousActivity(String description, int failedAttempts) {
                            Log.w(TAG, "Boot receiver detected suspicious activity: " + description);
                        }
                        
                        @Override
                        public void onSuccessfulBreakIn(long breakInTime) {
                            Log.e(TAG, "Boot receiver detected successful break-in!");
                            
                            // Start evidence collection
                            EvidenceManager evidenceManager = new EvidenceManager(context);
                            evidenceManager.captureSecurityEvidence("Post-reboot break-in detected");
                        }
                        
                        @Override
                        public void onDeviceLocked() {
                            Log.d(TAG, "Device locked after boot");
                        }
                        
                        @Override
                        public void onDeviceUnlocked(boolean wasBreakIn) {
                            Log.d(TAG, "Device unlocked after boot - Break-in: " + wasBreakIn);
                        }
                    });
                }
                
                Log.i(TAG, "Security monitoring restarted after boot");
            } else {
                Log.d(TAG, "Security was not armed before reboot - no action needed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling boot completed", e);
        }
    }

    private void handlePackageReplaced(Context context) {
        Log.i(TAG, "App package replaced - checking security status");
        
        try {
            SecurityManager securityManager = new SecurityManager(context);
            
            if (securityManager.isArmed()) {
                Log.i(TAG, "Security was armed before update - restarting services");
                
                // Restart motion detection service
                Intent serviceIntent = new Intent(context, SensorService.class);
                context.startService(serviceIntent);
                
                Log.i(TAG, "Security services restarted after app update");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling package replaced", e);
        }
    }
}

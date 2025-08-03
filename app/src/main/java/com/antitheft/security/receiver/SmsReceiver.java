// File Path: app/src/main/java/com/antitheft/security/receiver/SmsReceiver.java
package com.antitheft.security.receiver;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.antitheft.security.service.SecurityService;
import com.antitheft.security.utils.EvidenceCollector;

public class SmsReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsReceiver";
    
    // Remote commands
    private static final String CMD_LOCATE = "LOCATE";
    private static final String CMD_ALARM = "ALARM";
    private static final String CMD_LOCK = "LOCK";
    private static final String CMD_WIPE = "WIPE";
    private static final String CMD_EVIDENCE = "EVIDENCE";
    private static final String CMD_STATUS = "STATUS";
    private static final String CMD_PHOTO = "PHOTO";
    private static final String CMD_AUDIO = "AUDIO";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = sms.getOriginatingAddress();
                        String message = sms.getMessageBody();
                        
                        handleSmsCommand(context, sender, message);
                    }
                }
            }
        }
    }

    private void handleSmsCommand(Context context, String sender, String message) {
        SharedPreferences preferences = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE);
        
        // Check if SMS alerts are enabled
        if (!preferences.getBoolean("sms_alerts", true)) {
            return;
        }
        
        // Verify sender is the emergency contact
        String emergencyContact = preferences.getString("emergency_contact", "");
        if (!sender.contains(emergencyContact.replace(" ", "").replace("-", "").replace("(", "").replace(")", ""))) {
            Log.d(TAG, "SMS command ignored - not from emergency contact");
            return;
        }
        
        // Parse command
        String[] parts = message.trim().toUpperCase().split(" ");
        if (parts.length == 0) {
            return;
        }
        
        String command = parts[0];
        String pin = parts.length > 1 ? parts[1] : "";
        
        // Verify security PIN
        String securityPin = preferences.getString("security_pin", "");
        if (!pin.equals(securityPin)) {
            Log.d(TAG, "SMS command ignored - invalid PIN");
            sendSmsResponse(context, sender, "Invalid security PIN");
            return;
        }
        
        Log.d(TAG, "Processing SMS command: " + command);
        
        // Log security event
        EvidenceCollector evidenceCollector = new EvidenceCollector(context);
        evidenceCollector.collectSecurityEvent("SMS_COMMAND", "Command: " + command + " from: " + sender);
        
        // Execute command
        switch (command) {
            case CMD_LOCATE:
                handleLocateCommand(context, sender);
                break;
                
            case CMD_ALARM:
                handleAlarmCommand(context, sender);
                break;
                
            case CMD_LOCK:
                handleLockCommand(context, sender);
                break;
                
            case CMD_WIPE:
                handleWipeCommand(context, sender);
                break;
                
            case CMD_EVIDENCE:
                handleEvidenceCommand(context, sender);
                break;
                
            case CMD_STATUS:
                handleStatusCommand(context, sender);
                break;
                
            case CMD_PHOTO:
                handlePhotoCommand(context, sender);
                break;
                
            case CMD_AUDIO:
                handleAudioCommand(context, sender);
                break;
                
            default:
                sendSmsResponse(context, sender, "Unknown command. Available: LOCATE, ALARM, LOCK, WIPE, EVIDENCE, STATUS, PHOTO, AUDIO");
                break;
        }
    }

    private void handleLocateCommand(Context context, String sender) {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            
            if (locationManager != null) {
                Location location = null;
                
                // Try GPS first
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                
                // Fall back to network
                if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (location != null) {
                    String locationMsg = String.format("Device location: https://maps.google.com/?q=%f,%f (Accuracy: %.0fm)",
                        location.getLatitude(), location.getLongitude(), location.getAccuracy());
                    sendSmsResponse(context, sender, locationMsg);
                    
                    // Save location evidence
                    EvidenceCollector evidenceCollector = new EvidenceCollector(context);
                    evidenceCollector.saveLocationEvidence(location);
                } else {
                    sendSmsResponse(context, sender, "Unable to get current location. GPS may be disabled.");
                }
            }
        } catch (SecurityException e) {
            sendSmsResponse(context, sender, "Location permission denied");
        }
    }

    private void handleAlarmCommand(Context context, String sender) {
        try {
            // Play alarm sound at maximum volume
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
                
                MediaPlayer mediaPlayer = MediaPlayer.create(context, Uri.parse("android.resource://com.antitheft.security/raw/alarm_sound"));
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    
                    // Restore original volume after alarm
                    mediaPlayer.setOnCompletionListener(mp -> {
                        mp.release();
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
                    });
                }
            }
            
            sendSmsResponse(context, sender, "Alarm activated");
        } catch (Exception e) {
            sendSmsResponse(context, sender, "Failed to activate alarm: " + e.getMessage());
        }
    }

    private void handleLockCommand(Context context, String sender) {
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName deviceAdminComponent = new ComponentName(context, DeviceAdminReceiver.class);
            
            if (devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                devicePolicyManager.lockNow();
                sendSmsResponse(context, sender, "Device locked successfully");
            } else {
                sendSmsResponse(context, sender, "Device admin not enabled - cannot lock device");
            }
        } catch (Exception e) {
            sendSmsResponse(context, sender, "Failed to lock device: " + e.getMessage());
        }
    }

    private void handleWipeCommand(Context context, String sender) {
        SharedPreferences preferences = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE);
        
        if (!preferences.getBoolean("remote_wipe_enabled", false)) {
            sendSmsResponse(context, sender, "Remote wipe is disabled");
            return;
        }
        
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName deviceAdminComponent = new ComponentName(context, DeviceAdminReceiver.class);
            
            if (devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                sendSmsResponse(context, sender, "FACTORY RESET INITIATED - Device will wipe in 10 seconds");
                
                // Delay to allow SMS to be sent
                new Thread(() -> {
                    try {
                        Thread.sleep(10000);
                        devicePolicyManager.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Wipe delay interrupted", e);
                    }
                }).start();
            } else {
                sendSmsResponse(context, sender, "Device admin not enabled - cannot wipe device");
            }
        } catch (Exception e) {
            sendSmsResponse(context, sender, "Failed to wipe device: " + e.getMessage());
        }
    }

    private void handleEvidenceCommand(Context context, String sender) {
        // Trigger immediate evidence collection
        Intent serviceIntent = new Intent(context, SecurityService.class);
        serviceIntent.setAction("COLLECT_EVIDENCE");
        context.startForegroundService(serviceIntent);
        
        sendSmsResponse(context, sender, "Evidence collection initiated. Photos and audio being captured.");
    }

    private void handleStatusCommand(Context context, String sender) {
        SharedPreferences preferences = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE);
        
        boolean antiTheftActive = SecurityService.isRunning();
        boolean stealthMode = preferences.getBoolean("stealth_mode", false);
        boolean locationTracking = preferences.getBoolean("location_tracking", true);
        int wrongAttempts = preferences.getInt("wrong_attempts", 0);
        boolean theftDetected = preferences.getBoolean("theft_detected", false);
        
        String status = String.format("Status: %s | Stealth: %s | Location: %s | Wrong attempts: %d | Theft: %s",
            antiTheftActive ? "ACTIVE" : "INACTIVE",
            stealthMode ? "ON" : "OFF",
            locationTracking ? "ON" : "OFF",
            wrongAttempts,
            theftDetected ? "DETECTED" : "NONE");
            
        sendSmsResponse(context, sender, status);
    }

    private void handlePhotoCommand(Context context, String sender) {
        // Trigger photo capture
        Intent serviceIntent = new Intent(context, SecurityService.class);
        serviceIntent.setAction("CAPTURE_PHOTO");
        context.startForegroundService(serviceIntent);
        
        sendSmsResponse(context, sender, "Photo capture initiated");
    }

    private void handleAudioCommand(Context context, String sender) {
        // Trigger audio recording
        Intent serviceIntent = new Intent(context, SecurityService.class);
        serviceIntent.setAction("RECORD_AUDIO");
        context.startForegroundService(serviceIntent);
        
        sendSmsResponse(context, sender, "Audio recording started (30 seconds)");
    }

    private void sendSmsResponse(Context context, String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d(TAG, "SMS response sent: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS response", e);
        }
    }
}

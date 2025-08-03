// File: app/src/main/java/com/antitheft/security/SmartNotificationManager.java

package com.antitheft.security;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SmartNotificationManager {
    private static final String TAG = "AntiTheft_SmartNotify";
    private static final String PREFS_NAME = "SmartNotificationPrefs";
    private static final String DELAYED_NOTIFICATIONS_CHANNEL = "delayed_notifications";
    private static final String DISGUISED_NOTIFICATIONS_CHANNEL = "disguised_notifications";
    
    private Context context;
    private SharedPreferences preferences;
    private NotificationManager notificationManager;
    private ScheduledExecutorService scheduler;
    private EmailManager emailManager;
    
    // Notification queue for delayed delivery
    private List<PendingNotification> pendingNotifications = new ArrayList<>();
    
    public SmartNotificationManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.emailManager = new EmailManager(context);
        
        createNotificationChannels();
        startDeviceUnlockMonitoring();
    }
    
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for delayed notifications
            NotificationChannel delayedChannel = new NotificationChannel(
                DELAYED_NOTIFICATIONS_CHANNEL,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            delayedChannel.setDescription("Delayed security notifications");
            notificationManager.createNotificationChannel(delayedChannel);
            
            // Channel for disguised notifications
            NotificationChannel disguisedChannel = new NotificationChannel(
                DISGUISED_NOTIFICATIONS_CHANNEL,
                "System Updates",
                NotificationManager.IMPORTANCE_LOW
            );
            disguisedChannel.setDescription("System and app notifications");
            notificationManager.createNotificationChannel(disguisedChannel);
        }
    }
    
    private void startDeviceUnlockMonitoring() {
        // Monitor for device unlock events
        scheduler.scheduleAtFixedRate(() -> {
            if (isDeviceUnlocked()) {
                processDelayedNotifications();
            }
        }, 5, 5, TimeUnit.SECONDS); // Check every 5 seconds
    }
    
    // Queue a notification for delayed delivery
    public void queueSecurityNotification(String title, String message, List<String> evidencePaths, boolean isHighPriority) {
        Log.i(TAG, "Queueing security notification for delayed delivery");
        
        PendingNotification notification = new PendingNotification();
        notification.title = title;
        notification.message = message;
        notification.evidencePaths = evidencePaths != null ? new ArrayList<>(evidencePaths) : new ArrayList<>();
        notification.timestamp = System.currentTimeMillis();
        notification.isHighPriority = isHighPriority;
        notification.id = generateNotificationId();
        
        pendingNotifications.add(notification);
        
        // If device is currently unlocked, send immediately
        if (isDeviceUnlocked()) {
            processDelayedNotifications();
        } else {
            // Show disguised notification if enabled
            if (isDisguisedNotificationsEnabled()) {
                showDisguisedNotification();
            }
        }
        
        // Also queue email notifications
        queueEmailNotification(notification);
    }
    
    // Send immediate disguised notification that doesn't reveal the security breach
    private void showDisguisedNotification() {
        String[] disguisedTitles = {
            "System Update Available",
            "App Update Completed",
            "Storage Optimization Complete",
            "Battery Optimization Applied",
            "Security Scan Complete"
        };
        
        String[] disguisedMessages = {
            "System performance improvements installed",
            "Latest features and improvements added",
            "Storage space has been optimized",
            "Battery usage has been optimized",
            "System security check completed"
        };
        
        int randomIndex = (int) (Math.random() * disguisedTitles.length);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DISGUISED_NOTIFICATIONS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(disguisedTitles[randomIndex])
            .setContentText(disguisedMessages[randomIndex])
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true);
        
        notificationManager.notify(generateNotificationId(), builder.build());
        Log.d(TAG, "Disguised notification sent");
    }
    
    // Process all pending notifications when device is unlocked
    private void processDelayedNotifications() {
        if (pendingNotifications.isEmpty()) return;
        
        Log.i(TAG, "Device unlocked - processing " + pendingNotifications.size() + " delayed notifications");
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        for (PendingNotification notification : pendingNotifications) {
            mainHandler.post(() -> showSecurityNotification(notification));
        }
        
        pendingNotifications.clear();
    }
    
    private void showSecurityNotification(PendingNotification notification) {
        // Create detailed security notification
        String timeStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            .format(new Date(notification.timestamp));
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DELAYED_NOTIFICATIONS_CHANNEL)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("🚨 " + notification.title)
            .setContentText(notification.message)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(notification.message + "\n\nTime: " + timeStr + 
                        "\nEvidence collected: " + notification.evidencePaths.size() + " files"))
            .setPriority(notification.isHighPriority ? 
                NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 500, 250, 500})
            .setColor(context.getColor(R.color.status_armed));
        
        // Add action buttons
        // View Evidence action
        if (!notification.evidencePaths.isEmpty()) {
            // Add intent to view evidence gallery
            builder.addAction(R.drawable.ic_camera, "View Evidence", null);
        }
        
        notificationManager.notify(notification.id, builder.build());
        Log.d(TAG, "Security notification displayed");
    }
    
    private void queueEmailNotification(PendingNotification notification) {
        if (!isEmailNotificationsEnabled()) return;
        
        // Add delay to email sending based on settings
        int emailDelayMinutes = getEmailDelayMinutes();
        
        scheduler.schedule(() -> {
            sendEmailNotification(notification);
        }, emailDelayMinutes, TimeUnit.MINUTES);
        
        Log.d(TAG, "Email notification queued for " + emailDelayMinutes + " minutes delay");
    }
    
    private void sendEmailNotification(PendingNotification notification) {
        String subject = getEmailSubject();
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date(notification.timestamp));
        
        StringBuilder emailBody = new StringBuilder();
        emailBody.append("Security Alert - ").append(notification.title).append("\n\n");
        emailBody.append("Time: ").append(timeStr).append("\n");
        emailBody.append("Message: ").append(notification.message).append("\n\n");
        
        if (!notification.evidencePaths.isEmpty()) {
            emailBody.append("Evidence Collected:\n");
            for (int i = 0; i < notification.evidencePaths.size(); i++) {
                String path = notification.evidencePaths.get(i);
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                emailBody.append("- ").append(fileName).append("\n");
            }
            emailBody.append("\nEvidence files are attached to this email.\n\n");
        }
        
        emailBody.append("Device Information:\n");
        emailBody.append("Model: ").append(android.os.Build.MODEL).append("\n");
        emailBody.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        
        List<String> recipients = getEmailRecipients();
        for (String recipient : recipients) {
            emailManager.sendSecurityAlert(recipient, subject, emailBody.toString(), notification.evidencePaths);
        }
        
        Log.i(TAG, "Email notifications sent to " + recipients.size() + " recipients");
    }
    
    private boolean isDeviceUnlocked() {
        // Check if device is currently unlocked
        try {
            // This is a simplified check - in practice, you might use KeyguardManager
            // or monitor for ACTION_USER_PRESENT broadcasts
            return Settings.Global.getInt(context.getContentResolver(), 
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking device unlock status", e);
            return true; // Assume unlocked on error
        }
    }
    
    // Settings methods
    public boolean isDisguisedNotificationsEnabled() {
        return preferences.getBoolean("disguised_notifications", true);
    }
    
    public void setDisguisedNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean("disguised_notifications", enabled).apply();
    }
    
    public boolean isEmailNotificationsEnabled() {
        return preferences.getBoolean("email_notifications", false);
    }
    
    public void setEmailNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean("email_notifications", enabled).apply();
    }
    
    public int getEmailDelayMinutes() {
        return preferences.getInt("email_delay_minutes", 5);
    }
    
    public void setEmailDelayMinutes(int minutes) {
        preferences.edit().putInt("email_delay_minutes", minutes).apply();
    }
    
    public String getEmailSubject() {
        return preferences.getString("email_subject", "System Status Update");
    }
    
    public void setEmailSubject(String subject) {
        preferences.edit().putString("email_subject", subject).apply();
    }
    
    public List<String> getEmailRecipients() {
        String recipientsStr = preferences.getString("email_recipients", "");
        List<String> recipients = new ArrayList<>();
        if (!recipientsStr.isEmpty()) {
            String[] emails = recipientsStr.split(",");
            for (String email : emails) {
                email = email.trim();
                if (!email.isEmpty()) {
                    recipients.add(email);
                }
            }
        }
        return recipients;
    }
    
    public void setEmailRecipients(List<String> recipients) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(recipients.get(i).trim());
        }
        preferences.edit().putString("email_recipients", sb.toString()).apply();
    }
    
    public void addEmailRecipient(String email) {
        List<String> recipients = getEmailRecipients();
        if (!recipients.contains(email.trim())) {
            recipients.add(email.trim());
            setEmailRecipients(recipients);
        }
    }
    
    public void removeEmailRecipient(String email) {
        List<String> recipients = getEmailRecipients();
        recipients.remove(email.trim());
        setEmailRecipients(recipients);
    }
    
    // Get statistics
    public int getPendingNotificationCount() {
        return pendingNotifications.size();
    }
    
    public int getTotalNotificationsSent() {
        return preferences.getInt("total_notifications_sent", 0);
    }
    
    private void incrementNotificationsSent() {
        int total = getTotalNotificationsSent() + 1;
        preferences.edit().putInt("total_notifications_sent", total).apply();
    }
    
    private int generateNotificationId() {
        return (int) System.currentTimeMillis();
    }
    
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        if (emailManager != null) {
            emailManager.cleanup();
        }
    }
    
    // Inner class for pending notifications
    private static class PendingNotification {
        String title;
        String message;
        List<String> evidencePaths;
        long timestamp;
        boolean isHighPriority;
        int id;
    }
}

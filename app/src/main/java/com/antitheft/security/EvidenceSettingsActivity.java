// File: app/src/main/java/com/antitheft/security/EvidenceSettingsActivity.java

package com.antitheft.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class EvidenceSettingsActivity extends Activity {
    private static final String TAG = "AntiTheft_EvidenceSettings";
    
    private EvidenceManager evidenceManager;
    private SmartNotificationManager notificationManager;
    
    // Evidence settings controls
    private Switch switchPhotoEvidence, switchVideoEvidence, switchScreenshotEvidence;
    private Switch switchAutoEmail, switchDelayedNotifications, switchDisguisedNotifications;
    private SeekBar seekRetentionDays, seekEmailDelay;
    private TextView txtRetentionDays, txtEmailDelay, txtStorageUsed, txtEvidenceStats;
    private EditText editEmailRecipients;
    private Button btnTestCapture, btnClearEvidence, btnExportSettings, btnImportSettings;
    
    // Current settings
    private boolean photoEvidenceEnabled;
    private boolean videoEvidenceEnabled;
    private boolean screenshotEvidenceEnabled;
    private boolean autoEmailEnabled;
    private boolean delayedNotificationsEnabled;
    private boolean disguisedNotificationsEnabled;
    private int retentionDays;
    private int emailDelayMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evidence_settings);
        
        initializeManagers();
        initializeViews();
        setupEventListeners();
        loadCurrentSettings();
        updateUI();
        
        Log.i(TAG, "Evidence settings opened");
    }

    private void initializeManagers() {
        evidenceManager = new EvidenceManager(this);
        notificationManager = new SmartNotificationManager(this);
    }

    private void initializeViews() {
        // Evidence type switches
        switchPhotoEvidence = findViewById(R.id.switchPhotoEvidence);
        switchVideoEvidence = findViewById(R.id.switchVideoEvidence);
        switchScreenshotEvidence = findViewById(R.id.switchScreenshotEvidence);
        
        // Notification switches
        switchAutoEmail = findViewById(R.id.switchAutoEmail);
        switchDelayedNotifications = findViewById(R.id.switchDelayedNotifications);
        switchDisguisedNotifications = findViewById(R.id.switchDisguisedNotifications);
        
        // Seek bars
        seekRetentionDays = findViewById(R.id.seekRetentionDays);
        seekEmailDelay = findViewById(R.id.seekEmailDelay);
        
        // Text views
        txtRetentionDays = findViewById(R.id.txtRetentionDays);
        txtEmailDelay = findViewById(R.id.txtEmailDelay);
        txtStorageUsed = findViewById(R.id.txtStorageUsed);
        txtEvidenceStats = findViewById(R.id.txtEvidenceStats);
        
        // Edit text
        editEmailRecipients = findViewById(R.id.editEmailRecipients);
        
        // Buttons
        btnTestCapture = findViewById(R.id.btnTestCapture);
        btnClearEvidence = findViewById(R.id.btnClearEvidence);
        btnExportSettings = findViewById(R.id.btnExportSettings);
        btnImportSettings = findViewById(R.id.btnImportSettings);
    }

    private void setupEventListeners() {
        // Evidence type switches
        switchPhotoEvidence.setOnCheckedChangeListener((v, checked) -> {
            photoEvidenceEnabled = checked;
            saveSettings();
        });
        
        switchVideoEvidence.setOnCheckedChangeListener((v, checked) -> {
            videoEvidenceEnabled = checked;
            saveSettings();
            if (checked) {
                showVideoWarning();
            }
        });
        
        switchScreenshotEvidence.setOnCheckedChangeListener((v, checked) -> {
            screenshotEvidenceEnabled = checked;
            saveSettings();
        });
        
        // Notification switches
        switchAutoEmail.setOnCheckedChangeListener((v, checked) -> {
            autoEmailEnabled = checked;
            notificationManager.setEmailNotificationsEnabled(checked);
            updateEmailSettingsVisibility();
            saveSettings();
        });
        
        switchDelayedNotifications.setOnCheckedChangeListener((v, checked) -> {
            delayedNotificationsEnabled = checked;
            saveSettings();
        });
        
        switchDisguisedNotifications.setOnCheckedChangeListener((v, checked) -> {
            disguisedNotificationsEnabled = checked;
            notificationManager.setDisguisedNotificationsEnabled(checked);
            saveSettings();
        });
        
        // Retention days seek bar
        seekRetentionDays.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    retentionDays = Math.max(1, progress);
                    updateRetentionDisplay();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });
        
        // Email delay seek bar
        seekEmailDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    emailDelayMinutes = progress;
                    updateEmailDelayDisplay();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                notificationManager.setEmailDelayMinutes(emailDelayMinutes);
                saveSettings();
            }
        });
        
        // Buttons
        btnTestCapture.setOnClickListener(v -> testEvidenceCapture());
        btnClearEvidence.setOnClickListener(v -> clearAllEvidence());
        btnExportSettings.setOnClickListener(v -> exportSettings());
        btnImportSettings.setOnClickListener(v -> importSettings());
    }

    private void loadCurrentSettings() {
        evidenceManager.loadSettings();
        
        photoEvidenceEnabled = evidenceManager.isPhotoEvidenceEnabled();
        videoEvidenceEnabled = evidenceManager.isVideoEvidenceEnabled();
        screenshotEvidenceEnabled = evidenceManager.isScreenshotEvidenceEnabled();
        autoEmailEnabled = evidenceManager.isAutoEmailEnabled();
        retentionDays = evidenceManager.getEvidenceRetentionDays();
        
        delayedNotificationsEnabled = true; // Always enabled for security
        disguisedNotificationsEnabled = notificationManager.isDisguisedNotificationsEnabled();
        emailDelayMinutes = notificationManager.getEmailDelayMinutes();
        
        // Load email recipients
        List<String> recipients = notificationManager.getEmailRecipients();
        StringBuilder recipientsText = new StringBuilder();
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) recipientsText.append(", ");
            recipientsText.append(recipients.get(i));
        }
        editEmailRecipients.setText(recipientsText.toString());
    }

    private void updateUI() {
        // Set switch states
        switchPhotoEvidence.setChecked(photoEvidenceEnabled);
        switchVideoEvidence.setChecked(videoEvidenceEnabled);
        switchScreenshotEvidence.setChecked(screenshotEvidenceEnabled);
        switchAutoEmail.setChecked(autoEmailEnabled);
        switchDelayedNotifications.setChecked(delayedNotificationsEnabled);
        switchDisguisedNotifications.setChecked(disguisedNotificationsEnabled);
        
        // Set seek bar values
        seekRetentionDays.setProgress(retentionDays);
        seekEmailDelay.setProgress(emailDelayMinutes);
        
        // Update displays
        updateRetentionDisplay();
        updateEmailDelayDisplay();
        updateStorageDisplay();
        updateEvidenceStats();
        updateEmailSettingsVisibility();
    }

    private void updateRetentionDisplay() {
        String text = "Evidence Retention: " + retentionDays + " days";
        txtRetentionDays.setText(text);
    }

    private void updateEmailDelayDisplay() {
        String text;
        if (emailDelayMinutes == 0) {
            text = "Email Delay: Immediate";
        } else if (emailDelayMinutes < 60) {
            text = "Email Delay: " + emailDelayMinutes + " minutes";
        } else {
            int hours = emailDelayMinutes / 60;
            int minutes = emailDelayMinutes % 60;
            if (minutes == 0) {
                text = "Email Delay: " + hours + " hour" + (hours > 1 ? "s" : "");
            } else {
                text = "Email Delay: " + hours + "h " + minutes + "m";
            }
        }
        txtEmailDelay.setText(text);
    }

    private void updateStorageDisplay() {
        long totalSize = evidenceManager.getTotalEvidenceSize();
        String sizeText = formatFileSize(totalSize);
        txtStorageUsed.setText("Storage Used: " + sizeText);
    }

    private void updateEvidenceStats() {
        int totalFiles = evidenceManager.getEvidenceCount();
        int totalSessions = evidenceManager.getTotalEvidenceSessions();
        
        StringBuilder stats = new StringBuilder();
        stats.append("Evidence Files: ").append(totalFiles).append("\n");
        stats.append("Security Sessions: ").append(totalSessions).append("\n");
        stats.append("Photo Evidence: ").append(photoEvidenceEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Video Evidence: ").append(videoEvidenceEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Screenshot Evidence: ").append(screenshotEvidenceEnabled ? "Enabled" : "Disabled");
        
        txtEvidenceStats.setText(stats.toString());
    }

    private void updateEmailSettingsVisibility() {
        int visibility = autoEmailEnabled ? View.VISIBLE : View.GONE;
        findViewById(R.id.emailSettingsSection).setVisibility(visibility);
    }

    private void saveSettings() {
        evidenceManager.setPhotoEvidenceEnabled(photoEvidenceEnabled);
        evidenceManager.setVideoEvidenceEnabled(videoEvidenceEnabled);
        evidenceManager.setScreenshotEvidenceEnabled(screenshotEvidenceEnabled);
        evidenceManager.setAutoEmailEnabled(autoEmailEnabled);
        evidenceManager.setEvidenceRetentionDays(retentionDays);
        evidenceManager.saveSettings();
        
        // Save email recipients
        String recipientsText = editEmailRecipients.getText().toString();
        if (!recipientsText.trim().isEmpty()) {
            List<String> recipients = new ArrayList<>();
            String[] emails = recipientsText.split(",");
            for (String email : emails) {
                email = email.trim();
                if (!email.isEmpty() && isValidEmail(email)) {
                    recipients.add(email);
                }
            }
            notificationManager.setEmailRecipients(recipients);
        }
        
        updateUI();
        Log.d(TAG, "Evidence settings saved");
    }

    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private void showVideoWarning() {
        new AlertDialog.Builder(this)
            .setTitle("Video Evidence Warning")
            .setMessage("Video evidence uses more storage space and battery. Recording may be noticeable to intruders. Continue?")
            .setPositiveButton("Enable", (dialog, which) -> {
                // Video remains enabled
            })
            .setNegativeButton("Disable", (dialog, which) -> {
                switchVideoEvidence.setChecked(false);
                videoEvidenceEnabled = false;
                saveSettings();
            })
            .show();
    }

    private void testEvidenceCapture() {
        btnTestCapture.setEnabled(false);
        btnTestCapture.setText("Testing...");
        
        Toast.makeText(this, "Starting test evidence capture", Toast.LENGTH_SHORT).show();
        
        evidenceManager.captureSecurityEvidence("Settings test capture");
        
        // Re-enable button after delay
        findViewById(android.R.id.content).postDelayed(() -> {
            btnTestCapture.setEnabled(true);
            btnTestCapture.setText("Test Capture");
            Toast.makeText(this, "Test evidence capture completed", Toast.LENGTH_SHORT).show();
            updateUI(); // Refresh stats
        }, 5000);
    }

    private void clearAllEvidence() {
        new AlertDialog.Builder(this)
            .setTitle("Clear All Evidence")
            .setMessage("This will permanently delete all evidence files and sessions. This action cannot be undone.\n\nAre you sure?")
            .setPositiveButton("Clear All", (dialog, which) -> {
                // Clear evidence files
                evidenceManager.cleanupOldEvidence();
                
                // Also manually delete all evidence directories
                clearEvidenceDirectories();
                
                updateUI();
                Toast.makeText(this, "All evidence cleared", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void clearEvidenceDirectories() {
        try {
            // Clear photos
            java.io.File photosDir = new java.io.File(getExternalFilesDir(null), "security_photos");
            if (photosDir.exists()) {
                deleteDirectory(photosDir);
                photosDir.mkdirs();
            }
            
            // Clear videos
            java.io.File videosDir = new java.io.File(getExternalFilesDir(null), "security_videos");
            if (videosDir.exists()) {
                deleteDirectory(videosDir);
                videosDir.mkdirs();
            }
            
            // Clear screenshots
            java.io.File screenshotsDir = new java.io.File(getExternalFilesDir(null), "security_screenshots");
            if (screenshotsDir.exists()) {
                deleteDirectory(screenshotsDir);
                screenshotsDir.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing evidence directories", e);
        }
    }

    private void deleteDirectory(java.io.File dir) {
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        dir.delete();
    }

    private void exportSettings() {
        // Create settings export string
        StringBuilder export = new StringBuilder();
        export.append("# Anti-Theft Evidence Settings Export\n");
        export.append("photo_evidence=").append(photoEvidenceEnabled).append("\n");
        export.append("video_evidence=").append(videoEvidenceEnabled).append("\n");
        export.append("screenshot_evidence=").append(screenshotEvidenceEnabled).append("\n");
        export.append("auto_email=").append(autoEmailEnabled).append("\n");
        export.append("retention_days=").append(retentionDays).append("\n");
        export.append("email_delay=").append(emailDelayMinutes).append("\n");
        export.append("disguised_notifications=").append(disguisedNotificationsEnabled).append("\n");
        
        List<String> recipients = notificationManager.getEmailRecipients();
        if (!recipients.isEmpty()) {
            export.append("email_recipients=");
            for (int i = 0; i < recipients.size(); i++) {
                if (i > 0) export.append(",");
                export.append(recipients.get(i));
            }
            export.append("\n");
        }
        
        // Show export dialog
        new AlertDialog.Builder(this)
            .setTitle("Export Settings")
            .setMessage("Settings exported to clipboard. You can paste and save these settings.")
            .setPositiveButton("Copy to Clipboard", (dialog, which) -> {
                android.content.ClipboardManager clipboard = 
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Evidence Settings", export.toString());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Settings copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void importSettings() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Paste exported settings here...");
        
        new AlertDialog.Builder(this)
            .setTitle("Import Settings")
            .setMessage("Paste your exported settings:")
            .setView(input)
            .setPositiveButton("Import", (dialog, which) -> {
                String settingsText = input.getText().toString();
                if (parseAndApplySettings(settingsText)) {
                    Toast.makeText(this, "Settings imported successfully", Toast.LENGTH_SHORT).show();
                    updateUI();
                } else {
                    Toast.makeText(this, "Invalid settings format", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private boolean parseAndApplySettings(String settingsText) {
        try {
            String[] lines = settingsText.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                switch (key) {
                    case "photo_evidence":
                        photoEvidenceEnabled = Boolean.parseBoolean(value);
                        break;
                    case "video_evidence":
                        videoEvidenceEnabled = Boolean.parseBoolean(value);
                        break;
                    case "screenshot_evidence":
                        screenshotEvidenceEnabled = Boolean.parseBoolean(value);
                        break;
                    case "auto_email":
                        autoEmailEnabled = Boolean.parseBoolean(value);
                        break;
                    case "retention_days":
                        retentionDays = Math.max(1, Integer.parseInt(value));
                        break;
                    case "email_delay":
                        emailDelayMinutes = Math.max(0, Integer.parseInt(value));
                        break;
                    case "disguised_notifications":
                        disguisedNotificationsEnabled = Boolean.parseBoolean(value);
                        break;
                    case "email_recipients":
                        editEmailRecipients.setText(value);
                        break;
                }
            }
            
            saveSettings();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing settings", e);
            return false;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
        if (notificationManager != null) {
            notificationManager.cleanup();
        }
    }
}

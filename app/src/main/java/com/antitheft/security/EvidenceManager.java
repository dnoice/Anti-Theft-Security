// File: app/src/main/java/com/antitheft/security/EvidenceManager.java

package com.antitheft.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EvidenceManager {
    private static final String TAG = "AntiTheft_Evidence";
    private static final String PREFS_NAME = "EvidencePrefs";
    
    private Context context;
    private SharedPreferences preferences;
    private ExecutorService evidenceExecutor;
    
    // Evidence capture managers
    private MultiplePhotoCaptureManager photoCaptureManager;
    private VideoCaptureManager videoCaptureManager;
    private ScreenshotUtility screenshotUtility;
    private SmartNotificationManager notificationManager;
    
    // Evidence settings
    private boolean photoEvidenceEnabled = true;
    private boolean videoEvidenceEnabled = false;
    private boolean screenshotEvidenceEnabled = true;
    private boolean autoEmailEnabled = false;
    private int maxStorageGB = 2;
    private int evidenceRetentionDays = 30;
    
    public EvidenceManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.evidenceExecutor = Executors.newSingleThreadExecutor();
        
        initializeManagers();
        loadSettings();
        
        Log.i(TAG, "EvidenceManager initialized");
    }
    
    private void initializeManagers() {
        photoCaptureManager = new MultiplePhotoCaptureManager(context);
        videoCaptureManager = new VideoCaptureManager(context);
        screenshotUtility = new ScreenshotUtility(context);
        notificationManager = new SmartNotificationManager(context);
    }
    
    // Main evidence capture method
    public void captureSecurityEvidence(String triggerReason) {
        Log.i(TAG, "Starting evidence capture - Reason: " + triggerReason);
        
        evidenceExecutor.execute(() -> {
            List<String> evidencePaths = new ArrayList<>();
            
            try {
                // Create evidence session
                EvidenceSession session = createEvidenceSession(triggerReason);
                
                // Capture photos if enabled
                if (photoEvidenceEnabled && photoCaptureManager.hasPermissions()) {
                    List<String> photoPaths = capturePhotos(session);
                    evidencePaths.addAll(photoPaths);
                }
                
                // Capture video if enabled
                if (videoEvidenceEnabled && videoCaptureManager.hasPermissions()) {
                    List<String> videoPaths = captureVideo(session);
                    evidencePaths.addAll(videoPaths);
                }
                
                // Capture screenshot if enabled
                if (screenshotEvidenceEnabled) {
                    String screenshotPath = captureScreenshot(session);
                    if (screenshotPath != null) {
                        evidencePaths.add(screenshotPath);
                    }
                }
                
                // Save evidence session
                saveEvidenceSession(session, evidencePaths);
                
                // Send notifications
                sendEvidenceNotification(session, evidencePaths);
                
                Log.i(TAG, "Evidence capture completed - " + evidencePaths.size() + " files collected");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during evidence capture", e);
            }
        });
    }
    
    private EvidenceSession createEvidenceSession(String triggerReason) {
        EvidenceSession session = new EvidenceSession();
        session.sessionId = generateSessionId();
        session.timestamp = System.currentTimeMillis();
        session.triggerReason = triggerReason;
        session.deviceInfo = getDeviceInfo();
        
        return session;
    }
    
    private List<String> capturePhotos(EvidenceSession session) {
        List<String> photoPaths = new ArrayList<>();
        
        try {
            Log.d(TAG, "Capturing photos for session: " + session.sessionId);
            
            final Object lock = new Object();
            final boolean[] completed = {false};
            
            photoCaptureManager.startMultiplePhotoCapture(new MultiplePhotoCaptureManager.MultiplePhotoCaptureCallback() {
                @Override
                public void onPhotosProgress(int frontCount, int backCount, int totalRemaining) {
                    Log.d(TAG, "Photo progress - Front: " + frontCount + ", Back: " + backCount + ", Remaining: " + totalRemaining);
                }
                
                @Override
                public void onAllPhotosCompleted(List<String> frontPhotos, List<String> backPhotos) {
                    photoPaths.addAll(frontPhotos);
                    photoPaths.addAll(backPhotos);
                    
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notify();
                    }
                }
                
                @Override
                public void onPhotoCaptureError(String error) {
                    Log.e(TAG, "Photo capture error: " + error);
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notify();
                    }
                }
            });
            
            // Wait for photo capture to complete (with timeout)
            synchronized (lock) {
                if (!completed[0]) {
                    lock.wait(30000); // 30 second timeout
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing photos", e);
        }
        
        return photoPaths;
    }
    
    private List<String> captureVideo(EvidenceSession session) {
        List<String> videoPaths = new ArrayList<>();
        
        try {
            Log.d(TAG, "Capturing video for session: " + session.sessionId);
            
            final Object lock = new Object();
            final boolean[] completed = {false};
            
            videoCaptureManager.startSecretVideoRecording(new VideoCaptureManager.VideoCaptureCallback() {
                @Override
                public void onVideosRecorded(String frontVideoPath, String backVideoPath) {
                    if (frontVideoPath != null) videoPaths.add(frontVideoPath);
                    if (backVideoPath != null) videoPaths.add(backVideoPath);
                    
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notify();
                    }
                }
                
                @Override
                public void onVideoError(String error) {
                    Log.e(TAG, "Video capture error: " + error);
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notify();
                    }
                }
                
                @Override
                public void onRecordingProgress(int secondsRemaining) {
                    Log.d(TAG, "Video recording progress: " + secondsRemaining + "s remaining");
                }
            });
            
            // Wait for video capture to complete (with timeout)
            synchronized (lock) {
                if (!completed[0]) {
                    lock.wait(15000); // 15 second timeout
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing video", e);
        }
        
        return videoPaths;
    }
    
    private String captureScreenshot(EvidenceSession session) {
        try {
            Log.d(TAG, "Capturing screenshot for session: " + session.sessionId);
            return screenshotUtility.captureScreenshot("evidence_" + session.sessionId);
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screenshot", e);
            return null;
        }
    }
    
    private void saveEvidenceSession(EvidenceSession session, List<String> evidencePaths) {
        // Save session metadata
        String sessionKey = "session_" + session.sessionId;
        StringBuilder pathsBuilder = new StringBuilder();
        for (int i = 0; i < evidencePaths.size(); i++) {
            if (i > 0) pathsBuilder.append("|");
            pathsBuilder.append(evidencePaths.get(i));
        }
        
        preferences.edit()
            .putString(sessionKey + "_reason", session.triggerReason)
            .putLong(sessionKey + "_timestamp", session.timestamp)
            .putString(sessionKey + "_paths", pathsBuilder.toString())
            .putString(sessionKey + "_device", session.deviceInfo)
            .putInt("total_evidence_sessions", getTotalEvidenceSessions() + 1)
            .apply();
        
        Log.d(TAG, "Evidence session saved: " + session.sessionId);
    }
    
    private void sendEvidenceNotification(EvidenceSession session, List<String> evidencePaths) {
        String title = "Security Evidence Collected";
        String message = String.format("Evidence captured for: %s\n%d files collected", 
            session.triggerReason, evidencePaths.size());
        
        notificationManager.queueSecurityNotification(title, message, evidencePaths, true);
        
        // Send email if enabled
        if (autoEmailEnabled) {
            // Email will be sent automatically by the notification manager
            Log.d(TAG, "Email notification queued");
        }
    }
    
    // Evidence retrieval methods
    public List<EvidenceSession> getAllEvidenceSessions() {
        List<EvidenceSession> sessions = new ArrayList<>();
        
        for (String key : preferences.getAll().keySet()) {
            if (key.endsWith("_reason")) {
                String sessionId = key.substring(8, key.length() - 7); // Remove "session_" and "_reason"
                EvidenceSession session = loadEvidenceSession(sessionId);
                if (session != null) {
                    sessions.add(session);
                }
            }
        }
        
        // Sort by timestamp (newest first)
        sessions.sort((s1, s2) -> Long.compare(s2.timestamp, s1.timestamp));
        
        return sessions;
    }
    
    private EvidenceSession loadEvidenceSession(String sessionId) {
        String sessionKey = "session_" + sessionId;
        
        if (!preferences.contains(sessionKey + "_reason")) {
            return null;
        }
        
        EvidenceSession session = new EvidenceSession();
        session.sessionId = sessionId;
        session.triggerReason = preferences.getString(sessionKey + "_reason", "");
        session.timestamp = preferences.getLong(sessionKey + "_timestamp", 0);
        session.deviceInfo = preferences.getString(sessionKey + "_device", "");
        
        String pathsStr = preferences.getString(sessionKey + "_paths", "");
        if (!pathsStr.isEmpty()) {
            session.evidencePaths = Arrays.asList(pathsStr.split("\\|"));
        } else {
            session.evidencePaths = new ArrayList<>();
        }
        
        return session;
    }
    
    public List<String> getAllEvidenceFiles() {
        List<String> allFiles = new ArrayList<>();
        
        // Get files from photos directory
        File photosDir = new File(context.getExternalFilesDir(null), "security_photos");
        if (photosDir.exists()) {
            File[] photoFiles = photosDir.listFiles();
            if (photoFiles != null) {
                for (File file : photoFiles) {
                    allFiles.add(file.getAbsolutePath());
                }
            }
        }
        
        // Get files from videos directory
        File videosDir = new File(context.getExternalFilesDir(null), "security_videos");
        if (videosDir.exists()) {
            File[] videoFiles = videosDir.listFiles();
            if (videoFiles != null) {
                for (File file : videoFiles) {
                    allFiles.add(file.getAbsolutePath());
                }
            }
        }
        
        // Get files from screenshots directory
        File screenshotsDir = new File(context.getExternalFilesDir(null), "security_screenshots");
        if (screenshotsDir.exists()) {
            File[] screenshotFiles = screenshotsDir.listFiles();
            if (screenshotFiles != null) {
                for (File file : screenshotFiles) {
                    allFiles.add(file.getAbsolutePath());
                }
            }
        }
        
        return allFiles;
    }
    
    // Evidence maintenance
    public void cleanupOldEvidence() {
        evidenceExecutor.execute(() -> {
            Log.i(TAG, "Starting evidence cleanup");
            
            long cutoffTime = System.currentTimeMillis() - (evidenceRetentionDays * 24L * 60 * 60 * 1000);
            int deletedFiles = 0;
            
            // Clean up old evidence files
            List<String> allFiles = getAllEvidenceFiles();
            for (String filePath : allFiles) {
                File file = new File(filePath);
                if (file.exists() && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedFiles++;
                    }
                }
            }
            
            // Clean up old session records
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (key.contains("_timestamp")) {
                    long timestamp = preferences.getLong(key, 0);
                    if (timestamp < cutoffTime) {
                        String sessionId = key.substring(8, key.length() - 10); // Extract session ID
                        deleteSessionRecord(editor, sessionId);
                    }
                }
            }
            editor.apply();
            
            Log.i(TAG, "Evidence cleanup completed - " + deletedFiles + " files deleted");
        });
    }
    
    private void deleteSessionRecord(SharedPreferences.Editor editor, String sessionId) {
        String sessionKey = "session_" + sessionId;
        editor.remove(sessionKey + "_reason");
        editor.remove(sessionKey + "_timestamp");
        editor.remove(sessionKey + "_paths");
        editor.remove(sessionKey + "_device");
    }
    
    public void deleteEvidenceSession(String sessionId) {
        EvidenceSession session = loadEvidenceSession(sessionId);
        if (session != null) {
            // Delete associated files
            for (String filePath : session.evidencePaths) {
                File file = new File(filePath);
                if (file.exists()) {
                    file.delete();
                }
            }
            
            // Delete session record
            SharedPreferences.Editor editor = preferences.edit();
            deleteSessionRecord(editor, sessionId);
            editor.apply();
            
            Log.i(TAG, "Evidence session deleted: " + sessionId);
        }
    }
    
    // Statistics
    public int getEvidenceCount() {
        return getAllEvidenceFiles().size();
    }
    
    public int getTotalEvidenceSessions() {
        return preferences.getInt("total_evidence_sessions", 0);
    }
    
    public long getTotalEvidenceSize() {
        long totalSize = 0;
        for (String filePath : getAllEvidenceFiles()) {
            File file = new File(filePath);
            if (file.exists()) {
                totalSize += file.length();
            }
        }
        return totalSize;
    }
    
    // Settings
    public boolean isPhotoEvidenceEnabled() {
        return photoEvidenceEnabled;
    }
    
    public void setPhotoEvidenceEnabled(boolean enabled) {
        this.photoEvidenceEnabled = enabled;
    }
    
    public boolean isVideoEvidenceEnabled() {
        return videoEvidenceEnabled;
    }
    
    public void setVideoEvidenceEnabled(boolean enabled) {
        this.videoEvidenceEnabled = enabled;
    }
    
    public boolean isScreenshotEvidenceEnabled() {
        return screenshotEvidenceEnabled;
    }
    
    public void setScreenshotEvidenceEnabled(boolean enabled) {
        this.screenshotEvidenceEnabled = enabled;
    }
    
    public boolean isAutoEmailEnabled() {
        return autoEmailEnabled;
    }
    
    public void setAutoEmailEnabled(boolean enabled) {
        this.autoEmailEnabled = enabled;
    }
    
    public int getEvidenceRetentionDays() {
        return evidenceRetentionDays;
    }
    
    public void setEvidenceRetentionDays(int days) {
        this.evidenceRetentionDays = Math.max(1, days);
    }
    
    // Persistence
    public void saveSettings() {
        preferences.edit()
            .putBoolean("photo_evidence", photoEvidenceEnabled)
            .putBoolean("video_evidence", videoEvidenceEnabled)
            .putBoolean("screenshot_evidence", screenshotEvidenceEnabled)
            .putBoolean("auto_email", autoEmailEnabled)
            .putInt("retention_days", evidenceRetentionDays)
            .putInt("max_storage_gb", maxStorageGB)
            .apply();
        
        Log.d(TAG, "Evidence settings saved");
    }
    
    public void loadSettings() {
        photoEvidenceEnabled = preferences.getBoolean("photo_evidence", true);
        videoEvidenceEnabled = preferences.getBoolean("video_evidence", false);
        screenshotEvidenceEnabled = preferences.getBoolean("screenshot_evidence", true);
        autoEmailEnabled = preferences.getBoolean("auto_email", false);
        evidenceRetentionDays = preferences.getInt("retention_days", 30);
        maxStorageGB = preferences.getInt("max_storage_gb", 2);
        
        Log.d(TAG, "Evidence settings loaded");
    }
    
    // Utility methods
    private String generateSessionId() {
        return "EVD_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    private String getDeviceInfo() {
        return String.format("Model: %s, Android: %s, App: %s", 
            android.os.Build.MODEL,
            android.os.Build.VERSION.RELEASE,
            "Anti-Theft Security v1.0"
        );
    }
    
    public void cleanup() {
        if (evidenceExecutor != null && !evidenceExecutor.isShutdown()) {
            evidenceExecutor.shutdown();
        }
        
        if (photoCaptureManager != null) {
            photoCaptureManager.cleanupResources();
        }
        
        if (videoCaptureManager != null) {
            videoCaptureManager.cleanup();
        }
        
        if (notificationManager != null) {
            notificationManager.cleanup();
        }
        
        Log.d(TAG, "EvidenceManager cleanup completed");
    }
    
    // Evidence session data class
    public static class EvidenceSession {
        public String sessionId;
        public long timestamp;
        public String triggerReason;
        public String deviceInfo;
        public List<String> evidencePaths = new ArrayList<>();
        
        public String getFormattedTimestamp() {
            return new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
        }
        
        public int getFileCount() {
            return evidencePaths.size();
        }
        
        public String getFileTypes() {
            int photos = 0, videos = 0, screenshots = 0;
            
            for (String path : evidencePaths) {
                String lower = path.toLowerCase();
                if (lower.contains("photo") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    photos++;
                } else if (lower.contains("video") || lower.endsWith(".mp4")) {
                    videos++;
                } else if (lower.contains("screenshot") || lower.contains("screen")) {
                    screenshots++;
                }
            }
            
            StringBuilder types = new StringBuilder();
            if (photos > 0) types.append(photos).append(" photos");
            if (videos > 0) {
                if (types.length() > 0) types.append(", ");
                types.append(videos).append(" videos");
            }
            if (screenshots > 0) {
                if (types.length() > 0) types.append(", ");
                types.append(screenshots).append(" screenshots");
            }
            
            return types.toString();
        }
    }
}

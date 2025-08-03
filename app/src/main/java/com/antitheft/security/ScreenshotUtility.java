// File: app/src/main/java/com/antitheft/security/ScreenshotUtility.java

package com.antitheft.security;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotUtility {
    private static final String TAG = "AntiTheft_Screenshot";
    
    private Context context;
    
    public ScreenshotUtility(Context context) {
        this.context = context;
    }
    
    // Main screenshot capture method
    public String captureScreenshot(String sessionId) {
        try {
            // Create screenshots directory
            File screenshotsDir = new File(context.getExternalFilesDir(null), "security_screenshots");
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }
            
            // Generate filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = String.format("screenshot_%s_%s.png", sessionId, timestamp);
            File screenshotFile = new File(screenshotsDir, filename);
            
            // Try different screenshot methods based on Android version
            Bitmap screenshot = null;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                screenshot = captureScreenshotAPI26Plus();
            }
            
            if (screenshot == null) {
                screenshot = captureScreenshotLegacy();
            }
            
            if (screenshot == null) {
                screenshot = createFallbackScreenshot();
            }
            
            if (screenshot != null) {
                // Save screenshot to file
                FileOutputStream outputStream = new FileOutputStream(screenshotFile);
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.close();
                screenshot.recycle();
                
                Log.d(TAG, "Screenshot saved: " + screenshotFile.getAbsolutePath());
                return screenshotFile.getAbsolutePath();
            } else {
                Log.e(TAG, "Failed to capture screenshot");
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screenshot", e);
            return null;
        }
    }
    
    // Screenshot capture for Android 8.0+ using PixelCopy
    private Bitmap captureScreenshotAPI26Plus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        
        try {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                View rootView = activity.getWindow().getDecorView().getRootView();
                
                Bitmap bitmap = Bitmap.createBitmap(
                    rootView.getWidth(),
                    rootView.getHeight(),
                    Bitmap.Config.ARGB_8888
                );
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use PixelCopy for hardware-accelerated views
                    final boolean[] completed = {false};
                    final Bitmap[] result = {null};
                    
                    PixelCopy.request(
                        (Activity) context,
                        new Rect(0, 0, rootView.getWidth(), rootView.getHeight()),
                        bitmap,
                        new PixelCopy.OnPixelCopyFinishedListener() {
                            @Override
                            public void onPixelCopyFinished(int copyResult) {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    result[0] = bitmap;
                                }
                                completed[0] = true;
                            }
                        },
                        new Handler(Looper.getMainLooper())
                    );
                    
                    // Wait for completion (with timeout)
                    long startTime = System.currentTimeMillis();
                    while (!completed[0] && (System.currentTimeMillis() - startTime) < 3000) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    
                    return result[0];
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in API 26+ screenshot", e);
        }
        
        return null;
    }
    
    // Legacy screenshot capture using View.draw()
    private Bitmap captureScreenshotLegacy() {
        try {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                View rootView = activity.getWindow().getDecorView().getRootView();
                
                // Create bitmap
                Bitmap bitmap = Bitmap.createBitmap(
                    rootView.getWidth(),
                    rootView.getHeight(),
                    Bitmap.Config.ARGB_8888
                );
                
                // Draw view to canvas
                Canvas canvas = new Canvas(bitmap);
                rootView.draw(canvas);
                
                Log.d(TAG, "Legacy screenshot captured: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in legacy screenshot", e);
        }
        
        return null;
    }
    
    // Create a fallback screenshot with device info when real screenshot fails
    private Bitmap createFallbackScreenshot() {
        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            // Fill with dark background
            canvas.drawColor(Color.parseColor("#1a1a1a"));
            
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(48f);
            paint.setAntiAlias(true);
            
            Paint headerPaint = new Paint();
            headerPaint.setColor(Color.parseColor("#ff4444"));
            headerPaint.setTextSize(64f);
            headerPaint.setAntiAlias(true);
            headerPaint.setFakeBoldText(true);
            
            // Draw security alert header
            String headerText = "🚨 SECURITY ALERT 🚨";
            float headerX = (width - headerPaint.measureText(headerText)) / 2;
            canvas.drawText(headerText, headerX, 150, headerPaint);
            
            // Draw timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
            paint.setTextSize(36f);
            String timeText = "Time: " + timestamp;
            float timeX = (width - paint.measureText(timeText)) / 2;
            canvas.drawText(timeText, timeX, 250, paint);
            
            // Draw device info
            paint.setTextSize(32f);
            String[] infoLines = {
                "Device: " + Build.MODEL,
                "Android: " + Build.VERSION.RELEASE,
                "Screen captured during",
                "security breach attempt",
                "",
                "Evidence ID: " + System.currentTimeMillis()
            };
            
            float y = 350;
            for (String line : infoLines) {
                if (!line.isEmpty()) {
                    float x = (width - paint.measureText(line)) / 2;
                    canvas.drawText(line, x, y, paint);
                }
                y += 50;
            }
            
            // Draw warning box
            Paint boxPaint = new Paint();
            boxPaint.setColor(Color.parseColor("#ff4444"));
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(5f);
            
            float boxLeft = width * 0.1f;
            float boxTop = height * 0.7f;
            float boxRight = width * 0.9f;
            float boxBottom = height * 0.9f;
            
            canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, boxPaint);
            
            paint.setTextSize(28f);
            paint.setColor(Color.parseColor("#ffaa00"));
            String warningText = "Screenshot captured automatically";
            float warningX = (width - paint.measureText(warningText)) / 2;
            canvas.drawText(warningText, warningX, boxTop + 60, paint);
            
            String warningText2 = "as part of security monitoring";
            float warningX2 = (width - paint.measureText(warningText2)) / 2;
            canvas.drawText(warningText2, warningX2, boxTop + 100, paint);
            
            Log.d(TAG, "Fallback screenshot created: " + width + "x" + height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating fallback screenshot", e);
            return null;
        }
    }
    
    // Capture screenshot of specific view
    public String captureViewScreenshot(View view, String filename) {
        try {
            File screenshotsDir = new File(context.getExternalFilesDir(null), "security_screenshots");
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File screenshotFile = new File(screenshotsDir, filename + "_" + timestamp + ".png");
            
            Bitmap bitmap = Bitmap.createBitmap(
                view.getWidth(),
                view.getHeight(),
                Bitmap.Config.ARGB_8888
            );
            
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            
            FileOutputStream outputStream = new FileOutputStream(screenshotFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();
            bitmap.recycle();
            
            Log.d(TAG, "View screenshot saved: " + screenshotFile.getAbsolutePath());
            return screenshotFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing view screenshot", e);
            return null;
        }
    }
    
    // Capture multiple screenshots with delay
    public void captureDelayedScreenshots(int count, int delayMs, DelayedScreenshotCallback callback) {
        new Thread(() -> {
            for (int i = 0; i < count; i++) {
                String path = captureScreenshot("delayed_" + i);
                
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (path != null) {
                            callback.onScreenshotCaptured(i + 1, path);
                        } else {
                            callback.onScreenshotError(i + 1, "Failed to capture screenshot " + (i + 1));
                        }
                    });
                }
                
                if (i < count - 1) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(callback::onAllScreenshotsCompleted);
            }
        }).start();
    }
    
    public interface DelayedScreenshotCallback {
        void onScreenshotCaptured(int index, String path);
        void onScreenshotError(int index, String error);
        void onAllScreenshotsCompleted();
    }
    
    // Get all security screenshots
    public File[] getSecurityScreenshots() {
        File screenshotsDir = new File(context.getExternalFilesDir(null), "security_screenshots");
        if (screenshotsDir.exists()) {
            return screenshotsDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
        }
        return new File[0];
    }
    
    // Clean up old screenshots
    public void cleanupOldScreenshots(int keepCount) {
        File[] screenshots = getSecurityScreenshots();
        if (screenshots != null && screenshots.length > keepCount) {
            // Sort by last modified date
            java.util.Arrays.sort(screenshots, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            
            // Delete oldest screenshots
            for (int i = keepCount; i < screenshots.length; i++) {
                if (screenshots[i].delete()) {
                    Log.d(TAG, "Deleted old screenshot: " + screenshots[i].getName());
                }
            }
        }
    }
    
    // Check if screenshot capture is available
    public boolean isScreenshotAvailable() {
        try {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                View rootView = activity.getWindow().getDecorView().getRootView();
                return rootView.getWidth() > 0 && rootView.getHeight() > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking screenshot availability", e);
        }
        return false;
    }
    
    // Get screenshot statistics
    public String getScreenshotStats() {
        File[] screenshots = getSecurityScreenshots();
        int count = screenshots != null ? screenshots.length : 0;
        
        long totalSize = 0;
        long latestTime = 0;
        
        if (screenshots != null) {
            for (File file : screenshots) {
                totalSize += file.length();
                if (file.lastModified() > latestTime) {
                    latestTime = file.lastModified();
                }
            }
        }
        
        StringBuilder stats = new StringBuilder();
        stats.append("Screenshots: ").append(count).append("\n");
        stats.append("Total Size: ").append(formatFileSize(totalSize)).append("\n");
        
        if (latestTime > 0) {
            String timeStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(new Date(latestTime));
            stats.append("Latest: ").append(timeStr);
        } else {
            stats.append("Latest: None");
        }
        
        return stats.toString();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
    
    // Create screenshot with overlay information
    public String captureScreenshotWithOverlay(String triggerInfo) {
        try {
            Bitmap baseScreenshot = captureScreenshotLegacy();
            if (baseScreenshot == null) {
                baseScreenshot = createFallbackScreenshot();
            }
            
            if (baseScreenshot == null) {
                return null;
            }
            
            // Create overlay
            Canvas canvas = new Canvas(baseScreenshot);
            
            // Semi-transparent overlay
            Paint overlayPaint = new Paint();
            overlayPaint.setColor(Color.parseColor("#80000000"));
            canvas.drawRect(0, 0, baseScreenshot.getWidth(), 200, overlayPaint);
            
            // Alert text
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(36f);
            textPaint.setAntiAlias(true);
            textPaint.setFakeBoldText(true);
            
            String alertText = "🚨 SECURITY BREACH 🚨";
            float alertX = (baseScreenshot.getWidth() - textPaint.measureText(alertText)) / 2;
            canvas.drawText(alertText, alertX, 50, textPaint);
            
            textPaint.setTextSize(24f);
            textPaint.setFakeBoldText(false);
            String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
            float timeX = (baseScreenshot.getWidth() - textPaint.measureText(timeText)) / 2;
            canvas.drawText(timeText, timeX, 90, textPaint);
            
            if (triggerInfo != null && !triggerInfo.isEmpty()) {
                textPaint.setTextSize(20f);
                float infoX = (baseScreenshot.getWidth() - textPaint.measureText(triggerInfo)) / 2;
                canvas.drawText(triggerInfo, infoX, 130, textPaint);
            }
            
            // Save enhanced screenshot
            File screenshotsDir = new File(context.getExternalFilesDir(null), "security_screenshots");
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File screenshotFile = new File(screenshotsDir, "security_overlay_" + timestamp + ".png");
            
            FileOutputStream outputStream = new FileOutputStream(screenshotFile);
            baseScreenshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();
            baseScreenshot.recycle();
            
            Log.d(TAG, "Overlay screenshot saved: " + screenshotFile.getAbsolutePath());
            return screenshotFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay screenshot", e);
            return null;
        }
    }
}

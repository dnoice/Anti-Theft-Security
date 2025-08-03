// File: app/src/main/java/com/antitheft/security/FakeHomeScreenActivity.java

package com.antitheft.security;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

public class FakeHomeScreenActivity extends Activity {
    private static final String TAG = "AntiTheft_FakeHome";
    
    private EvidenceManager evidenceManager;
    private MultiplePhotoCaptureManager photoCaptureManager;
    private SmartNotificationManager notificationManager;
    
    private GridLayout appsGrid;
    private TextView timeText, dateText;
    private boolean isCapturingEvidence = false;
    
    // Fake app data
    private final String[] fakeAppNames = {
        "Phone", "Messages", "Contacts", "Chrome",
        "Gmail", "Maps", "YouTube", "Play Store",
        "Camera", "Photos", "Settings", "Calculator"
    };
    
    private final int[] fakeAppIcons = {
        android.R.drawable.ic_menu_call,
        android.R.drawable.ic_dialog_email,
        android.R.drawable.ic_menu_my_calendar,
        android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_dialog_email,
        android.R.drawable.ic_menu_mapmode,
        android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_add,
        android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_gallery,
        android.R.drawable.ic_menu_preferences,
        android.R.drawable.ic_menu_edit
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make it look like real home screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                           WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_fake_home_screen);
        
        initializeManagers();
        initializeViews();
        setupFakeApps();
        updateDateTime();
        
        Log.i(TAG, "Fake home screen activated - capturing evidence on app clicks");
    }

    private void initializeManagers() {
        evidenceManager = new EvidenceManager(this);
        photoCaptureManager = new MultiplePhotoCaptureManager(this);
        notificationManager = new SmartNotificationManager(this);
    }

    private void initializeViews() {
        appsGrid = findViewById(R.id.appsGrid);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
    }

    private void setupFakeApps() {
        appsGrid.removeAllViews();
        
        for (int i = 0; i < fakeAppNames.length; i++) {
            View appView = createFakeAppIcon(fakeAppNames[i], fakeAppIcons[i], i);
            appsGrid.addView(appView);
        }
    }

    private View createFakeAppIcon(String appName, int iconRes, int position) {
        View appView = getLayoutInflater().inflate(R.layout.fake_app_icon, appsGrid, false);
        
        ImageView iconView = appView.findViewById(R.id.appIcon);
        TextView nameView = appView.findViewById(R.id.appName);
        
        iconView.setImageResource(iconRes);
        nameView.setText(appName);
        
        // Set click listener to capture evidence
        appView.setOnClickListener(v -> onFakeAppClicked(appName, position));
        
        return appView;
    }

    private void onFakeAppClicked(String appName, int position) {
        Log.i(TAG, "Intruder clicked fake app: " + appName);
        
        if (!isCapturingEvidence) {
            captureEvidenceOnAppClick(appName);
        }
        
        // Show fake app loading or error
        showFakeAppResponse(appName);
    }

    private void captureEvidenceOnAppClick(String appName) {
        isCapturingEvidence = true;
        
        // Capture multiple photos
        photoCaptureManager.startMultiplePhotoCapture(new MultiplePhotoCaptureManager.MultiplePhotoCaptureCallback() {
            @Override
            public void onPhotosProgress(int frontCount, int backCount, int totalRemaining) {
                Log.d(TAG, "Evidence capture progress: " + (frontCount + backCount) + " photos taken");
            }
            
            @Override
            public void onAllPhotosCompleted(java.util.List<String> frontPhotos, java.util.List<String> backPhotos) {
                Log.i(TAG, "Evidence captured - Front: " + frontPhotos.size() + ", Back: " + backPhotos.size());
                
                // Combine all evidence paths
                java.util.List<String> allEvidencePaths = new java.util.ArrayList<>();
                allEvidencePaths.addAll(frontPhotos);
                allEvidencePaths.addAll(backPhotos);
                
                // Send delayed notification
                String title = "Unauthorized Device Access Detected";
                String message = "Someone attempted to access " + appName + " on your device. " +
                               "Evidence has been collected automatically.";
                
                notificationManager.queueSecurityNotification(title, message, allEvidencePaths, true);
                
                isCapturingEvidence = false;
            }
            
            @Override
            public void onPhotoCaptureError(String error) {
                Log.e(TAG, "Evidence capture failed: " + error);
                isCapturingEvidence = false;
            }
        });
    }

    private void showFakeAppResponse(String appName) {
        // Show different responses based on app type
        String[] loadingMessages = {
            "Loading " + appName + "...",
            "Starting " + appName + "...",
            "Opening " + appName + "..."
        };
        
        String[] errorMessages = {
            appName + " is not responding",
            "Cannot connect to " + appName + " servers",
            appName + " needs to be updated",
            "Insufficient storage to run " + appName,
            "Network error - please try again"
        };
        
        // Show loading message first
        String loadingMsg = loadingMessages[(int) (Math.random() * loadingMessages.length)];
        Toast.makeText(this, loadingMsg, Toast.LENGTH_SHORT).show();
        
        // Then show error after delay
        new Handler().postDelayed(() -> {
            String errorMsg = errorMessages[(int) (Math.random() * errorMessages.length)];
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }, 2000);
    }

    private void updateDateTime() {
        Handler timeHandler = new Handler();
        Runnable timeRunnable = new Runnable() {
            @Override
            public void run() {
                java.util.Date now = new java.util.Date();
                
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.getDefault());
                
                timeText.setText(timeFormat.format(now));
                dateText.setText(dateFormat.format(now));
                
                timeHandler.postDelayed(this, 60000); // Update every minute
            }
        };
        timeHandler.post(timeRunnable);
    }

    @Override
    public void onBackPressed() {
        // Simulate back button behavior - show recent apps or do nothing
        Toast.makeText(this, "No recent apps", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't let the intruder leave the fake home screen easily
        if (!isFinishing()) {
            // Restart this activity to keep intruder trapped
            Intent restart = new Intent(this, FakeHomeScreenActivity.class);
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(restart);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (photoCaptureManager != null) {
            photoCaptureManager.cleanupResources();
        }
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
        if (notificationManager != null) {
            notificationManager.cleanup();
        }
    }

    // Handle volume buttons to potentially exit fake home screen (secret gesture)
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && 
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Secret combination to exit fake home screen
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Static method to activate fake home screen
    public static void activateFakeHomeScreen(android.content.Context context) {
        Intent intent = new Intent(context, FakeHomeScreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        Log.i(TAG, "Fake home screen activated");
    }
}

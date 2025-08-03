// File: app/src/main/java/com/antitheft/security/MainActivity.java

package com.antitheft.security;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final String TAG = "AntiTheft_Main";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // Core managers
    private SecurityManager securityManager;
    private SensorService sensorService;
    private EvidenceManager evidenceManager;
    private AppDisguiseManager disguiseManager;
    private BreakInDetectionManager breakInManager;
    
    // UI Components
    private Button btnArm, btnDisarm, btnViewEvidence, btnSettings;
    private Switch switchMotionDetection, switchCameraCapture, switchBreakInDetection;
    private SeekBar seekSensitivity;
    private Spinner spinnerAlarmSound;
    private TextView txtStatus, txtSensitivity, txtStats;
    private EditText editPin, editConfirmPin;
    
    private boolean isArmed = false;
    private boolean isFirstRun = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeManagers();
        initializeViews();
        setupEventListeners();
        checkPermissions();
        checkFirstRun();
        
        Log.i(TAG, "MainActivity created");
    }

    private void initializeManagers() {
        securityManager = new SecurityManager(this);
        sensorService = new SensorService();
        evidenceManager = new EvidenceManager(this);
        disguiseManager = new AppDisguiseManager(this);
        breakInManager = new BreakInDetectionManager(this);
    }

    private void initializeViews() {
        // Main control buttons
        btnArm = findViewById(R.id.btnArm);
        btnDisarm = findViewById(R.id.btnDisarm);
        btnViewEvidence = findViewById(R.id.btnViewEvidence);
        btnSettings = findViewById(R.id.btnSettings);
        
        // Feature switches
        switchMotionDetection = findViewById(R.id.switchMotionDetection);
        switchCameraCapture = findViewById(R.id.switchCameraCapture);
        switchBreakInDetection = findViewById(R.id.switchBreakInDetection);
        
        // Controls
        seekSensitivity = findViewById(R.id.seekSensitivity);
        spinnerAlarmSound = findViewById(R.id.spinnerAlarmSound);
        
        // Info displays
        txtStatus = findViewById(R.id.txtStatus);
        txtSensitivity = findViewById(R.id.txtSensitivity);
        txtStats = findViewById(R.id.txtStats);
        
        // PIN setup
        editPin = findViewById(R.id.editPin);
        editConfirmPin = findViewById(R.id.editConfirmPin);
        
        // Load saved settings
        loadSettings();
        updateUI();
    }

    private void setupEventListeners() {
        btnArm.setOnClickListener(v -> armSecurity());
        btnDisarm.setOnClickListener(v -> disarmSecurity());
        btnViewEvidence.setOnClickListener(v -> viewEvidence());
        btnSettings.setOnClickListener(v -> openSettings());
        
        // Feature toggles
        switchMotionDetection.setOnCheckedChangeListener((v, checked) -> {
            securityManager.setMotionDetectionEnabled(checked);
            saveSettings();
        });
        
        switchCameraCapture.setOnCheckedChangeListener((v, checked) -> {
            securityManager.setCameraEvidenceEnabled(checked);
            saveSettings();
        });
        
        switchBreakInDetection.setOnCheckedChangeListener((v, checked) -> {
            securityManager.setBreakInDetectionEnabled(checked);
            if (checked) {
                startBreakInMonitoring();
            } else {
                stopBreakInMonitoring();
            }
            saveSettings();
        });
        
        // Sensitivity control
        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateSensitivityDisplay(progress);
                    securityManager.setSensitivity(progress);
                    saveSettings();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void armSecurity() {
        if (!validatePin()) {
            showError("Please set a valid PIN first");
            return;
        }
        
        if (!hasRequiredPermissions()) {
            requestPermissions();
            return;
        }
        
        // Start security monitoring
        if (switchMotionDetection.isChecked()) {
            Intent serviceIntent = new Intent(this, SensorService.class);
            startService(serviceIntent);
        }
        
        if (switchBreakInDetection.isChecked()) {
            startBreakInMonitoring();
        }
        
        isArmed = true;
        securityManager.setArmed(true);
        updateUI();
        
        Toast.makeText(this, "Security system ARMED", Toast.LENGTH_LONG).show();
        Log.i(TAG, "Security system armed");
        
        // Optional: Show fake home screen immediately
        if (securityManager.isFakeHomeScreenEnabled()) {
            FakeHomeScreenActivity.activateFakeHomeScreen(this);
        }
    }

    private void disarmSecurity() {
        if (!securityManager.validatePin(editPin.getText().toString())) {
            showError("Invalid PIN");
            return;
        }
        
        // Stop all monitoring
        stopService(new Intent(this, SensorService.class));
        stopBreakInMonitoring();
        
        isArmed = false;
        securityManager.setArmed(false);
        updateUI();
        
        Toast.makeText(this, "Security system DISARMED", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Security system disarmed");
    }

    private void viewEvidence() {
        Intent intent = new Intent(this, EvidenceGalleryActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, EvidenceSettingsActivity.class);
        startActivity(intent);
    }

    private boolean validatePin() {
        String pin = editPin.getText().toString();
        String confirmPin = editConfirmPin.getText().toString();
        
        if (pin.length() < 4) {
            showError("PIN must be at least 4 digits");
            return false;
        }
        
        if (isFirstRun && !pin.equals(confirmPin)) {
            showError("PIN confirmation doesn't match");
            return false;
        }
        
        if (isFirstRun) {
            // Save new PIN
            securityManager.setPin(pin);
            isFirstRun = false;
        }
        
        return true;
    }

    private void startBreakInMonitoring() {
        breakInManager.startMonitoring(new BreakInDetectionManager.BreakInDetectionCallback() {
            @Override
            public void onUnlockAttemptDetected(int attemptNumber) {
                Log.w(TAG, "Unlock attempt #" + attemptNumber + " detected");
            }
            
            @Override
            public void onSuspiciousActivity(String description, int failedAttempts) {
                Log.w(TAG, "Suspicious activity: " + description);
                
                // Trigger evidence collection
                if (switchCameraCapture.isChecked()) {
                    evidenceManager.captureSecurityEvidence("Break-in attempt detected");
                }
            }
            
            @Override
            public void onSuccessfulBreakIn(long breakInTime) {
                Log.e(TAG, "SUCCESSFUL BREAK-IN DETECTED!");
                
                // Immediate evidence collection
                evidenceManager.captureSecurityEvidence("Successful break-in detected");
                
                // Activate fake home screen
                FakeHomeScreenActivity.activateFakeHomeScreen(MainActivity.this);
            }
            
            @Override
            public void onDeviceLocked() {
                Log.d(TAG, "Device locked");
            }
            
            @Override
            public void onDeviceUnlocked(boolean wasBreakIn) {
                Log.d(TAG, "Device unlocked - Break-in: " + wasBreakIn);
                runOnUiThread(() -> updateStats());
            }
        });
    }

    private void stopBreakInMonitoring() {
        breakInManager.stopMonitoring();
    }

    private void updateUI() {
        if (isArmed) {
            txtStatus.setText("🛡️ ARMED");
            txtStatus.setTextColor(getColor(R.color.status_armed));
            btnArm.setEnabled(false);
            btnDisarm.setEnabled(true);
        } else {
            txtStatus.setText("🔓 DISARMED");
            txtStatus.setTextColor(getColor(R.color.status_disarmed));
            btnArm.setEnabled(true);
            btnDisarm.setEnabled(false);
        }
        
        updateSensitivityDisplay(seekSensitivity.getProgress());
        updateStats();
    }

    private void updateSensitivityDisplay(int progress) {
        String[] levels = {"Very Low", "Low", "Medium", "High", "Very High"};
        int levelIndex = Math.min(progress / 20, levels.length - 1);
        txtSensitivity.setText("Sensitivity: " + levels[levelIndex] + " (" + progress + "%)");
    }

    private void updateStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Evidence Files: ").append(evidenceManager.getEvidenceCount()).append("\n");
        stats.append("Total Triggers: ").append(securityManager.getTotalTriggers()).append("\n");
        stats.append("Break-in Attempts: ").append(breakInManager.getTotalUnlockAttempts()).append("\n");
        stats.append("App Disguise: ").append(disguiseManager.getCurrentDisguise().getDisplayName());
        
        txtStats.setText(stats.toString());
    }

    private void loadSettings() {
        switchMotionDetection.setChecked(securityManager.isMotionDetectionEnabled());
        switchCameraCapture.setChecked(securityManager.isCameraEvidenceEnabled());
        switchBreakInDetection.setChecked(securityManager.isBreakInDetectionEnabled());
        seekSensitivity.setProgress(securityManager.getSensitivity());
        
        // Hide confirm PIN field if PIN already set
        if (securityManager.hasPin()) {
            editConfirmPin.setVisibility(View.GONE);
            isFirstRun = false;
        }
    }

    private void saveSettings() {
        securityManager.saveSettings();
    }

    private void checkFirstRun() {
        if (!securityManager.hasPin()) {
            showToast("Welcome! Please set a PIN to secure your device.");
        }
    }

    private void checkPermissions() {
        if (!hasRequiredPermissions()) {
            showToast("Some permissions are required for full functionality");
        }
    }

    private boolean hasRequiredPermissions() {
        String[] requiredPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        };
        
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK
        };
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                showToast("All permissions granted!");
            } else {
                showError("Some features may not work without required permissions");
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup managers
        if (breakInManager != null) {
            breakInManager.stopMonitoring();
        }
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
    }

    @Override
    public void onBackPressed() {
        if (isArmed) {
            // Don't allow back press when armed
            showToast("Please disarm the security system first");
            return;
        }
        super.onBackPressed();
    }
}

// File: app/src/main/java/com/antitheft/security/AlarmActivity.java

package com.antitheft.security;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {
    private static final String TAG = "AntiTheft_AlarmActivity";
    
    // Core managers
    private SecurityManager securityManager;
    private AlarmManager alarmManager;
    private EvidenceManager evidenceManager;
    
    // UI Components
    private TextView txtAlarmMessage, txtTriggerType, txtTriggerTime, txtTriggerDetails;
    private TextView txtAlarmDuration, txtEvidenceStatus, txtPinWarning;
    private EditText editAlarmPin;
    private Button btnDisarmAlarm, btnSnoozeAlarm, btnViewEvidence, btnEmergencyCall, btnCancelEmergency;
    private ImageView iconWarning;
    private View flashOverlay;
    private LinearLayout emergencyOverlay;
    
    // Alarm state
    private boolean isAlarmActive = true;
    private boolean isSnoozing = false;
    private boolean isEmergencyMode = false;
    private int pinAttempts = 0;
    private static final int MAX_PIN_ATTEMPTS = 5;
    private long alarmStartTime;
    private String triggerType = "Unknown";
    private String triggerDetails = "";
    
    // Animation and effects
    private ObjectAnimator flashAnimator;
    private ObjectAnimator pulseAnimator;
    private Handler uiUpdateHandler = new Handler();
    private Runnable uiUpdateRunnable;
    
    // Power management
    private PowerManager.WakeLock wakeLock;
    private KeyguardManager.KeyguardLock keyguardLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setupFullscreenMode();
        setContentView(R.layout.activity_alarm);
        
        initializeManagers();
        initializeViews();
        setupEventListeners();
        processIntentData();
        startAlarmSequence();
        
        Log.e(TAG, "AlarmActivity started - SECURITY BREACH!");
    }

    private void setupFullscreenMode() {
        // Make activity fullscreen and bypass lock screen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        
        // Hide navigation and status bars
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        
        acquireWakeLock();
    }

    private void initializeManagers() {
        securityManager = new SecurityManager(this);
        alarmManager = new AlarmManager(this);
        evidenceManager = new EvidenceManager(this);
    }

    private void initializeViews() {
        txtAlarmMessage = findViewById(R.id.txtAlarmMessage);
        txtTriggerType = findViewById(R.id.txtTriggerType);
        txtTriggerTime = findViewById(R.id.txtTriggerTime);
        txtTriggerDetails = findViewById(R.id.txtTriggerDetails);
        txtAlarmDuration = findViewById(R.id.txtAlarmDuration);
        txtEvidenceStatus = findViewById(R.id.txtEvidenceStatus);
        txtPinWarning = findViewById(R.id.txtPinWarning);
        
        editAlarmPin = findViewById(R.id.editAlarmPin);
        btnDisarmAlarm = findViewById(R.id.btnDisarmAlarm);
        btnSnoozeAlarm = findViewById(R.id.btnSnoozeAlarm);
        btnViewEvidence = findViewById(R.id.btnViewEvidence);
        btnEmergencyCall = findViewById(R.id.btnEmergencyCall);
        btnCancelEmergency = findViewById(R.id.btnCancelEmergency);
        
        iconWarning = findViewById(R.id.iconWarning);
        flashOverlay = findViewById(R.id.flashOverlay);
        emergencyOverlay = findViewById(R.id.emergencyOverlay);
        
        alarmStartTime = System.currentTimeMillis();
        updateTriggerTime();
    }

    private void setupEventListeners() {
        btnDisarmAlarm.setOnClickListener(v -> attemptDisarm());
        btnSnoozeAlarm.setOnClickListener(v -> snoozeAlarm());
        btnViewEvidence.setOnClickListener(v -> viewEvidence());
        btnEmergencyCall.setOnClickListener(v -> activateEmergencyMode());
        btnCancelEmergency.setOnClickListener(v -> cancelEmergencyMode());
        
        // Auto-submit PIN on 4+ digits
        editAlarmPin.setOnEditorActionListener((v, actionId, event) -> {
            if (editAlarmPin.getText().length() >= 4) {
                attemptDisarm();
                return true;
            }
            return false;
        });
    }

    private void processIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            triggerType = intent.getStringExtra("trigger_type");
            triggerDetails = intent.getStringExtra("trigger_details");
            float magnitude = intent.getFloatExtra("magnitude", 0);
            
            if (triggerType == null) triggerType = "Security Breach";
            if (triggerDetails == null) triggerDetails = "Unauthorized access detected";
            
            updateAlarmMessage();
        }
    }

    private void startAlarmSequence() {
        Log.e(TAG, "Starting alarm sequence - " + triggerType);
        
        // Start visual effects
        startFlashAnimation();
        startPulseAnimation();
        
        // Start evidence collection
        startEvidenceCollection();
        
        // Start UI updates
        startUIUpdates();
        
        // Disable volume keys
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
    }

    private void startFlashAnimation() {
        flashAnimator = ObjectAnimator.ofFloat(flashOverlay, "alpha", 0f, 0.3f, 0f);
        flashAnimator.setDuration(1000);
        flashAnimator.setRepeatCount(ValueAnimator.INFINITE);
        flashAnimator.start();
    }

    private void startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(iconWarning, "scaleX", 1f, 1.2f, 1f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(iconWarning, "scaleY", 1f, 1.2f, 1f);
        pulseY.setDuration(1500);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        
        pulseAnimator.start();
        pulseY.start();
    }

    private void startEvidenceCollection() {
        txtEvidenceStatus.setText("📷 Capturing Evidence...");
        txtEvidenceStatus.setTextColor(getColor(R.color.evidence_active));
        
        // Start evidence capture in background
        evidenceManager.captureSecurityEvidence("Alarm triggered: " + triggerType);
        
        // Update evidence status after a delay
        uiUpdateHandler.postDelayed(() -> {
            txtEvidenceStatus.setText("📷 Evidence Collected");
            txtEvidenceStatus.setTextColor(getColor(R.color.evidence_complete));
        }, 8000);
    }

    private void startUIUpdates() {
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAlarmActive) {
                    updateAlarmDuration();
                    updatePinAttemptsWarning();
                    uiUpdateHandler.postDelayed(this, 1000); // Update every second
                }
            }
        };
        uiUpdateHandler.post(uiUpdateRunnable);
    }

    private void updateAlarmMessage() {
        String message = "UNAUTHORIZED DEVICE ACCESS DETECTED";
        
        switch (triggerType) {
            case "Motion Detection":
                message = "UNAUTHORIZED DEVICE MOVEMENT DETECTED";
                break;
            case "Break-in Detection":
                message = "BREAK-IN ATTEMPT DETECTED";
                break;
            case "Camera Trigger":
                message = "CAMERA TAMPERING DETECTED";
                break;
            case "Manual Trigger":
                message = "MANUAL SECURITY ALERT ACTIVATED";
                break;
        }
        
        txtAlarmMessage.setText(message);
        txtTriggerType.setText("Trigger: " + triggerType);
        txtTriggerDetails.setText(triggerDetails);
    }

    private void updateTriggerTime() {
        String timeStr = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            .format(new Date(alarmStartTime));
        txtTriggerTime.setText("Time: " + timeStr);
    }

    private void updateAlarmDuration() {
        long duration = System.currentTimeMillis() - alarmStartTime;
        int minutes = (int) (duration / 60000);
        int seconds = (int) ((duration % 60000) / 1000);
        
        txtAlarmDuration.setText(String.format("⏱️ %02d:%02d", minutes, seconds));
    }

    private void updatePinAttemptsWarning() {
        int attemptsRemaining = MAX_PIN_ATTEMPTS - pinAttempts;
        
        if (pinAttempts > 0) {
            txtPinWarning.setVisibility(View.VISIBLE);
            if (attemptsRemaining > 1) {
                txtPinWarning.setText("⚠️ Warning: " + attemptsRemaining + " attempts remaining");
                txtPinWarning.setTextColor(getColor(R.color.warning_color));
            } else if (attemptsRemaining == 1) {
                txtPinWarning.setText("🚨 FINAL ATTEMPT - Device will lock permanently!");
                txtPinWarning.setTextColor(getColor(R.color.error_color));
            } else {
                txtPinWarning.setText("🔒 TOO MANY ATTEMPTS - DEVICE LOCKED");
                txtPinWarning.setTextColor(getColor(R.color.error_color));
                lockDevice();
            }
        }
    }

    private void attemptDisarm() {
        String pin = editAlarmPin.getText().toString();
        
        if (pin.length() < 4) {
            showError("PIN must be at least 4 digits");
            return;
        }
        
        if (securityManager.validatePin(pin)) {
            disarmAlarm();
        } else {
            pinAttempts++;
            editAlarmPin.setText("");
            showError("Invalid PIN");
            
            // Record failed attempt
            securityManager.recordTrigger("Failed PIN Attempt", 
                "Attempt #" + pinAttempts + " during alarm");
            
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                lockDevice();
            }
        }
    }

    private void disarmAlarm() {
        Log.i(TAG, "Alarm disarmed successfully");
        
        isAlarmActive = false;
        
        // Stop alarm sound
        alarmManager.stopAlarm();
        
        // Stop animations
        stopAnimations();
        
        // Update security manager
        securityManager.setArmed(false);
        
        // Show success message
        showSuccess("Security system disarmed");
        
        // Finish activity
        finishAlarm();
    }

    private void snoozeAlarm() {
        if (isSnoozing) {
            showError("Already snoozing");
            return;
        }
        
        Log.i(TAG, "Alarm snoozed for 5 minutes");
        
        isSnoozing = true;
        alarmManager.stopAlarm();
        stopAnimations();
        
        btnSnoozeAlarm.setText("SNOOZED (5m)");
        btnSnoozeAlarm.setEnabled(false);
        
        showToast("Alarm snoozed for 5 minutes");
        
        // Restart alarm after 5 minutes
        uiUpdateHandler.postDelayed(() -> {
            if (isAlarmActive) {
                alarmManager.startAlarm();
                startFlashAnimation();
                startPulseAnimation();
                isSnoozing = false;
                btnSnoozeAlarm.setText("SNOOZE");
                btnSnoozeAlarm.setEnabled(true);
                showToast("Snooze ended - alarm reactivated");
            }
        }, 5 * 60 * 1000); // 5 minutes
    }

    private void viewEvidence() {
        Intent intent = new Intent(this, EvidenceGalleryActivity.class);
        intent.putExtra("alarm_session", true);
        startActivity(intent);
    }

    private void activateEmergencyMode() {
        Log.w(TAG, "Emergency mode activated");
        
        isEmergencyMode = true;
        emergencyOverlay.setVisibility(View.VISIBLE);
        
        // Start emergency procedures
        securityManager.enableEmergencyMode();
        
        // Could add GPS location sharing, emergency contacts notification, etc.
        showToast("Emergency mode activated - sending alerts");
    }

    private void cancelEmergencyMode() {
        isEmergencyMode = false;
        emergencyOverlay.setVisibility(View.GONE);
        securityManager.disableEmergencyMode();
        
        showToast("Emergency mode cancelled");
    }

    private void lockDevice() {
        Log.e(TAG, "Device locked due to too many PIN attempts");
        
        // Disable all controls
        editAlarmPin.setEnabled(false);
        btnDisarmAlarm.setEnabled(false);
        btnSnoozeAlarm.setEnabled(false);
        
        // Show permanent lock message
        txtAlarmMessage.setText("DEVICE PERMANENTLY LOCKED");
        txtPinWarning.setText("🔒 Contact device owner to unlock");
        
        // Record security event
        securityManager.recordTrigger("Device Locked", 
            "Too many failed PIN attempts during alarm");
        
        // Could trigger additional security measures here
    }

    private void stopAnimations() {
        if (flashAnimator != null) {
            flashAnimator.cancel();
        }
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        
        // Reset views to normal state
        flashOverlay.setAlpha(0f);
        iconWarning.setScaleX(1f);
        iconWarning.setScaleY(1f);
    }

    private void finishAlarm() {
        // Stop all background tasks
        if (uiUpdateRunnable != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
        }
        
        // Release wake lock
        releaseWakeLock();
        
        // Return to main activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        
        finish();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AntiTheft:AlarmWakeLock"
        );
        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes max
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // Prevent back button from closing alarm
    @Override
    public void onBackPressed() {
        showToast("Enter PIN to disarm alarm");
    }

    // Disable volume keys during alarm
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || 
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            showToast("Volume controls disabled during alarm");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Don't allow alarm to be paused - restart immediately
        if (isAlarmActive && !isEmergencyMode) {
            Intent restart = new Intent(this, AlarmActivity.class);
            restart.putExtra("trigger_type", triggerType);
            restart.putExtra("trigger_details", triggerDetails);
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(restart);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        stopAnimations();
        releaseWakeLock();
        
        // Cleanup managers
        if (alarmManager != null) {
            alarmManager.cleanup();
        }
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
        
        // Remove all callbacks
        if (uiUpdateHandler != null) {
            uiUpdateHandler.removeCallbacksAndMessages(null);
        }
        
        Log.i(TAG, "AlarmActivity destroyed");
    }

    // Utility methods
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
    }

    private void showSuccess(String message) {
        Toast.makeText(this, "✓ " + message, Toast.LENGTH_SHORT).show();
    }
}

// File Path: app/src/main/java/com/antitheft/security/MainActivity.java
package com.antitheft.security;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.antitheft.security.receiver.DeviceAdminReceiver;
import com.antitheft.security.service.SecurityService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int DEVICE_ADMIN_REQUEST = 1002;
    private static final int OVERLAY_PERMISSION_REQUEST = 1003;

    private Switch switchAntiTheft, switchStealth, switchLocationTracking;
    private MaterialButton btnSetup, btnViewEvidence, btnTestAlert;
    private MaterialCardView cardStatus;
    private MaterialTextView txtStatus, txtDeviceInfo;
    
    private SharedPreferences preferences;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        checkPermissions();
        updateUI();
    }

    private void initializeViews() {
        switchAntiTheft = findViewById(R.id.switch_anti_theft);
        switchStealth = findViewById(R.id.switch_stealth);
        switchLocationTracking = findViewById(R.id.switch_location_tracking);
        btnSetup = findViewById(R.id.btn_setup);
        btnViewEvidence = findViewById(R.id.btn_view_evidence);
        btnTestAlert = findViewById(R.id.btn_test_alert);
        cardStatus = findViewById(R.id.card_status);
        txtStatus = findViewById(R.id.txt_status);
        txtDeviceInfo = findViewById(R.id.txt_device_info);

        // Set click listeners
        switchAntiTheft.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startSecurityService();
            } else {
                stopSecurityService();
            }
            updateStatus();
        });

        switchStealth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("stealth_mode", isChecked).apply();
            if (isChecked) {
                enableStealthMode();
            } else {
                disableStealthMode();
            }
        });

        switchLocationTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("location_tracking", isChecked).apply();
        });

        btnSetup.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        btnViewEvidence.setOnClickListener(v -> startActivity(new Intent(this, EvidenceViewerActivity.class)));
        btnTestAlert.setOnClickListener(v -> testSecurityAlert());
    }

    private void initializeComponents() {
        preferences = getSharedPreferences("AntiTheftPrefs", MODE_PRIVATE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);
    }

    private void checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        }

        // Check device admin
        if (!devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                "Enable device admin to allow remote wipe and enhanced security features");
            startActivityForResult(intent, DEVICE_ADMIN_REQUEST);
        }
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
            if (!allGranted) {
                Toast.makeText(this, "All permissions are required for proper functionality", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case DEVICE_ADMIN_REQUEST:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Device admin enabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Device admin required for full functionality", 
                        Toast.LENGTH_LONG).show();
                }
                break;
                
            case OVERLAY_PERMISSION_REQUEST:
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Overlay permission required for alerts", 
                        Toast.LENGTH_LONG).show();
                }
                break;
        }
        updateUI();
    }

    private void updateUI() {
        // Update switches based on current state
        switchAntiTheft.setChecked(SecurityService.isRunning());
        switchStealth.setChecked(preferences.getBoolean("stealth_mode", false));
        switchLocationTracking.setChecked(preferences.getBoolean("location_tracking", true));
        
        // Update device info
        updateDeviceInfo();
        updateStatus();
    }

    private void updateDeviceInfo() {
        String deviceInfo = "Device: " + android.os.Build.MODEL + "\n" +
                           "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
                           "Security Patch: " + android.os.Build.VERSION.SECURITY_PATCH;
        txtDeviceInfo.setText(deviceInfo);
    }

    private void updateStatus() {
        if (SecurityService.isRunning()) {
            txtStatus.setText("PROTECTED");
            txtStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            cardStatus.setCardBackgroundColor(getColor(android.R.color.holo_green_light));
        } else {
            txtStatus.setText("UNPROTECTED");
            txtStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            cardStatus.setCardBackgroundColor(getColor(android.R.color.holo_red_light));
        }
    }

    private void startSecurityService() {
        if (hasAllPermissions()) {
            Intent serviceIntent = new Intent(this, SecurityService.class);
            startForegroundService(serviceIntent);
            Toast.makeText(this, "Anti-theft protection enabled", Toast.LENGTH_SHORT).show();
        } else {
            switchAntiTheft.setChecked(false);
            Toast.makeText(this, "Please grant all required permissions", Toast.LENGTH_LONG).show();
            checkPermissions();
        }
    }

    private void stopSecurityService() {
        Intent serviceIntent = new Intent(this, SecurityService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "Anti-theft protection disabled", Toast.LENGTH_SHORT).show();
    }

    private void enableStealthMode() {
        // Hide app icon from launcher
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, 
            "com.antitheft.security.MainActivity-Alias");
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
        
        Toast.makeText(this, "Stealth mode enabled - app icon hidden", Toast.LENGTH_LONG).show();
    }

    private void disableStealthMode() {
        // Show app icon in launcher
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, 
            "com.antitheft.security.MainActivity-Alias");
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
        
        Toast.makeText(this, "Stealth mode disabled - app icon visible", Toast.LENGTH_SHORT).show();
    }

    private void testSecurityAlert() {
        Intent testIntent = new Intent(this, SecurityService.class);
        testIntent.setAction("TEST_ALERT");
        startForegroundService(testIntent);
        Toast.makeText(this, "Testing security alert...", Toast.LENGTH_SHORT).show();
    }

    private boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}

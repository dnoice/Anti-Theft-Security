// File Path: app/src/main/java/com/antitheft/security/SetupActivity.java
package com.antitheft.security;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SetupActivity extends AppCompatActivity {

    private TextInputLayout layoutEmergencyContact, layoutSecurityPin, layoutMaxAttempts;
    private TextInputEditText editEmergencyContact, editSecurityPin, editMaxAttempts;
    private SwitchMaterial switchPhotoEvidence, switchAudioEvidence, switchEncryptEvidence, 
                           switchSmsAlerts, switchRemoteWipe;
    private MaterialButton btnSave, btnCancel;
    
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        preferences = getSharedPreferences("AntiTheftPrefs", MODE_PRIVATE);
        
        initializeViews();
        loadCurrentSettings();
        setupClickListeners();
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Security Setup");
        }
    }

    private void initializeViews() {
        layoutEmergencyContact = findViewById(R.id.layout_emergency_contact);
        layoutSecurityPin = findViewById(R.id.layout_security_pin);
        layoutMaxAttempts = findViewById(R.id.layout_max_attempts);
        
        editEmergencyContact = findViewById(R.id.edit_emergency_contact);
        editSecurityPin = findViewById(R.id.edit_security_pin);
        editMaxAttempts = findViewById(R.id.edit_max_attempts);
        
        switchPhotoEvidence = findViewById(R.id.switch_photo_evidence);
        switchAudioEvidence = findViewById(R.id.switch_audio_evidence);
        switchEncryptEvidence = findViewById(R.id.switch_encrypt_evidence);
        switchSmsAlerts = findViewById(R.id.switch_sms_alerts);
        switchRemoteWipe = findViewById(R.id.switch_remote_wipe);
        
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void loadCurrentSettings() {
        editEmergencyContact.setText(preferences.getString("emergency_contact", ""));
        editSecurityPin.setText(preferences.getString("security_pin", ""));
        editMaxAttempts.setText(String.valueOf(preferences.getInt("max_wrong_attempts", 3)));
        
        switchPhotoEvidence.setChecked(preferences.getBoolean("photo_evidence", true));
        switchAudioEvidence.setChecked(preferences.getBoolean("audio_evidence", false));
        switchEncryptEvidence.setChecked(preferences.getBoolean("encrypt_evidence", true));
        switchSmsAlerts.setChecked(preferences.getBoolean("sms_alerts", true));
        switchRemoteWipe.setChecked(preferences.getBoolean("remote_wipe_enabled", false));
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void saveSettings() {
        if (!validateInputs()) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        
        // Save text inputs
        editor.putString("emergency_contact", editEmergencyContact.getText().toString().trim());
        editor.putString("security_pin", editSecurityPin.getText().toString().trim());
        editor.putInt("max_wrong_attempts", Integer.parseInt(editMaxAttempts.getText().toString().trim()));
        
        // Save switch states
        editor.putBoolean("photo_evidence", switchPhotoEvidence.isChecked());
        editor.putBoolean("audio_evidence", switchAudioEvidence.isChecked());
        editor.putBoolean("encrypt_evidence", switchEncryptEvidence.isChecked());
        editor.putBoolean("sms_alerts", switchSmsAlerts.isChecked());
        editor.putBoolean("remote_wipe_enabled", switchRemoteWipe.isChecked());
        
        // Mark setup as completed
        editor.putBoolean("setup_completed", true);
        
        editor.apply();
        
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean validateInputs() {
        boolean isValid = true;
        
        // Validate emergency contact
        String emergencyContact = editEmergencyContact.getText().toString().trim();
        if (TextUtils.isEmpty(emergencyContact)) {
            layoutEmergencyContact.setError("Emergency contact is required");
            isValid = false;
        } else if (!android.util.Patterns.PHONE.matcher(emergencyContact).matches()) {
            layoutEmergencyContact.setError("Please enter a valid phone number");
            isValid = false;
        } else {
            layoutEmergencyContact.setError(null);
        }
        
        // Validate security PIN
        String securityPin = editSecurityPin.getText().toString().trim();
        if (TextUtils.isEmpty(securityPin)) {
            layoutSecurityPin.setError("Security PIN is required");
            isValid = false;
        } else if (securityPin.length() < 4) {
            layoutSecurityPin.setError("PIN must be at least 4 digits");
            isValid = false;
        } else {
            layoutSecurityPin.setError(null);
        }
        
        // Validate max attempts
        String maxAttemptsStr = editMaxAttempts.getText().toString().trim();
        if (TextUtils.isEmpty(maxAttemptsStr)) {
            layoutMaxAttempts.setError("Max attempts is required");
            isValid = false;
        } else {
            try {
                int maxAttempts = Integer.parseInt(maxAttemptsStr);
                if (maxAttempts < 1 || maxAttempts > 10) {
                    layoutMaxAttempts.setError("Max attempts must be between 1 and 10");
                    isValid = false;
                } else {
                    layoutMaxAttempts.setError(null);
                }
            } catch (NumberFormatException e) {
                layoutMaxAttempts.setError("Please enter a valid number");
                isValid = false;
            }
        }
        
        return isValid;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

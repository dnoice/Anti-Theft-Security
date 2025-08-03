// File Path: app/src/main/java/com/antitheft/security/service/SecurityService.java
package com.antitheft.security.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.antitheft.security.MainActivity;
import com.antitheft.security.R;
import com.antitheft.security.receiver.DeviceAdminReceiver;
import com.antitheft.security.utils.EvidenceCollector;
import com.antitheft.security.utils.EncryptionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SecurityService extends Service implements LocationListener {

    private static final String TAG = "SecurityService";
    private static final String CHANNEL_ID = "ANTITHEFT_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    
    private static boolean isServiceRunning = false;
    
    private LocationManager locationManager;
    private MediaRecorder mediaRecorder;
    private Camera camera;
    private SharedPreferences preferences;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private EvidenceCollector evidenceCollector;
    
    private Handler mainHandler;
    private boolean isRecording = false;
    private boolean isLocationTracking = false;
    private int wrongAttemptCount = 0;
    
    // Evidence collection intervals
    private static final long LOCATION_UPDATE_INTERVAL = 30000; // 30 seconds
    private static final long EVIDENCE_COLLECTION_INTERVAL = 60000; // 1 minute
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        preferences = getSharedPreferences("AntiTheftPrefs", MODE_PRIVATE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);
        evidenceCollector = new EvidenceCollector(this);
        mainHandler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        initializeLocationTracking();
        
        Log.d(TAG, "SecurityService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isServiceRunning = true;
        
        if (intent != null && "TEST_ALERT".equals(intent.getAction())) {
            handleTestAlert();
            return START_STICKY;
        }
        
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Start evidence collection
        startEvidenceCollection();
        
        // Start location tracking if enabled
        if (preferences.getBoolean("location_tracking", true)) {
            startLocationTracking();
        }
        
        Log.d(TAG, "SecurityService started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        
        stopLocationTracking();
        stopAudioRecording();
        releaseCamera();
        
        Log.d(TAG, "SecurityService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        return isServiceRunning;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Anti-Theft Security",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Anti-theft protection is active");
        channel.setShowBadge(false);
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Anti-Theft Protection Active")
            .setContentText("Your device is being monitored and protected")
            .setSmallIcon(R.drawable.ic_security)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void startEvidenceCollection() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isServiceRunning) {
                    collectEvidence();
                    mainHandler.postDelayed(this, EVIDENCE_COLLECTION_INTERVAL);
                }
            }
        }, EVIDENCE_COLLECTION_INTERVAL);
    }

    private void collectEvidence() {
        // Collect device state information
        evidenceCollector.collectDeviceState();
        
        // Collect network information
        evidenceCollector.collectNetworkInfo();
        
        // Take photo if camera is available
        if (preferences.getBoolean("photo_evidence", true)) {
            capturePhoto();
        }
        
        // Record audio if enabled
        if (preferences.getBoolean("audio_evidence", false)) {
            startAudioRecording();
        }
    }

    private void initializeLocationTracking() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void startLocationTracking() {
        if (locationManager != null && !isLocationTracking) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    10, // 10 meters
                    this
                );
                
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    10,
                    this
                );
                
                isLocationTracking = true;
                Log.d(TAG, "Location tracking started");
            } catch (SecurityException e) {
                Log.e(TAG, "Location permission not granted", e);
            }
        }
    }

    private void stopLocationTracking() {
        if (locationManager != null && isLocationTracking) {
            locationManager.removeUpdates(this);
            isLocationTracking = false;
            Log.d(TAG, "Location tracking stopped");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Save location evidence
        evidenceCollector.saveLocationEvidence(location);
        
        // Send location to emergency contacts if theft detected
        if (preferences.getBoolean("theft_detected", false)) {
            sendLocationAlert(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    private void capturePhoto() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (camera != null) {
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPictureFormat(ImageFormat.JPEG);
                camera.setParameters(parameters);
                
                // Create a dummy preview to satisfy camera requirements
                SurfaceTexture dummyTexture = new SurfaceTexture(0);
                camera.setPreviewTexture(dummyTexture);
                camera.startPreview();
                
                camera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        savePhotoEvidence(data);
                        releaseCamera();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing photo", e);
            releaseCamera();
        }
    }

    private void savePhotoEvidence(byte[] data) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String filename = "evidence_photo_" + timestamp + ".jpg";
            
            File evidenceDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Evidence");
            if (!evidenceDir.exists()) {
                evidenceDir.mkdirs();
            }
            
            File photoFile = new File(evidenceDir, filename);
            FileOutputStream fos = new FileOutputStream(photoFile);
            fos.write(data);
            fos.close();
            
            // Encrypt and store evidence metadata
            evidenceCollector.saveEvidenceMetadata("PHOTO", photoFile.getAbsolutePath(), new Date());
            
            Log.d(TAG, "Photo evidence saved: " + filename);
        } catch (IOException e) {
            Log.e(TAG, "Error saving photo evidence", e);
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void startAudioRecording() {
        if (!isRecording) {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
                String filename = "evidence_audio_" + timestamp + ".3gp";
                
                File evidenceDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Evidence");
                if (!evidenceDir.exists()) {
                    evidenceDir.mkdirs();
                }
                
                File audioFile = new File(evidenceDir, filename);
                
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
                mediaRecorder.setMaxDuration(30000); // 30 seconds
                
                mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                    @Override
                    public void onInfo(MediaRecorder mr, int what, int extra) {
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                            stopAudioRecording();
                        }
                    }
                });
                
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
                
                // Save evidence metadata
                evidenceCollector.saveEvidenceMetadata("AUDIO", audioFile.getAbsolutePath(), new Date());
                
                Log.d(TAG, "Audio recording started: " + filename);
            } catch (Exception e) {
                Log.e(TAG, "Error starting audio recording", e);
                stopAudioRecording();
            }
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                Log.d(TAG, "Audio recording stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio recording", e);
            }
        }
    }

    private void handleTestAlert() {
        // Simulate theft detection
        preferences.edit().putBoolean("theft_detected", true).apply();
        
        // Collect immediate evidence
        collectEvidence();
        
        // Send alert to emergency contacts
        sendTheftAlert();
        
        // Reset theft detection after test
        mainHandler.postDelayed(() -> {
            preferences.edit().putBoolean("theft_detected", false).apply();
        }, 5000);
    }

    public void onWrongPasswordAttempt() {
        wrongAttemptCount++;
        preferences.edit().putInt("wrong_attempts", wrongAttemptCount).apply();
        
        // Trigger theft detection after configured attempts
        int maxAttempts = preferences.getInt("max_wrong_attempts", 3);
        if (wrongAttemptCount >= maxAttempts) {
            triggerTheftDetection();
        }
        
        // Always collect evidence on wrong attempts
        collectEvidence();
    }

    private void triggerTheftDetection() {
        preferences.edit().putBoolean("theft_detected", true).apply();
        
        // Enhanced evidence collection
        collectEvidence();
        
        // Send immediate alert
        sendTheftAlert();
        
        // Lock device if device admin is enabled
        if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            devicePolicyManager.lockNow();
        }
        
        Log.w(TAG, "Theft detection triggered!");
    }

    private void sendTheftAlert() {
        String emergencyContact = preferences.getString("emergency_contact", "");
        if (!emergencyContact.isEmpty()) {
            String message = "ALERT: Possible theft detected on your device. " +
                           "Device location and evidence are being collected.";
            
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(emergencyContact, null, message, null, null);
                Log.d(TAG, "Theft alert sent to: " + emergencyContact);
            } catch (Exception e) {
                Log.e(TAG, "Error sending theft alert", e);
            }
        }
    }

    private void sendLocationAlert(Location location) {
        String emergencyContact = preferences.getString("emergency_contact", "");
        if (!emergencyContact.isEmpty()) {
            String message = String.format(Locale.getDefault(),
                "Device location: https://maps.google.com/?q=%f,%f",
                location.getLatitude(), location.getLongitude());
            
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(emergencyContact, null, message, null, null);
                Log.d(TAG, "Location alert sent");
            } catch (Exception e) {
                Log.e(TAG, "Error sending location alert", e);
            }
        }
    }
}

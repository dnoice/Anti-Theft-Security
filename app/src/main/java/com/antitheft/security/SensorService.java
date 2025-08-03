// File: app/src/main/java/com/antitheft/security/SensorService.java

package com.antitheft.security;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = "AntiTheft_SensorService";
    private static final String NOTIFICATION_CHANNEL_ID = "motion_detection_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Motion detection parameters
    private static final float MOTION_THRESHOLD_LOW = 2.0f;
    private static final float MOTION_THRESHOLD_MEDIUM = 5.0f;
    private static final float MOTION_THRESHOLD_HIGH = 8.0f;
    private static final long MOTION_DETECTION_INTERVAL = 100; // 100ms
    private static final long ALARM_TRIGGER_DELAY = 2000; // 2 seconds
    private static final long MOTION_TIMEOUT = 30000; // 30 seconds of no motion to reset
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private PowerManager.WakeLock wakeLock;
    
    private SecurityManager securityManager;
    private AlarmManager alarmManager;
    private EvidenceManager evidenceManager;
    
    // Motion detection state
    private boolean isMonitoring = false;
    private float lastX, lastY, lastZ;
    private long lastMotionTime = 0;
    private long motionStartTime = 0;
    private boolean motionDetected = false;
    private int consecutiveMotionEvents = 0;
    private static final int MOTION_EVENTS_THRESHOLD = 3;
    
    // Handler for delayed alarm triggering
    private Handler alarmHandler = new Handler();
    private Runnable alarmTriggerRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        
        initializeServices();
        createNotificationChannel();
        acquireWakeLock();
        
        Log.i(TAG, "SensorService created");
    }

    private void initializeServices() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        securityManager = new SecurityManager(this);
        alarmManager = new AlarmManager(this);
        evidenceManager = new EvidenceManager(this);
        
        // Get sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer found on device");
        }
        if (gyroscope == null) {
            Log.w(TAG, "No gyroscope found on device");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!securityManager.isArmed()) {
            Log.w(TAG, "Service started but security not armed");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        startForegroundService();
        startMotionDetection();
        
        Log.i(TAG, "Motion detection service started");
        return START_STICKY; // Restart if killed
    }

    private void startForegroundService() {
        Notification notification = createServiceNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🛡️ Anti-Theft Protection Active")
            .setContentText("Motion detection is monitoring your device")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void startMotionDetection() {
        if (isMonitoring) return;
        
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        
        isMonitoring = true;
        resetMotionState();
        
        Log.i(TAG, "Motion detection started");
    }

    private void stopMotionDetection() {
        if (!isMonitoring) return;
        
        sensorManager.unregisterListener(this);
        isMonitoring = false;
        
        // Cancel any pending alarm
        if (alarmTriggerRunnable != null) {
            alarmHandler.removeCallbacks(alarmTriggerRunnable);
        }
        
        Log.i(TAG, "Motion detection stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isMonitoring) return;
        
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometerData(event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            handleGyroscopeData(event);
        }
    }

    private void handleAccelerometerData(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        // Calculate motion magnitude
        float deltaX = Math.abs(x - lastX);
        float deltaY = Math.abs(y - lastY);
        float deltaZ = Math.abs(z - lastZ);
        float motionMagnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        
        // Update last values
        lastX = x;
        lastY = y;
        lastZ = z;
        
        // Check if motion exceeds threshold
        float threshold = getMotionThreshold();
        if (motionMagnitude > threshold) {
            onMotionDetected(motionMagnitude, "Accelerometer");
        } else {
            onNoMotion();
        }
    }

    private void handleGyroscopeData(SensorEvent event) {
        // Gyroscope data for rotational motion detection
        float rotationMagnitude = (float) Math.sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        );
        
        // Convert rad/s to degrees/s and check threshold
        float rotationDegrees = (float) Math.toDegrees(rotationMagnitude);
        float rotationThreshold = getMotionThreshold() * 10; // Scale for rotation
        
        if (rotationDegrees > rotationThreshold) {
            onMotionDetected(rotationDegrees, "Gyroscope");
        }
    }

    private float getMotionThreshold() {
        int sensitivity = securityManager.getSensitivity();
        
        if (sensitivity <= 20) {
            return MOTION_THRESHOLD_HIGH;
        } else if (sensitivity <= 40) {
            return MOTION_THRESHOLD_MEDIUM + (MOTION_THRESHOLD_HIGH - MOTION_THRESHOLD_MEDIUM) * (40 - sensitivity) / 20f;
        } else if (sensitivity <= 60) {
            return MOTION_THRESHOLD_MEDIUM;
        } else if (sensitivity <= 80) {
            return MOTION_THRESHOLD_LOW + (MOTION_THRESHOLD_MEDIUM - MOTION_THRESHOLD_LOW) * (80 - sensitivity) / 20f;
        } else {
            return MOTION_THRESHOLD_LOW;
        }
    }

    private void onMotionDetected(float magnitude, String sensorType) {
        long currentTime = System.currentTimeMillis();
        
        if (!motionDetected) {
            motionDetected = true;
            motionStartTime = currentTime;
            consecutiveMotionEvents = 1;
            
            Log.d(TAG, "Initial motion detected - " + sensorType + ": " + magnitude);
        } else {
            consecutiveMotionEvents++;
        }
        
        lastMotionTime = currentTime;
        
        // Check if we have enough consecutive motion events to trigger alarm
        if (consecutiveMotionEvents >= MOTION_EVENTS_THRESHOLD) {
            scheduleAlarmTrigger(magnitude, sensorType);
        }
        
        // Update service notification
        updateServiceNotification("Motion detected", "Magnitude: " + String.format("%.2f", magnitude));
    }

    private void onNoMotion() {
        long currentTime = System.currentTimeMillis();
        
        // Reset motion state if no motion for timeout period
        if (motionDetected && (currentTime - lastMotionTime) > MOTION_TIMEOUT) {
            resetMotionState();
            Log.d(TAG, "Motion timeout - resetting state");
            updateServiceNotification("Monitoring", "No motion detected");
        }
    }

    private void scheduleAlarmTrigger(float magnitude, String sensorType) {
        // Cancel any existing alarm trigger
        if (alarmTriggerRunnable != null) {
            alarmHandler.removeCallbacks(alarmTriggerRunnable);
        }
        
        // Schedule new alarm trigger
        alarmTriggerRunnable = () -> triggerSecurityAlarm(magnitude, sensorType);
        alarmHandler.postDelayed(alarmTriggerRunnable, ALARM_TRIGGER_DELAY);
        
        Log.w(TAG, "Alarm trigger scheduled - " + sensorType + ": " + magnitude);
    }

    private void triggerSecurityAlarm(float magnitude, String sensorType) {
        Log.e(TAG, "SECURITY BREACH DETECTED! " + sensorType + " magnitude: " + magnitude);
        
        // Record the security trigger
        String details = String.format("Motion detected - %s: %.2f (Sensitivity: %d%%)", 
            sensorType, magnitude, securityManager.getSensitivity());
        securityManager.recordTrigger("Motion Detection", details);
        
        // Start evidence collection if enabled
        if (securityManager.isCameraEvidenceEnabled()) {
            evidenceManager.captureSecurityEvidence("Motion detection triggered");
        }
        
        // Trigger the alarm
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.putExtra("trigger_type", "Motion Detection");
        alarmIntent.putExtra("trigger_details", details);
        alarmIntent.putExtra("magnitude", magnitude);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(alarmIntent);
        
        // Start alarm sound/vibration
        alarmManager.startAlarm();
        
        // Update notification
        updateServiceNotification("🚨 ALARM TRIGGERED", "Motion detected: " + magnitude);
        
        // Don't reset motion state - keep monitoring for additional triggers
    }

    private void resetMotionState() {
        motionDetected = false;
        consecutiveMotionEvents = 0;
        motionStartTime = 0;
        
        // Cancel any pending alarm
        if (alarmTriggerRunnable != null) {
            alarmHandler.removeCallbacks(alarmTriggerRunnable);
            alarmTriggerRunnable = null;
        }
    }

    private void updateServiceNotification(String title, String text) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🛡️ " + title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
        
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Motion Detection Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when motion detection is active");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AntiTheft:MotionDetection"
        );
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + " accuracy: " + accuracy);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        stopMotionDetection();
        releaseWakeLock();
        
        // Cleanup
        if (alarmManager != null) {
            alarmManager.cleanup();
        }
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
        
        Log.i(TAG, "SensorService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }

    // Public methods for external control
    public static void startMotionMonitoring(Context context) {
        Intent serviceIntent = new Intent(context, SensorService.class);
        context.startService(serviceIntent);
    }

    public static void stopMotionMonitoring(Context context) {
        Intent serviceIntent = new Intent(context, SensorService.class);
        context.stopService(serviceIntent);
    }

    // Get motion detection statistics
    public String getMotionStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Motion Detection: ").append(isMonitoring ? "Active" : "Inactive").append("\n");
        stats.append("Current Threshold: ").append(String.format("%.2f", getMotionThreshold())).append("\n");
        stats.append("Sensitivity: ").append(securityManager.getSensitivity()).append("%\n");
        stats.append("Motion Events: ").append(consecutiveMotionEvents).append("\n");
        
        if (motionDetected) {
            long motionDuration = System.currentTimeMillis() - motionStartTime;
            stats.append("Motion Duration: ").append(motionDuration / 1000).append("s");
        } else {
            stats.append("Status: Monitoring for motion");
        }
        
        return stats.toString();
    }
}

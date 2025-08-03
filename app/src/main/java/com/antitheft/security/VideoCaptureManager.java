// File: app/src/main/java/com/antitheft/security/VideoCaptureManager.java

package com.antitheft.security;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class VideoCaptureManager {
    private static final String TAG = "AntiTheft_Video";
    private static final int VIDEO_DURATION_SECONDS = 10;
    
    private Context context;
    private android.hardware.camera2.CameraManager cameraManager;
    private CameraDevice frontCamera, backCamera;
    private MediaRecorder frontRecorder, backRecorder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    private boolean isRecording = false;
    private VideoCaptureCallback captureCallback;
    
    public interface VideoCaptureCallback {
        void onVideosRecorded(String frontVideoPath, String backVideoPath);
        void onVideoError(String error);
        void onRecordingProgress(int secondsRemaining);
    }
    
    public VideoCaptureManager(Context context) {
        this.context = context;
        this.cameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("VideoBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    public boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    
    public void startSecretVideoRecording(VideoCaptureCallback callback) {
        if (isRecording) {
            Log.w(TAG, "Already recording video");
            return;
        }
        
        if (!hasPermissions()) {
            callback.onVideoError("Required permissions not granted");
            return;
        }
        
        this.captureCallback = callback;
        isRecording = true;
        
        Log.i(TAG, "Starting secret video recording");
        
        // Start recording from both cameras
        startFrontVideoRecording();
        startBackVideoRecording();
        
        // Start countdown timer
        startRecordingTimer();
    }
    
    private void startFrontVideoRecording() {
        try {
            String frontCameraId = getFrontCameraId();
            if (frontCameraId == null) {
                Log.w(TAG, "No front camera available");
                return;
            }
            
            // Setup MediaRecorder for front camera
            frontRecorder = new MediaRecorder();
            setupMediaRecorder(frontRecorder, "front");
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        frontCamera = camera;
                        startRecordingSession(frontCamera, frontRecorder);
                    }
                    
                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        frontCamera = null;
                    }
                    
                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "Front camera error: " + error);
                        camera.close();
                        frontCamera = null;
                    }
                }, backgroundHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting front video recording", e);
        }
    }
    
    private void startBackVideoRecording() {
        try {
            String backCameraId = getBackCameraId();
            if (backCameraId == null) {
                Log.w(TAG, "No back camera available");
                return;
            }
            
            // Setup MediaRecorder for back camera
            backRecorder = new MediaRecorder();
            setupMediaRecorder(backRecorder, "back");
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        backCamera = camera;
                        startRecordingSession(backCamera, backRecorder);
                    }
                    
                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        backCamera = null;
                    }
                    
                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "Back camera error: " + error);
                        camera.close();
                        backCamera = null;
                    }
                }, backgroundHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting back video recording", e);
        }
    }
    
    private void setupMediaRecorder(MediaRecorder recorder, String cameraType) throws IOException {
        // Create video directory
        File videoDir = new File(context.getExternalFilesDir(null), "security_videos");
        if (!videoDir.exists()) {
            videoDir.mkdirs();
        }
        
        // Create filename with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File videoFile = new File(videoDir, "security_" + cameraType + "_" + timestamp + ".mp4");
        
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(videoFile.getAbsolutePath());
        
        // Video settings - optimize for evidence quality vs file size
        recorder.setVideoEncodingBitRate(2000000); // 2Mbps
        recorder.setVideoFrameRate(30);
        recorder.setVideoSize(1280, 720); // 720p for good quality/size balance
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        
        // Audio settings
        recorder.setAudioEncodingBitRate(128000);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
        // Set maximum recording duration
        recorder.setMaxDuration(VIDEO_DURATION_SECONDS * 1000);
        recorder.setOnInfoListener((mr, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                stopVideoRecording();
            }
        });
        
        recorder.prepare();
    }
    
    private void startRecordingSession(CameraDevice camera, MediaRecorder recorder) {
        try {
            Surface recordingSurface = recorder.getSurface();
            
            CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(recordingSurface);
            
            camera.createCaptureSession(Arrays.asList(recordingSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler);
                            recorder.start();
                            Log.d(TAG, "Video recording started");
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error starting video recording", e);
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Video recording session configuration failed");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating recording session", e);
        }
    }
    
    private void startRecordingTimer() {
        Handler timerHandler = new Handler();
        Runnable timerRunnable = new Runnable() {
            int secondsLeft = VIDEO_DURATION_SECONDS;
            
            @Override
            public void run() {
                if (captureCallback != null && isRecording) {
                    captureCallback.onRecordingProgress(secondsLeft);
                }
                
                secondsLeft--;
                if (secondsLeft >= 0 && isRecording) {
                    timerHandler.postDelayed(this, 1000);
                } else if (isRecording) {
                    stopVideoRecording();
                }
            }
        };
        timerHandler.post(timerRunnable);
    }
    
    public void stopVideoRecording() {
        if (!isRecording) return;
        
        Log.d(TAG, "Stopping video recording");
        isRecording = false;
        
        String frontVideoPath = null;
        String backVideoPath = null;
        
        // Stop front recording
        if (frontRecorder != null) {
            try {
                frontRecorder.stop();
                frontVideoPath = getLastVideoPath("front");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping front recorder", e);
            }
            frontRecorder.release();
            frontRecorder = null;
        }
        
        // Stop back recording
        if (backRecorder != null) {
            try {
                backRecorder.stop();
                backVideoPath = getLastVideoPath("back");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping back recorder", e);
            }
            backRecorder.release();
            backRecorder = null;
        }
        
        // Close cameras
        closeCameras();
        
        // Callback with results
        if (captureCallback != null) {
            if (frontVideoPath != null || backVideoPath != null) {
                captureCallback.onVideosRecorded(frontVideoPath, backVideoPath);
            } else {
                captureCallback.onVideoError("Failed to record any videos");
            }
        }
    }
    
    private String getLastVideoPath(String cameraType) {
        File videoDir = new File(context.getExternalFilesDir(null), "security_videos");
        File[] videoFiles = videoDir.listFiles((dir, name) -> 
            name.contains("security_" + cameraType) && name.endsWith(".mp4"));
        
        if (videoFiles != null && videoFiles.length > 0) {
            // Get the most recent file
            File latestFile = videoFiles[0];
            for (File file : videoFiles) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }
            return latestFile.getAbsolutePath();
        }
        return null;
    }
    
    private void closeCameras() {
        if (frontCamera != null) {
            frontCamera.close();
            frontCamera = null;
        }
        if (backCamera != null) {
            backCamera.close();
            backCamera = null;
        }
    }
    
    private String getFrontCameraId() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting front camera ID", e);
        }
        return null;
    }
    
    private String getBackCameraId() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting back camera ID", e);
        }
        return null;
    }
    
    // Get all recorded security videos
    public File[] getSecurityVideos() {
        File videoDir = new File(context.getExternalFilesDir(null), "security_videos");
        if (videoDir.exists()) {
            return videoDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
        }
        return new File[0];
    }
    
    // Cleanup old videos
    public void cleanupOldVideos(int keepCount) {
        File[] videos = getSecurityVideos();
        if (videos != null && videos.length > keepCount) {
            // Sort by last modified date
            java.util.Arrays.sort(videos, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            
            // Delete oldest videos
            for (int i = keepCount; i < videos.length; i++) {
                if (videos[i].delete()) {
                    Log.d(TAG, "Deleted old video: " + videos[i].getName());
                }
            }
        }
    }
    
    public void cleanup() {
        stopVideoRecording();
        closeCameras();
        
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}

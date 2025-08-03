// File: app/src/main/java/com/antitheft/security/MultiplePhotoCaptureManager.java

package com.antitheft.security;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.ImageReader;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MultiplePhotoCaptureManager {
    private static final String TAG = "AntiTheft_MultiPhoto";
    private static final int PHOTOS_PER_CAMERA = 4; // Take 4 photos from each camera
    private static final int PHOTO_INTERVAL_MS = 1500; // 1.5 seconds between photos
    
    private Context context;
    private android.hardware.camera2.CameraManager cameraManager;
    private CameraDevice frontCamera, backCamera;
    private ImageReader frontImageReader, backImageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    private boolean isCapturing = false;
    private MultiplePhotoCaptureCallback captureCallback;
    
    private List<String> frontPhotoPaths = new ArrayList<>();
    private List<String> backPhotoPaths = new ArrayList<>();
    private int frontPhotosRemaining = 0;
    private int backPhotosRemaining = 0;
    
    public interface MultiplePhotoCaptureCallback {
        void onPhotosProgress(int frontCount, int backCount, int totalRemaining);
        void onAllPhotosCompleted(List<String> frontPhotos, List<String> backPhotos);
        void onPhotoCaptureError(String error);
    }
    
    public MultiplePhotoCaptureManager(Context context) {
        this.context = context;
        this.cameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("MultiPhotoBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    public boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    
    public void startMultiplePhotoCapture(MultiplePhotoCaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Already capturing multiple photos");
            return;
        }
        
        if (!hasPermissions()) {
            callback.onPhotoCaptureError("Camera permissions not granted");
            return;
        }
        
        this.captureCallback = callback;
        isCapturing = true;
        
        // Reset counters and lists
        frontPhotoPaths.clear();
        backPhotoPaths.clear();
        frontPhotosRemaining = PHOTOS_PER_CAMERA;
        backPhotosRemaining = PHOTOS_PER_CAMERA;
        
        Log.i(TAG, "Starting multiple photo capture - " + PHOTOS_PER_CAMERA + " photos per camera");
        
        // Start capturing from both cameras
        setupAndStartFrontCapture();
        setupAndStartBackCapture();
    }
    
    private void setupAndStartFrontCapture() {
        try {
            String frontCameraId = getFrontCameraId();
            if (frontCameraId == null) {
                Log.w(TAG, "No front camera found");
                frontPhotosRemaining = 0;
                checkCaptureCompletion();
                return;
            }
            
            // Set up image reader for front camera
            Size frontSize = getBestCaptureSize(frontCameraId);
            frontImageReader = ImageReader.newInstance(frontSize.getWidth(), frontSize.getHeight(), ImageFormat.JPEG, PHOTOS_PER_CAMERA);
            frontImageReader.setOnImageAvailableListener(new FrontCameraImageListener(), backgroundHandler);
            
            // Open front camera
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(frontCameraId, new FrontCameraStateCallback(), backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up front camera", e);
            frontPhotosRemaining = 0;
            checkCaptureCompletion();
        }
    }
    
    private void setupAndStartBackCapture() {
        try {
            String backCameraId = getBackCameraId();
            if (backCameraId == null) {
                Log.w(TAG, "No back camera found");
                backPhotosRemaining = 0;
                checkCaptureCompletion();
                return;
            }
            
            // Set up image reader for back camera
            Size backSize = getBestCaptureSize(backCameraId);
            backImageReader = ImageReader.newInstance(backSize.getWidth(), backSize.getHeight(), ImageFormat.JPEG, PHOTOS_PER_CAMERA);
            backImageReader.setOnImageAvailableListener(new BackCameraImageListener(), backgroundHandler);
            
            // Open back camera
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(backCameraId, new BackCameraStateCallback(), backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up back camera", e);
            backPhotosRemaining = 0;
            checkCaptureCompletion();
        }
    }
    
    private class FrontCameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            frontCamera = camera;
            startFrontPhotoSequence();
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
            frontPhotosRemaining = 0;
            checkCaptureCompletion();
        }
    }
    
    private class BackCameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            backCamera = camera;
            startBackPhotoSequence();
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
            backPhotosRemaining = 0;
            checkCaptureCompletion();
        }
    }
    
    private void startFrontPhotoSequence() {
        takeFrontPhoto(0); // Start with photo 0
    }
    
    private void startBackPhotoSequence() {
        takeBackPhoto(0); // Start with photo 0
    }
    
    private void takeFrontPhoto(int photoIndex) {
        if (frontCamera == null || frontImageReader == null || photoIndex >= PHOTOS_PER_CAMERA) {
            return;
        }
        
        try {
            CaptureRequest.Builder captureBuilder = frontCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(frontImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            
            frontCamera.createCaptureSession(Arrays.asList(frontImageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), null, backgroundHandler);
                            
                            // Schedule next photo
                            if (photoIndex + 1 < PHOTOS_PER_CAMERA) {
                                backgroundHandler.postDelayed(() -> takeFrontPhoto(photoIndex + 1), PHOTO_INTERVAL_MS);
                            }
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error capturing front photo " + photoIndex, e);
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Front camera capture session configuration failed");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up front photo capture", e);
        }
    }
    
    private void takeBackPhoto(int photoIndex) {
        if (backCamera == null || backImageReader == null || photoIndex >= PHOTOS_PER_CAMERA) {
            return;
        }
        
        try {
            CaptureRequest.Builder captureBuilder = backCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(backImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            
            backCamera.createCaptureSession(Arrays.asList(backImageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), null, backgroundHandler);
                            
                            // Schedule next photo
                            if (photoIndex + 1 < PHOTOS_PER_CAMERA) {
                                backgroundHandler.postDelayed(() -> takeBackPhoto(photoIndex + 1), PHOTO_INTERVAL_MS);
                            }
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error capturing back photo " + photoIndex, e);
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Back camera capture session configuration failed");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up back photo capture", e);
        }
    }
    
    private class FrontCameraImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                String photoPath = saveImageToFile(image, "front");
                if (photoPath != null) {
                    frontPhotoPaths.add(photoPath);
                    Log.d(TAG, "Front photo " + frontPhotoPaths.size() + "/" + PHOTOS_PER_CAMERA + " saved");
                }
                image.close();
                
                frontPhotosRemaining--;
                updateProgress();
                
                if (frontPhotosRemaining <= 0) {
                    closeFrontCamera();
                    checkCaptureCompletion();
                }
            }
        }
    }
    
    private class BackCameraImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                String photoPath = saveImageToFile(image, "back");
                if (photoPath != null) {
                    backPhotoPaths.add(photoPath);
                    Log.d(TAG, "Back photo " + backPhotoPaths.size() + "/" + PHOTOS_PER_CAMERA + " saved");
                }
                image.close();
                
                backPhotosRemaining--;
                updateProgress();
                
                if (backPhotosRemaining <= 0) {
                    closeBackCamera();
                    checkCaptureCompletion();
                }
            }
        }
    }
    
    private void updateProgress() {
        if (captureCallback != null) {
            int totalRemaining = frontPhotosRemaining + backPhotosRemaining;
            captureCallback.onPhotosProgress(frontPhotoPaths.size(), backPhotoPaths.size(), totalRemaining);
        }
    }
    
    private void checkCaptureCompletion() {
        if (frontPhotosRemaining <= 0 && backPhotosRemaining <= 0) {
            isCapturing = false;
            
            if (captureCallback != null) {
                if (!frontPhotoPaths.isEmpty() || !backPhotoPaths.isEmpty()) {
                    captureCallback.onAllPhotosCompleted(frontPhotoPaths, backPhotoPaths);
                } else {
                    captureCallback.onPhotoCaptureError("No photos were successfully captured");
                }
            }
            
            cleanup();
        }
    }
    
    private String saveImageToFile(Image image, String cameraType) {
        try {
            // Create security photos directory
            File photosDir = new File(context.getExternalFilesDir(null), "security_photos");
            if (!photosDir.exists()) {
                photosDir.mkdirs();
            }
            
            // Create filename with timestamp and sequence
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            int sequenceNum = cameraType.equals("front") ? frontPhotoPaths.size() + 1 : backPhotoPaths.size() + 1;
            File photoFile = new File(photosDir, String.format("security_%s_%s_%02d.jpg", cameraType, timestamp, sequenceNum));
            
            // Convert image to byte array
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            // Save to file
            FileOutputStream output = new FileOutputStream(photoFile);
            output.write(bytes);
            output.close();
            
            Log.d(TAG, "Saved " + cameraType + " photo: " + photoFile.getAbsolutePath());
            return photoFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e(TAG, "Error saving " + cameraType + " photo", e);
            return null;
        }
    }
    
    private void closeFrontCamera() {
        if (frontCamera != null) {
            frontCamera.close();
            frontCamera = null;
        }
        if (frontImageReader != null) {
            frontImageReader.close();
            frontImageReader = null;
        }
    }
    
    private void closeBackCamera() {
        if (backCamera != null) {
            backCamera.close();
            backCamera = null;
        }
        if (backImageReader != null) {
            backImageReader.close();
            backImageReader = null;
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
    
    private Size getBestCaptureSize(String cameraId) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG);
            
            // Find a good balance between quality and file size
            for (Size size : sizes) {
                if (size.getWidth() <= 1920 && size.getHeight() <= 1080) {
                    return size;
                }
            }
            
            // Fallback to largest available
            return sizes[0];
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting capture size", e);
            return new Size(640, 480); // Fallback size
        }
    }
    
    public void stopCapture() {
        if (isCapturing) {
            isCapturing = false;
            frontPhotosRemaining = 0;
            backPhotosRemaining = 0;
            cleanup();
        }
    }
    
    private void cleanup() {
        closeFrontCamera();
        closeBackCamera();
    }
    
    public void cleanupResources() {
        stopCapture();
        
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
    
    public boolean isCapturing() {
        return isCapturing;
    }
}

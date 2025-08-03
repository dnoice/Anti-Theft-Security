// File: app/src/main/java/com/antitheft/security/CameraManager.java

package com.antitheft.security;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
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
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class CameraManager {
    private static final String TAG = "AntiTheft_Camera";
    
    private Context context;
    private android.hardware.camera2.CameraManager cameraManager;
    private CameraDevice frontCamera, backCamera;
    private ImageReader frontImageReader, backImageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    private boolean isCapturing = false;
    private CameraCaptureCallback captureCallback;
    
    public interface CameraCaptureCallback {
        void onPhotosCaptured(String frontPhotoPath, String backPhotoPath);
        void onCaptureError(String error);
        void onCaptureProgress(String status);
    }
    
    public CameraManager(Context context) {
        this.context = context;
        this.cameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    public boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    
    public void captureSecurityPhotos(CameraCaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Already capturing photos");
            return;
        }
        
        if (!hasPermissions()) {
            callback.onCaptureError("Camera permissions not granted");
            return;
        }
        
        this.captureCallback = callback;
        isCapturing = true;
        
        Log.i(TAG, "Starting security photo capture");
        callback.onCaptureProgress("Initializing cameras...");
        
        // Start capturing from both cameras simultaneously
        captureFrontPhoto();
        captureBackPhoto();
    }
    
    private void captureFrontPhoto() {
        try {
            String frontCameraId = getFrontCameraId();
            if (frontCameraId == null) {
                Log.w(TAG, "No front camera found");
                onFrontCaptureComplete(null);
                return;
            }
            
            // Setup image reader for front camera
            Size frontSize = getBestCaptureSize(frontCameraId);
            frontImageReader = ImageReader.newInstance(frontSize.getWidth(), frontSize.getHeight(), ImageFormat.JPEG, 1);
            frontImageReader.setOnImageAvailableListener(new FrontCameraImageListener(), backgroundHandler);
            
            // Open front camera
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(frontCameraId, new FrontCameraStateCallback(), backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up front camera", e);
            onFrontCaptureComplete(null);
        }
    }
    
    private void captureBackPhoto() {
        try {
            String backCameraId = getBackCameraId();
            if (backCameraId == null) {
                Log.w(TAG, "No back camera found");
                onBackCaptureComplete(null);
                return;
            }
            
            // Setup image reader for back camera
            Size backSize = getBestCaptureSize(backCameraId);
            backImageReader = ImageReader.newInstance(backSize.getWidth(), backSize.getHeight(), ImageFormat.JPEG, 1);
            backImageReader.setOnImageAvailableListener(new BackCameraImageListener(), backgroundHandler);
            
            // Open back camera
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(backCameraId, new BackCameraStateCallback(), backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up back camera", e);
            onBackCaptureComplete(null);
        }
    }
    
    private class FrontCameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            frontCamera = camera;
            captureStillPicture(frontCamera, frontImageReader);
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
            onFrontCaptureComplete(null);
        }
    }
    
    private class BackCameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            backCamera = camera;
            captureStillPicture(backCamera, backImageReader);
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
            onBackCaptureComplete(null);
        }
    }
    
    private void captureStillPicture(CameraDevice camera, ImageReader imageReader) {
        try {
            CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            
            // Auto settings for best quality
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            
            camera.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), null, backgroundHandler);
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error capturing photo", e);
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Camera capture session configuration failed");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error setting up photo capture", e);
        }
    }
    
    private class FrontCameraImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                String photoPath = saveImageToFile(image, "front");
                image.close();
                onFrontCaptureComplete(photoPath);
            }
        }
    }
    
    private class BackCameraImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                String photoPath = saveImageToFile(image, "back");
                image.close();
                onBackCaptureComplete(photoPath);
            }
        }
    }
    
    private String frontPhotoPath = null;
    private String backPhotoPath = null;
    private boolean frontComplete = false;
    private boolean backComplete = false;
    
    private synchronized void onFrontCaptureComplete(String photoPath) {
        frontPhotoPath = photoPath;
        frontComplete = true;
        closeFrontCamera();
        
        if (photoPath != null) {
            Log.d(TAG, "Front photo captured: " + photoPath);
            if (captureCallback != null) {
                captureCallback.onCaptureProgress("Front camera captured");
            }
        } else {
            Log.w(TAG, "Front photo capture failed");
        }
        
        checkCaptureCompletion();
    }
    
    private synchronized void onBackCaptureComplete(String photoPath) {
        backPhotoPath = photoPath;
        backComplete = true;
        closeBackCamera();
        
        if (photoPath != null) {
            Log.d(TAG, "Back photo captured: " + photoPath);
            if (captureCallback != null) {
                captureCallback.onCaptureProgress("Back camera captured");
            }
        } else {
            Log.w(TAG, "Back photo capture failed");
        }
        
        checkCaptureCompletion();
    }
    
    private void checkCaptureCompletion() {
        if (frontComplete && backComplete) {
            isCapturing = false;
            
            if (captureCallback != null) {
                if (frontPhotoPath != null || backPhotoPath != null) {
                    captureCallback.onPhotosCaptured(frontPhotoPath, backPhotoPath);
                } else {
                    captureCallback.onCaptureError("Failed to capture any photos");
                }
            }
            
            // Reset state for next capture
            resetCaptureState();
        }
    }
    
    private void resetCaptureState() {
        frontPhotoPath = null;
        backPhotoPath = null;
        frontComplete = false;
        backComplete = false;
    }
    
    private String saveImageToFile(Image image, String cameraType) {
        try {
            // Create security photos directory
            File photosDir = new File(context.getExternalFilesDir(null), "security_photos");
            if (!photosDir.exists()) {
                photosDir.mkdirs();
            }
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(photosDir, String.format("security_%s_%s.jpg", cameraType, timestamp));
            
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
            
            // Find a good balance between quality and file size (prefer 1920x1080 or similar)
            for (Size size : sizes) {
                if (size.getWidth() <= 1920 && size.getHeight() <= 1080 && 
                    size.getWidth() >= 1280 && size.getHeight() >= 720) {
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
    
    // Quick single photo capture for immediate use
    public void captureQuickPhoto(String cameraType, QuickCaptureCallback callback) {
        if (isCapturing) {
            callback.onError("Camera busy");
            return;
        }
        
        try {
            String cameraId = cameraType.equals("front") ? getFrontCameraId() : getBackCameraId();
            if (cameraId == null) {
                callback.onError("Camera not available");
                return;
            }
            
            Size size = getBestCaptureSize(cameraId);
            ImageReader imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    String photoPath = saveImageToFile(image, cameraType + "_quick");
                    image.close();
                    reader.close();
                    callback.onPhotoCapture(photoPath);
                } else {
                    reader.close();
                    callback.onError("Failed to capture image");
                }
            }, backgroundHandler);
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        captureStillPicture(camera, imageReader);
                        camera.close();
                    }
                    
                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        callback.onError("Camera disconnected");
                    }
                    
                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        camera.close();
                        callback.onError("Camera error: " + error);
                    }
                }, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error in quick capture", e);
            callback.onError("Camera access error");
        }
    }
    
    public interface QuickCaptureCallback {
        void onPhotoCapture(String photoPath);
        void onError(String error);
    }
    
    // Get all security photos
    public File[] getSecurityPhotos() {
        File photosDir = new File(context.getExternalFilesDir(null), "security_photos");
        if (photosDir.exists()) {
            return photosDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg"));
        }
        return new File[0];
    }
    
    // Cleanup old photos
    public void cleanupOldPhotos(int keepCount) {
        File[] photos = getSecurityPhotos();
        if (photos != null && photos.length > keepCount) {
            // Sort by last modified date
            java.util.Arrays.sort(photos, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            
            // Delete oldest photos
            for (int i = keepCount; i < photos.length; i++) {
                if (photos[i].delete()) {
                    Log.d(TAG, "Deleted old photo: " + photos[i].getName());
                }
            }
        }
    }
    
    public void cleanup() {
        isCapturing = false;
        closeFrontCamera();
        closeBackCamera();
        
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

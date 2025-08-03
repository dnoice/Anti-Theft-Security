// File: app/src/main/java/com/antitheft/security/FullscreenImageActivity.java

package com.antitheft.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FullscreenImageActivity extends Activity {
    private static final String TAG = "AntiTheft_FullscreenImage";
    
    // UI Components
    private ImageView imageViewFullscreen, btnBack, btnMenu, btnPrevious, btnNext;
    private ImageView btnZoomIn, btnZoomOut;
    private TextView txtImageTitle, txtImageInfo, txtSessionInfo, txtEvidenceDetails, txtMetadata, txtError;
    private Button btnShare, btnDelete, btnZoom, btnRetry;
    private LinearLayout topOverlay, bottomOverlay, layoutZoomControls, layoutError;
    private ScrollView metadataPanel;
    private ProgressBar progressLoading;
    
    // Image data
    private String filePath;
    private String fileName;
    private String sessionId;
    private String triggerReason;
    private File imageFile;
    
    // Zoom and pan functionality
    private ScaleGestureDetector scaleDetector;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private boolean isZoomMode = false;
    
    // UI visibility
    private boolean overlaysVisible = true;
    private Handler hideHandler = new Handler();
    private Runnable hideRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);
        
        initializeViews();
        processIntentData();
        setupEventListeners();
        setupZoomFunctionality();
        loadImage();
        
        Log.i(TAG, "Fullscreen image viewer opened: " + fileName);
    }

    private void initializeViews() {
        imageViewFullscreen = findViewById(R.id.imageViewFullscreen);
        btnBack = findViewById(R.id.btnBack);
        btnMenu = findViewById(R.id.btnMenu);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        
        txtImageTitle = findViewById(R.id.txtImageTitle);
        txtImageInfo = findViewById(R.id.txtImageInfo);
        txtSessionInfo = findViewById(R.id.txtSessionInfo);
        txtEvidenceDetails = findViewById(R.id.txtEvidenceDetails);
        txtMetadata = findViewById(R.id.txtMetadata);
        txtError = findViewById(R.id.txtError);
        
        btnShare = findViewById(R.id.btnShare);
        btnDelete = findViewById(R.id.btnDelete);
        btnZoom = findViewById(R.id.btnZoom);
        btnRetry = findViewById(R.id.btnRetry);
        
        topOverlay = findViewById(R.id.topOverlay);
        bottomOverlay = findViewById(R.id.bottomOverlay);
        layoutZoomControls = findViewById(R.id.layoutZoomControls);
        layoutError = findViewById(R.id.layoutError);
        metadataPanel = findViewById(R.id.metadataPanel);
        progressLoading = findViewById(R.id.progressLoading);
    }

    private void processIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            filePath = intent.getStringExtra("file_path");
            fileName = intent.getStringExtra("file_name");
            sessionId = intent.getStringExtra("session_id");
            triggerReason = intent.getStringExtra("trigger_reason");
            
            if (filePath == null) {
                showError("No image file specified");
                return;
            }
            
            imageFile = new File(filePath);
            if (!imageFile.exists()) {
                showError("Image file not found");
                return;
            }
            
            updateUI();
        }
    }

    private void setupEventListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnMenu.setOnClickListener(v -> showMenu());
        btnShare.setOnClickListener(v -> shareImage());
        btnDelete.setOnClickListener(v -> deleteImage());
        btnZoom.setOnClickListener(v -> toggleZoomMode());
        btnRetry.setOnClickListener(v -> loadImage());
        
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnZoomOut.setOnClickListener(v -> zoomOut());
        
        // Toggle overlays on image tap
        imageViewFullscreen.setOnClickListener(v -> toggleOverlays());
        
        // Auto-hide overlays after delay
        setupAutoHide();
    }

    private void setupZoomFunctionality() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 5.0f)); // Limit zoom range
                
                matrix.setScale(scaleFactor, scaleFactor);
                imageViewFullscreen.setImageMatrix(matrix);
                imageViewFullscreen.setScaleType(ImageView.ScaleType.MATRIX);
                
                return true;
            }
        });
        
        imageViewFullscreen.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Cancel auto-hide when user interacts
                cancelAutoHide();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Restart auto-hide
                startAutoHide();
            }
            
            return true;
        });
    }

    private void updateUI() {
        if (fileName != null) {
            txtImageTitle.setText(fileName);
        }
        
        if (imageFile != null) {
            // File info
            long fileSize = imageFile.length();
            String sizeText = formatFileSize(fileSize);
            String dateText = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(imageFile.lastModified()));
            txtImageInfo.setText(dateText + " • " + sizeText);
        }
        
        if (triggerReason != null) {
            txtSessionInfo.setText("📱 " + triggerReason);
        }
        
        updateEvidenceDetails();
    }

    private void updateEvidenceDetails() {
        StringBuilder details = new StringBuilder();
        
        if (sessionId != null) {
            details.append("Session ID: ").append(sessionId).append("\n");
        }
        if (triggerReason != null) {
            details.append("Trigger: ").append(triggerReason).append("\n");
        }
        
        // Determine camera type from filename
        String cameraType = "Unknown";
        if (fileName != null) {
            if (fileName.contains("front")) {
                cameraType = "Front Camera";
            } else if (fileName.contains("back")) {
                cameraType = "Back Camera";
            } else if (fileName.contains("screenshot")) {
                cameraType = "Screenshot";
            }
        }
        details.append("Source: ").append(cameraType).append("\n");
        
        if (imageFile != null) {
            details.append("File Size: ").append(formatFileSize(imageFile.length())).append("\n");
            details.append("Path: ").append(imageFile.getAbsolutePath());
        }
        
        txtEvidenceDetails.setText(details.toString());
    }

    private void loadImage() {
        if (imageFile == null || !imageFile.exists()) {
            showError("Image file not found");
            return;
        }
        
        progressLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        
        Glide.with(this)
            .load(imageFile)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(imageViewFullscreen);
        
        progressLoading.setVisibility(View.GONE);
        
        // Load metadata
        loadImageMetadata();
    }

    private void loadImageMetadata() {
        try {
            StringBuilder metadata = new StringBuilder();
            metadata.append("File Information:\n");
            metadata.append("Name: ").append(imageFile.getName()).append("\n");
            metadata.append("Size: ").append(formatFileSize(imageFile.length())).append("\n");
            metadata.append("Modified: ").append(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(imageFile.lastModified()))
            ).append("\n");
            metadata.append("Path: ").append(imageFile.getAbsolutePath()).append("\n\n");
            
            metadata.append("Security Information:\n");
            if (sessionId != null) {
                metadata.append("Session ID: ").append(sessionId).append("\n");
            }
            if (triggerReason != null) {
                metadata.append("Trigger Reason: ").append(triggerReason).append("\n");
            }
            metadata.append("Evidence Type: Image/Photo\n");
            metadata.append("Capture Method: Automatic\n");
            
            txtMetadata.setText(metadata.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error loading metadata", e);
            txtMetadata.setText("Error loading metadata");
        }
    }

    private void showError(String message) {
        txtError.setText(message);
        layoutError.setVisibility(View.VISIBLE);
        imageViewFullscreen.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
    }

    private void shareImage() {
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Uri imageUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Security Evidence");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Security evidence from Anti-Theft app\nTrigger: " + triggerReason);
            
            startActivity(Intent.createChooser(shareIntent, "Share Evidence"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing image", e);
            Toast.makeText(this, "Error sharing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteImage() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Evidence")
            .setMessage("Are you sure you want to delete this evidence file? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                if (imageFile != null && imageFile.exists()) {
                    if (imageFile.delete()) {
                        Toast.makeText(this, "Evidence deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void toggleZoomMode() {
        isZoomMode = !isZoomMode;
        
        if (isZoomMode) {
            layoutZoomControls.setVisibility(View.VISIBLE);
            btnZoom.setText("Exit Zoom");
            imageViewFullscreen.setScaleType(ImageView.ScaleType.MATRIX);
        } else {
            layoutZoomControls.setVisibility(View.GONE);
            btnZoom.setText("Zoom");
            resetZoom();
        }
    }

    private void zoomIn() {
        scaleFactor = Math.min(scaleFactor * 1.2f, 5.0f);
        matrix.setScale(scaleFactor, scaleFactor);
        imageViewFullscreen.setImageMatrix(matrix);
    }

    private void zoomOut() {
        scaleFactor = Math.max(scaleFactor / 1.2f, 0.5f);
        matrix.setScale(scaleFactor, scaleFactor);
        imageViewFullscreen.setImageMatrix(matrix);
    }

    private void resetZoom() {
        scaleFactor = 1.0f;
        matrix.reset();
        imageViewFullscreen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageViewFullscreen.setImageMatrix(matrix);
    }

    private void showMenu() {
        String[] options = {"Show Metadata", "Copy File Path", "Open in Gallery", "Export Evidence"};
        
        new AlertDialog.Builder(this)
            .setTitle("Image Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        toggleMetadataPanel();
                        break;
                    case 1:
                        copyFilePathToClipboard();
                        break;
                    case 2:
                        openInGallery();
                        break;
                    case 3:
                        exportEvidence();
                        break;
                }
            })
            .show();
    }

    private void toggleMetadataPanel() {
        if (metadataPanel.getVisibility() == View.VISIBLE) {
            metadataPanel.setVisibility(View.GONE);
        } else {
            metadataPanel.setVisibility(View.VISIBLE);
        }
    }

    private void copyFilePathToClipboard() {
        if (imageFile != null) {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("File Path", imageFile.getAbsolutePath());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "File path copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri imageUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile
            );
            intent.setDataAndType(imageUri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening in gallery", e);
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportEvidence() {
        // Create a detailed evidence report
        StringBuilder report = new StringBuilder();
        report.append("SECURITY EVIDENCE REPORT\n");
        report.append("========================\n\n");
        report.append("Image File: ").append(fileName).append("\n");
        report.append("Session ID: ").append(sessionId != null ? sessionId : "Unknown").append("\n");
        report.append("Trigger: ").append(triggerReason != null ? triggerReason : "Unknown").append("\n");
        report.append("Timestamp: ").append(
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(imageFile.lastModified()))
        ).append("\n");
        report.append("File Size: ").append(formatFileSize(imageFile.length())).append("\n");
        report.append("Device: ").append(android.os.Build.MODEL).append("\n");
        report.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n\n");
        report.append("This evidence was automatically collected by Anti-Theft Security app.\n");
        
        // Copy to clipboard
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Evidence Report", report.toString());
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Evidence report copied to clipboard", Toast.LENGTH_LONG).show();
    }

    private void toggleOverlays() {
        overlaysVisible = !overlaysVisible;
        
        int visibility = overlaysVisible ? View.VISIBLE : View.GONE;
        topOverlay.setVisibility(visibility);
        bottomOverlay.setVisibility(visibility);
        
        if (overlaysVisible) {
            startAutoHide();
        } else {
            cancelAutoHide();
        }
    }

    private void setupAutoHide() {
        hideRunnable = () -> {
            if (overlaysVisible) {
                toggleOverlays();
            }
        };
        startAutoHide();
    }

    private void startAutoHide() {
        cancelAutoHide();
        hideHandler.postDelayed(hideRunnable, 3000); // Hide after 3 seconds
    }

    private void cancelAutoHide() {
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAutoHide();
    }

    @Override
    public void onBackPressed() {
        if (metadataPanel.getVisibility() == View.VISIBLE) {
            metadataPanel.setVisibility(View.GONE);
        } else if (isZoomMode) {
            toggleZoomMode();
        } else {
            super.onBackPressed();
        }
    }
}

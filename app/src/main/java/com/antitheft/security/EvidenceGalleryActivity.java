// File: app/src/main/java/com/antitheft/security/EvidenceGalleryActivity.java

package com.antitheft.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EvidenceGalleryActivity extends Activity {
    private static final String TAG = "AntiTheft_Gallery";
    
    private EvidenceManager evidenceManager;
    private EmailManager emailManager;
    
    private GridView gridEvidence;
    private TextView txtEvidenceCount, txtStorageUsed, txtNoEvidence;
    private LinearLayout layoutStats;
    private Button btnSelectAll, btnDelete, btnShare, btnClearAll;
    
    private List<EvidenceItem> evidenceItems = new ArrayList<>();
    private EvidenceAdapter evidenceAdapter;
    private List<EvidenceItem> selectedItems = new ArrayList<>();
    
    private boolean isSelectionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evidence_gallery);
        
        initializeManagers();
        initializeViews();
        setupEventListeners();
        loadEvidenceData();
        
        Log.i(TAG, "Evidence gallery opened");
    }

    private void initializeManagers() {
        evidenceManager = new EvidenceManager(this);
        emailManager = new EmailManager(this);
    }

    private void initializeViews() {
        gridEvidence = findViewById(R.id.gridEvidence);
        txtEvidenceCount = findViewById(R.id.txtEvidenceCount);
        txtStorageUsed = findViewById(R.id.txtStorageUsed);
        txtNoEvidence = findViewById(R.id.txtNoEvidence);
        layoutStats = findViewById(R.id.layoutStats);
        
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDelete = findViewById(R.id.btnDelete);
        btnShare = findViewById(R.id.btnShare);
        btnClearAll = findViewById(R.id.btnClearAll);
        
        evidenceAdapter = new EvidenceAdapter();
        gridEvidence.setAdapter(evidenceAdapter);
        
        // Initially hide selection buttons
        hideSelectionButtons();
    }

    private void setupEventListeners() {
        gridEvidence.setOnItemClickListener((parent, view, position, id) -> {
            if (isSelectionMode) {
                toggleItemSelection(position);
            } else {
                openFullscreenView(position);
            }
        });
        
        gridEvidence.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!isSelectionMode) {
                enterSelectionMode();
                toggleItemSelection(position);
            }
            return true;
        });
        
        btnSelectAll.setOnClickListener(v -> selectAllItems());
        btnDelete.setOnClickListener(v -> deleteSelectedItems());
        btnShare.setOnClickListener(v -> shareSelectedItems());
        btnClearAll.setOnClickListener(v -> clearAllEvidence());
    }

    private void loadEvidenceData() {
        evidenceItems.clear();
        
        // Load evidence sessions
        List<EvidenceManager.EvidenceSession> sessions = evidenceManager.getAllEvidenceSessions();
        for (EvidenceManager.EvidenceSession session : sessions) {
            for (String filePath : session.evidencePaths) {
                File file = new File(filePath);
                if (file.exists()) {
                    EvidenceItem item = new EvidenceItem();
                    item.filePath = filePath;
                    item.fileName = file.getName();
                    item.fileSize = file.length();
                    item.dateModified = file.lastModified();
                    item.sessionId = session.sessionId;
                    item.triggerReason = session.triggerReason;
                    item.fileType = getFileType(filePath);
                    evidenceItems.add(item);
                }
            }
        }
        
        // Sort by date (newest first)
        evidenceItems.sort((item1, item2) -> Long.compare(item2.dateModified, item1.dateModified));
        
        updateUI();
    }

    private EvidenceFileType getFileType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.contains("photo")) {
            return EvidenceFileType.PHOTO;
        } else if (lower.endsWith(".mp4") || lower.contains("video")) {
            return EvidenceFileType.VIDEO;
        } else if (lower.endsWith(".png") || lower.contains("screenshot")) {
            return EvidenceFileType.SCREENSHOT;
        }
        return EvidenceFileType.OTHER;
    }

    private void updateUI() {
        evidenceAdapter.notifyDataSetChanged();
        
        if (evidenceItems.isEmpty()) {
            gridEvidence.setVisibility(View.GONE);
            layoutStats.setVisibility(View.GONE);
            txtNoEvidence.setVisibility(View.VISIBLE);
        } else {
            gridEvidence.setVisibility(View.VISIBLE);
            layoutStats.setVisibility(View.VISIBLE);
            txtNoEvidence.setVisibility(View.GONE);
            
            // Update statistics
            txtEvidenceCount.setText("Evidence Files: " + evidenceItems.size());
            
            long totalSize = 0;
            for (EvidenceItem item : evidenceItems) {
                totalSize += item.fileSize;
            }
            txtStorageUsed.setText("Storage Used: " + formatFileSize(totalSize));
        }
        
        updateSelectionUI();
    }

    private void enterSelectionMode() {
        isSelectionMode = true;
        showSelectionButtons();
        updateSelectionUI();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedItems.clear();
        hideSelectionButtons();
        evidenceAdapter.notifyDataSetChanged();
    }

    private void toggleItemSelection(int position) {
        EvidenceItem item = evidenceItems.get(position);
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        
        if (selectedItems.isEmpty()) {
            exitSelectionMode();
        } else {
            updateSelectionUI();
        }
        
        evidenceAdapter.notifyDataSetChanged();
    }

    private void selectAllItems() {
        selectedItems.clear();
        selectedItems.addAll(evidenceItems);
        updateSelectionUI();
        evidenceAdapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        if (isSelectionMode) {
            btnSelectAll.setText(selectedItems.size() == evidenceItems.size() ? "Deselect All" : "Select All");
            btnDelete.setText("Delete (" + selectedItems.size() + ")");
            btnShare.setText("Share (" + selectedItems.size() + ")");
            
            // Enable/disable buttons based on selection
            btnDelete.setEnabled(!selectedItems.isEmpty());
            btnShare.setEnabled(!selectedItems.isEmpty());
        }
    }

    private void showSelectionButtons() {
        btnSelectAll.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.VISIBLE);
    }

    private void hideSelectionButtons() {
        btnSelectAll.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        btnShare.setVisibility(View.GONE);
    }

    private void openFullscreenView(int position) {
        EvidenceItem item = evidenceItems.get(position);
        
        Intent intent = new Intent(this, FullscreenImageActivity.class);
        intent.putExtra("file_path", item.filePath);
        intent.putExtra("file_name", item.fileName);
        intent.putExtra("session_id", item.sessionId);
        intent.putExtra("trigger_reason", item.triggerReason);
        startActivity(intent);
    }

    private void deleteSelectedItems() {
        if (selectedItems.isEmpty()) return;
        
        new AlertDialog.Builder(this)
            .setTitle("Delete Evidence")
            .setMessage("Are you sure you want to delete " + selectedItems.size() + " evidence file(s)? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                int deletedCount = 0;
                
                for (EvidenceItem item : selectedItems) {
                    File file = new File(item.filePath);
                    if (file.exists() && file.delete()) {
                        deletedCount++;
                        evidenceItems.remove(item);
                    }
                }
                
                Toast.makeText(this, deletedCount + " files deleted", Toast.LENGTH_SHORT).show();
                exitSelectionMode();
                updateUI();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void shareSelectedItems() {
        if (selectedItems.isEmpty()) return;
        
        try {
            ArrayList<Uri> fileUris = new ArrayList<>();
            
            for (EvidenceItem item : selectedItems) {
                File file = new File(item.filePath);
                if (file.exists()) {
                    Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        file
                    );
                    fileUris.add(uri);
                }
            }
            
            if (fileUris.isEmpty()) {
                Toast.makeText(this, "No files to share", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent shareIntent = new Intent();
            if (fileUris.size() == 1) {
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUris.get(0));
            } else {
                shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
            }
            
            shareIntent.setType("*/*");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Security Evidence");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Security evidence files from Anti-Theft app");
            
            startActivity(Intent.createChooser(shareIntent, "Share Evidence"));
            
        } catch (Exception e) {
            Log.e(TAG, "Error sharing files", e);
            Toast.makeText(this, "Error sharing files", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllEvidence() {
        if (evidenceItems.isEmpty()) return;
        
        new AlertDialog.Builder(this)
            .setTitle("Clear All Evidence")
            .setMessage("Are you sure you want to delete ALL evidence files? This will permanently remove " + 
                       evidenceItems.size() + " files and cannot be undone.")
            .setPositiveButton("Clear All", (dialog, which) -> {
                int deletedCount = 0;
                
                for (EvidenceItem item : evidenceItems) {
                    File file = new File(item.filePath);
                    if (file.exists() && file.delete()) {
                        deletedCount++;
                    }
                }
                
                evidenceItems.clear();
                selectedItems.clear();
                exitSelectionMode();
                updateUI();
                
                Toast.makeText(this, "All evidence cleared (" + deletedCount + " files)", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Evidence adapter for GridView
    private class EvidenceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return evidenceItems.size();
        }

        @Override
        public Object getItem(int position) {
            return evidenceItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = LayoutInflater.from(EvidenceGalleryActivity.this)
                    .inflate(R.layout.item_evidence, parent, false);
                holder = new ViewHolder();
                holder.imagePreview = convertView.findViewById(R.id.imagePreview);
                holder.txtFileName = convertView.findViewById(R.id.txtFileName);
                holder.txtFileInfo = convertView.findViewById(R.id.txtFileInfo);
                holder.iconFileType = convertView.findViewById(R.id.iconFileType);
                holder.selectionOverlay = convertView.findViewById(R.id.selectionOverlay);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            EvidenceItem item = evidenceItems.get(position);
            
            // Set file name
            holder.txtFileName.setText(item.fileName);
            
            // Set file info
            String dateStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(new Date(item.dateModified));
            holder.txtFileInfo.setText(dateStr + " • " + formatFileSize(item.fileSize));
            
            // Set file type icon
            switch (item.fileType) {
                case PHOTO:
                    holder.iconFileType.setImageResource(R.drawable.ic_camera);
                    break;
                case VIDEO:
                    holder.iconFileType.setImageResource(R.drawable.ic_video);
                    break;
                case SCREENSHOT:
                    holder.iconFileType.setImageResource(R.drawable.ic_screenshot);
                    break;
                default:
                    holder.iconFileType.setImageResource(R.drawable.ic_file);
                    break;
            }
            
            // Load preview image
            if (item.fileType == EvidenceFileType.PHOTO || item.fileType == EvidenceFileType.SCREENSHOT) {
                Glide.with(EvidenceGalleryActivity.this)
                    .load(new File(item.filePath))
                    .thumbnail(0.1f)
                    .centerCrop()
                    .into(holder.imagePreview);
            } else if (item.fileType == EvidenceFileType.VIDEO) {
                // For videos, show a video thumbnail
                Glide.with(EvidenceGalleryActivity.this)
                    .load(Uri.fromFile(new File(item.filePath)))
                    .thumbnail(0.1f)
                    .centerCrop()
                    .into(holder.imagePreview);
            } else {
                holder.imagePreview.setImageResource(R.drawable.ic_file_large);
            }
            
            // Show selection overlay
            boolean isSelected = selectedItems.contains(item);
            holder.selectionOverlay.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            holder.selectionOverlay.setBackgroundColor(
                isSelected ? getColor(R.color.selection_active) : getColor(R.color.selection_inactive)
            );
            
            return convertView;
        }
    }

    private static class ViewHolder {
        ImageView imagePreview;
        ImageView iconFileType;
        TextView txtFileName;
        TextView txtFileInfo;
        View selectionOverlay;
    }

    // Evidence item data class
    private static class EvidenceItem {
        String filePath;
        String fileName;
        long fileSize;
        long dateModified;
        String sessionId;
        String triggerReason;
        EvidenceFileType fileType;
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            EvidenceItem that = (EvidenceItem) obj;
            return filePath.equals(that.filePath);
        }
        
        @Override
        public int hashCode() {
            return filePath.hashCode();
        }
    }

    private enum EvidenceFileType {
        PHOTO, VIDEO, SCREENSHOT, OTHER
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.evidence_gallery_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                loadEvidenceData();
                Toast.makeText(this, "Evidence refreshed", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, EvidenceSettingsActivity.class));
                return true;
            case R.id.menu_cleanup:
                showCleanupDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showCleanupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Cleanup Evidence")
            .setMessage("Remove evidence files older than:")
            .setPositiveButton("7 Days", (dialog, which) -> cleanupOldEvidence(7))
            .setNeutralButton("30 Days", (dialog, which) -> cleanupOldEvidence(30))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void cleanupOldEvidence(int days) {
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        int deletedCount = 0;
        
        List<EvidenceItem> toDelete = new ArrayList<>();
        for (EvidenceItem item : evidenceItems) {
            if (item.dateModified < cutoffTime) {
                File file = new File(item.filePath);
                if (file.exists() && file.delete()) {
                    deletedCount++;
                    toDelete.add(item);
                }
            }
        }
        
        evidenceItems.removeAll(toDelete);
        updateUI();
        
        Toast.makeText(this, "Cleaned up " + deletedCount + " old files", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (evidenceManager != null) {
            evidenceManager.cleanup();
        }
        if (emailManager != null) {
            emailManager.cleanup();
        }
    }
}

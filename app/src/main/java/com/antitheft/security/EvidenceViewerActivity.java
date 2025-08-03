// File Path: app/src/main/java/com/antitheft/security/EvidenceViewerActivity.java
package com.antitheft.security;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.antitheft.security.utils.EvidenceCollector;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EvidenceViewerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChipGroup chipGroupFilters;
    private MaterialButton btnClearEvidence, btnExportEvidence;
    private MaterialTextView txtEvidenceCount;
    
    private EvidenceAdapter adapter;
    private EvidenceCollector evidenceCollector;
    private List<EvidenceItem> evidenceItems;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evidence_viewer);

        initializeViews();
        setupRecyclerView();
        setupFilters();
        loadEvidence();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_evidence);
        chipGroupFilters = findViewById(R.id.chip_group_filters);
        btnClearEvidence = findViewById(R.id.btn_clear_evidence);
        btnExportEvidence = findViewById(R.id.btn_export_evidence);
        txtEvidenceCount = findViewById(R.id.txt_evidence_count);
        
        evidenceCollector = new EvidenceCollector(this);
        evidenceItems = new ArrayList<>();
        
        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Evidence Collection");
        }
        
        btnClearEvidence.setOnClickListener(v -> showClearEvidenceDialog());
        btnExportEvidence.setOnClickListener(v -> exportEvidence());
    }

    private void setupRecyclerView() {
        adapter = new EvidenceAdapter(evidenceItems);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        String[] filters = {"ALL", "LOCATION", "PHOTO", "AUDIO", "NETWORK", "DEVICE", "SECURITY"};
        
        for (String filter : filters) {
            Chip chip = new Chip(this);
            chip.setText(filter);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Uncheck other chips
                    for (int i = 0; i < chipGroupFilters.getChildCount(); i++) {
                        Chip otherChip = (Chip) chipGroupFilters.getChildAt(i);
                        if (otherChip != chip) {
                            otherChip.setChecked(false);
                        }
                    }
                    currentFilter = filter;
                    filterEvidence();
                }
            });
            
            if (filter.equals("ALL")) {
                chip.setChecked(true);
            }
            
            chipGroupFilters.addView(chip);
        }
    }

    private void loadEvidence() {
        evidenceItems.clear();
        
        // Load location evidence
        loadEvidenceType("location", "LOCATION");
        
        // Load device state evidence
        loadEvidenceType("device_state", "DEVICE");
        
        // Load network info evidence
        loadEvidenceType("network_info", "NETWORK");
        
        // Load security events
        loadEvidenceType("security_events", "SECURITY");
        
        // Load system events
        loadEvidenceType("system_events", "SYSTEM");
        
        // Load evidence metadata (photos, audio, etc.)
        loadEvidenceType("evidence_metadata", "METADATA");
        
        // Load photo and audio files directly
        loadFileEvidence();
        
        // Sort by timestamp (newest first)
        evidenceItems.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        
        adapter.notifyDataSetChanged();
        updateEvidenceCount();
    }

    private void loadEvidenceType(String type, String category) {
        JSONArray data = evidenceCollector.getEvidenceData(type);
        for (int i = 0; i < data.length(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                EvidenceItem evidenceItem = new EvidenceItem();
                evidenceItem.type = category;
                evidenceItem.data = item.toString(2);
                evidenceItem.timestamp = item.optLong("timestamp", System.currentTimeMillis());
                evidenceItem.title = getEvidenceTitle(category, item);
                evidenceItems.add(evidenceItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFileEvidence() {
        // Load photo evidence
        File photoDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Evidence");
        if (photoDir.exists()) {
            File[] photos = photoDir.listFiles((dir, name) -> name.endsWith(".jpg"));
            if (photos != null) {
                for (File photo : photos) {
                    EvidenceItem item = new EvidenceItem();
                    item.type = "PHOTO";
                    item.title = "Photo Evidence";
                    item.filePath = photo.getAbsolutePath();
                    item.timestamp = photo.lastModified();
                    item.data = "Photo captured at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(photo.lastModified()));
                    evidenceItems.add(item);
                }
            }
        }
        
        // Load audio evidence
        File audioDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Evidence");
        if (audioDir.exists()) {
            File[] audioFiles = audioDir.listFiles((dir, name) -> name.endsWith(".3gp"));
            if (audioFiles != null) {
                for (File audio : audioFiles) {
                    EvidenceItem item = new EvidenceItem();
                    item.type = "AUDIO";
                    item.title = "Audio Evidence";
                    item.filePath = audio.getAbsolutePath();
                    item.timestamp = audio.lastModified();
                    item.data = "Audio recorded at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(audio.lastModified()));
                    evidenceItems.add(item);
                }
            }
        }
    }

    private String getEvidenceTitle(String category, JSONObject item) {
        try {
            switch (category) {
                case "LOCATION":
                    return "Location: " + String.format(Locale.getDefault(), "%.4f, %.4f", 
                        item.getDouble("latitude"), item.getDouble("longitude"));
                case "DEVICE":
                    return "Device: " + item.optString("device_model", "Unknown");
                case "NETWORK":
                    return "Network: " + item.optString("wifi_ssid", "No WiFi");
                case "SECURITY":
                    return "Security: " + item.optString("security_event", "Event");
                case "SYSTEM":
                    return "System: " + item.optString("event_type", "Event");
                default:
                    return category + " Evidence";
            }
        } catch (JSONException e) {
            return category + " Evidence";
        }
    }

    private void filterEvidence() {
        adapter.filter(currentFilter);
        updateEvidenceCount();
    }

    private void updateEvidenceCount() {
        int totalCount = evidenceItems.size();
        int filteredCount = adapter.getFilteredCount();
        
        if (currentFilter.equals("ALL")) {
            txtEvidenceCount.setText(String.format(Locale.getDefault(), "%d evidence items", totalCount));
        } else {
            txtEvidenceCount.setText(String.format(Locale.getDefault(), "%d of %d evidence items", filteredCount, totalCount));
        }
    }

    private void showClearEvidenceDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Clear All Evidence")
            .setMessage("Are you sure you want to permanently delete all collected evidence? This action cannot be undone.")
            .setPositiveButton("Delete All", (dialog, which) -> {
                evidenceCollector.clearAllEvidence();
                evidenceItems.clear();
                adapter.notifyDataSetChanged();
                updateEvidenceCount();
                Toast.makeText(this, "All evidence cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportEvidence() {
        // Create a summary of all evidence for export
        StringBuilder export = new StringBuilder();
        export.append("ANTI-THEFT SECURITY - EVIDENCE REPORT\n");
        export.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
        
        for (EvidenceItem item : evidenceItems) {
            export.append("=== ").append(item.title).append(" ===\n");
            export.append("Type: ").append(item.type).append("\n");
            export.append("Timestamp: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(item.timestamp))).append("\n");
            if (item.filePath != null) {
                export.append("File: ").append(item.filePath).append("\n");
            }
            export.append("Data:\n").append(item.data).append("\n\n");
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, export.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Anti-Theft Evidence Report");
        startActivity(Intent.createChooser(shareIntent, "Export Evidence"));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Evidence Item Model
    private static class EvidenceItem {
        String type;
        String title;
        String data;
        String filePath;
        long timestamp;
    }

    // Evidence Adapter
    private class EvidenceAdapter extends RecyclerView.Adapter<EvidenceAdapter.EvidenceViewHolder> {
        
        private List<EvidenceItem> allItems;
        private List<EvidenceItem> filteredItems;

        public EvidenceAdapter(List<EvidenceItem> items) {
            this.allItems = items;
            this.filteredItems = new ArrayList<>(items);
        }

        @Override
        public EvidenceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evidence, parent, false);
            return new EvidenceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(EvidenceViewHolder holder, int position) {
            EvidenceItem item = filteredItems.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        public void filter(String category) {
            filteredItems.clear();
            if (category.equals("ALL")) {
                filteredItems.addAll(allItems);
            } else {
                for (EvidenceItem item : allItems) {
                    if (item.type.equals(category)) {
                        filteredItems.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        public int getFilteredCount() {
            return filteredItems.size();
        }

        class EvidenceViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView txtTitle, txtTimestamp, txtData;
            ImageView imgThumbnail;
            MaterialButton btnView;

            public EvidenceViewHolder(View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txt_evidence_title);
                txtTimestamp = itemView.findViewById(R.id.txt_evidence_timestamp);
                txtData = itemView.findViewById(R.id.txt_evidence_data);
                imgThumbnail = itemView.findViewById(R.id.img_evidence_thumbnail);
                btnView = itemView.findViewById(R.id.btn_view_evidence);
            }

            public void bind(EvidenceItem item) {
                txtTitle.setText(item.title);
                txtTimestamp.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(item.timestamp)));
                
                // Limit data display length
                String displayData = item.data;
                if (displayData.length() > 200) {
                    displayData = displayData.substring(0, 200) + "...";
                }
                txtData.setText(displayData);

                // Handle thumbnails for photos
                if (item.type.equals("PHOTO") && item.filePath != null) {
                    File photoFile = new File(item.filePath);
                    if (photoFile.exists()) {
                        Bitmap thumbnail = BitmapFactory.decodeFile(item.filePath);
                        if (thumbnail != null) {
                            imgThumbnail.setImageBitmap(thumbnail);
                            imgThumbnail.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    imgThumbnail.setVisibility(View.GONE);
                }

                btnView.setOnClickListener(v -> {
                    if (item.filePath != null) {
                        // Open file with appropriate app
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        Uri uri = Uri.fromFile(new File(item.filePath));
                        if (item.type.equals("PHOTO")) {
                            intent.setDataAndType(uri, "image/*");
                        } else if (item.type.equals("AUDIO")) {
                            intent.setDataAndType(uri, "audio/*");
                        }
                        
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        } else {
                            Toast.makeText(EvidenceViewerActivity.this, "No app available to open this file", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Show detailed data
                        showEvidenceDetails(item);
                    }
                });
            }
        }
    }

    private void showEvidenceDetails(EvidenceItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(item.title);
        builder.setMessage(item.data);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}

// File Path: app/src/main/java/com/antitheft/security/utils/EvidenceCollector.java
package com.antitheft.security.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EvidenceCollector {
    
    private static final String TAG = "EvidenceCollector";
    private Context context;
    private SharedPreferences preferences;
    
    public EvidenceCollector(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE);
    }
    
    public void collectDeviceState() {
        try {
            JSONObject deviceState = new JSONObject();
            
            // Device information
            deviceState.put("device_model", Build.MODEL);
            deviceState.put("device_manufacturer", Build.MANUFACTURER);
            deviceState.put("android_version", Build.VERSION.RELEASE);
            deviceState.put("sdk_version", Build.VERSION.SDK_INT);
            deviceState.put("security_patch", Build.VERSION.SECURITY_PATCH);
            deviceState.put("timestamp", System.currentTimeMillis());
            deviceState.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            
            // Battery information
            // Note: Battery level collection would require additional implementation
            
            // Screen state
            // Note: Screen state detection would require additional implementation
            
            saveEvidenceData("device_state", deviceState);
            Log.d(TAG, "Device state collected");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error collecting device state", e);
        }
    }
    
    public void collectNetworkInfo() {
        try {
            JSONObject networkInfo = new JSONObject();
            
            // WiFi information
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    networkInfo.put("wifi_ssid", wifiInfo.getSSID());
                    networkInfo.put("wifi_bssid", wifiInfo.getBSSID());
                    networkInfo.put("wifi_rssi", wifiInfo.getRssi());
                    networkInfo.put("wifi_link_speed", wifiInfo.getLinkSpeed());
                    networkInfo.put("wifi_frequency", wifiInfo.getFrequency());
                }
            }
            
            // Mobile network information
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                try {
                    networkInfo.put("network_operator", telephonyManager.getNetworkOperatorName());
                    networkInfo.put("network_type", getNetworkTypeString(telephonyManager.getNetworkType()));
                    networkInfo.put("sim_operator", telephonyManager.getSimOperatorName());
                    networkInfo.put("phone_type", getPhoneTypeString(telephonyManager.getPhoneType()));
                    
                    // Cell location (if available)
                    if (telephonyManager.getCellLocation() instanceof GsmCellLocation) {
                        GsmCellLocation cellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
                        if (cellLocation != null) {
                            networkInfo.put("cell_id", cellLocation.getCid());
                            networkInfo.put("lac", cellLocation.getLac());
                        }
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Permission denied for telephony info", e);
                }
            }
            
            // Connection state
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    networkInfo.put("connection_type", activeNetwork.getTypeName());
                    networkInfo.put("is_connected", activeNetwork.isConnected());
                    networkInfo.put("is_roaming", activeNetwork.isRoaming());
                }
            }
            
            networkInfo.put("timestamp", System.currentTimeMillis());
            networkInfo.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            
            saveEvidenceData("network_info", networkInfo);
            Log.d(TAG, "Network information collected");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error collecting network info", e);
        }
    }
    
    public void saveLocationEvidence(Location location) {
        try {
            JSONObject locationData = new JSONObject();
            
            locationData.put("latitude", location.getLatitude());
            locationData.put("longitude", location.getLongitude());
            locationData.put("altitude", location.getAltitude());
            locationData.put("accuracy", location.getAccuracy());
            locationData.put("speed", location.getSpeed());
            locationData.put("bearing", location.getBearing());
            locationData.put("provider", location.getProvider());
            locationData.put("timestamp", location.getTime());
            locationData.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(location.getTime())));
            
            // Create Google Maps link
            String mapsLink = String.format(Locale.getDefault(), 
                "https://maps.google.com/?q=%f,%f", 
                location.getLatitude(), location.getLongitude());
            locationData.put("maps_link", mapsLink);
            
            saveEvidenceData("location", locationData);
            
            // Also save to emergency location for quick access
            preferences.edit()
                .putString("last_known_location", locationData.toString())
                .putLong("last_location_time", System.currentTimeMillis())
                .apply();
                
            Log.d(TAG, "Location evidence saved");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error saving location evidence", e);
        }
    }
    
    public void saveEvidenceMetadata(String type, String filePath, Date timestamp) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("type", type);
            metadata.put("file_path", filePath);
            metadata.put("timestamp", timestamp.getTime());
            metadata.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(timestamp));
            metadata.put("file_size", new File(filePath).length());
            
            saveEvidenceData("evidence_metadata", metadata);
            Log.d(TAG, "Evidence metadata saved for: " + type);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error saving evidence metadata", e);
        }
    }
    
    public void collectSystemEvents(String eventType, String description) {
        try {
            JSONObject event = new JSONObject();
            event.put("event_type", eventType);
            event.put("description", description);
            event.put("timestamp", System.currentTimeMillis());
            event.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            
            saveEvidenceData("system_events", event);
            Log.d(TAG, "System event logged: " + eventType);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error logging system event", e);
        }
    }
    
    public void collectSecurityEvent(String event, String details) {
        try {
            JSONObject securityEvent = new JSONObject();
            securityEvent.put("security_event", event);
            securityEvent.put("details", details);
            securityEvent.put("timestamp", System.currentTimeMillis());
            securityEvent.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            securityEvent.put("wrong_attempts", preferences.getInt("wrong_attempts", 0));
            securityEvent.put("theft_detected", preferences.getBoolean("theft_detected", false));
            
            saveEvidenceData("security_events", securityEvent);
            Log.d(TAG, "Security event logged: " + event);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error logging security event", e);
        }
    }
    
    private void saveEvidenceData(String dataType, JSONObject data) {
        try {
            File evidenceDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Evidence");
            if (!evidenceDir.exists()) {
                evidenceDir.mkdirs();
            }
            
            String filename = dataType + "_log.json";
            File evidenceFile = new File(evidenceDir, filename);
            
            JSONArray evidenceArray;
            if (evidenceFile.exists()) {
                // Read existing data
                String existingData = FileUtils.readFileToString(evidenceFile);
                try {
                    evidenceArray = new JSONArray(existingData);
                } catch (JSONException e) {
                    evidenceArray = new JSONArray();
                }
            } else {
                evidenceArray = new JSONArray();
            }
            
            // Add new data
            evidenceArray.put(data);
            
            // Keep only last 1000 entries to prevent file from growing too large
            if (evidenceArray.length() > 1000) {
                JSONArray trimmedArray = new JSONArray();
                for (int i = evidenceArray.length() - 1000; i < evidenceArray.length(); i++) {
                    trimmedArray.put(evidenceArray.get(i));
                }
                evidenceArray = trimmedArray;
            }
            
            // Write back to file
            FileWriter writer = new FileWriter(evidenceFile);
            writer.write(evidenceArray.toString(2)); // Pretty print with indent
            writer.close();
            
            // Encrypt the file if encryption is enabled
            if (preferences.getBoolean("encrypt_evidence", true)) {
                EncryptionUtils.encryptFile(evidenceFile);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error saving evidence data", e);
        } catch (JSONException e) {
            Log.e(TAG, "Error processing evidence JSON", e);
        }
    }
    
    public JSONArray getEvidenceData(String dataType) {
        try {
            File evidenceDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Evidence");
            String filename = dataType + "_log.json";
            File evidenceFile = new File(evidenceDir, filename);
            
            if (evidenceFile.exists()) {
                // Decrypt if needed
                if (preferences.getBoolean("encrypt_evidence", true)) {
                    EncryptionUtils.decryptFile(evidenceFile);
                }
                
                String data = FileUtils.readFileToString(evidenceFile);
                return new JSONArray(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading evidence data", e);
        }
        
        return new JSONArray();
    }
    
    public void clearAllEvidence() {
        try {
            File evidenceDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Evidence");
            if (evidenceDir.exists()) {
                File[] files = evidenceDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            
            // Clear photo evidence
            File photoEvidenceDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Evidence");
            if (photoEvidenceDir.exists()) {
                File[] photoFiles = photoEvidenceDir.listFiles();
                if (photoFiles != null) {
                    for (File file : photoFiles) {
                        file.delete();
                    }
                }
            }
            
            // Clear audio evidence
            File audioEvidenceDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Evidence");
            if (audioEvidenceDir.exists()) {
                File[] audioFiles = audioEvidenceDir.listFiles();
                if (audioFiles != null) {
                    for (File file : audioFiles) {
                        file.delete();
                    }
                }
            }
            
            Log.d(TAG, "All evidence cleared");
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing evidence", e);
        }
    }
    
    private String getNetworkTypeString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            default: return "UNKNOWN";
        }
    }
    
    private String getPhoneTypeString(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM: return "GSM";
            case TelephonyManager.PHONE_TYPE_CDMA: return "CDMA";
            case TelephonyManager.PHONE_TYPE_SIP: return "SIP";
            default: return "UNKNOWN";
        }
    }
}

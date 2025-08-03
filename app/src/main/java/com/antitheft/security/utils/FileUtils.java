// File Path: app/src/main/java/com/antitheft/security/utils/FileUtils.java
package com.antitheft.security.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    
    private static final String TAG = "FileUtils";
    
    /**
     * Reads the contents of a file as a string
     */
    public static String readFileToString(File file) {
        if (!file.exists() || !file.isFile()) {
            Log.w(TAG, "File does not exist: " + file.getAbsolutePath());
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        BufferedReader reader = null;
        
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            // Remove the last newline if content is not empty
            if (content.length() > 0) {
                content.setLength(content.length() - 1);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + file.getAbsolutePath(), e);
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file reader", e);
                }
            }
        }
        
        return content.toString();
    }
    
    /**
     * Writes a string to a file
     */
    public static boolean writeStringToFile(File file, String content) {
        FileOutputStream fos = null;
        
        try {
            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.flush();
            
            Log.d(TAG, "Successfully wrote to file: " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file: " + file.getAbsolutePath(), e);
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file output stream", e);
                }
            }
        }
    }
    
    /**
     * Copies a file from source to destination
     */
    public static boolean copyFile(File source, File destination) {
        if (!source.exists() || !source.isFile()) {
            Log.w(TAG, "Source file does not exist: " + source.getAbsolutePath());
            return false;
        }
        
        InputStream inputStream = null;
        OutputStream outputStream = null;
        
        try {
            // Create parent directories if they don't exist
            File parentDir = destination.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(destination);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.flush();
            
            Log.d(TAG, "Successfully copied file from " + source.getAbsolutePath() + " to " + destination.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }
    
    /**
     * Deletes a file or directory recursively
     */
    public static boolean deleteRecursively(File fileOrDirectory) {
        if (!fileOrDirectory.exists()) {
            return true;
        }
        
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        
        boolean deleted = fileOrDirectory.delete();
        if (deleted) {
            Log.d(TAG, "Deleted: " + fileOrDirectory.getAbsolutePath());
        } else {
            Log.w(TAG, "Failed to delete: " + fileOrDirectory.getAbsolutePath());
        }
        
        return deleted;
    }
    
    /**
     * Gets the file size in a human-readable format
     */
    public static String getReadableFileSize(long size) {
        if (size <= 0) return "0 B";
        
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    /**
     * Creates a directory if it doesn't exist
     */
    public static boolean createDirectoryIfNotExists(File directory) {
        if (directory.exists()) {
            return directory.isDirectory();
        }
        
        boolean created = directory.mkdirs();
        if (created) {
            Log.d(TAG, "Created directory: " + directory.getAbsolutePath());
        } else {
            Log.w(TAG, "Failed to create directory: " + directory.getAbsolutePath());
        }
        
        return created;
    }
    
    /**
     * Gets the file extension
     */
    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < name.length() - 1) {
            return name.substring(lastDotIndex + 1).toLowerCase();
        }
        
        return "";
    }
    
    /**
     * Checks if a file is a supported image format
     */
    public static boolean isImageFile(File file) {
        String extension = getFileExtension(file);
        return extension.equals("jpg") || extension.equals("jpeg") || 
               extension.equals("png") || extension.equals("gif") || 
               extension.equals("bmp") || extension.equals("webp");
    }
    
    /**
     * Checks if a file is a supported audio format
     */
    public static boolean isAudioFile(File file) {
        String extension = getFileExtension(file);
        return extension.equals("3gp") || extension.equals("mp3") || 
               extension.equals("wav") || extension.equals("m4a") || 
               extension.equals("aac") || extension.equals("ogg");
    }
    
    /**
     * Securely deletes a file by overwriting it with random data before deletion
     */
    public static boolean secureDelete(File file) {
        if (!file.exists() || !file.isFile()) {
            return true;
        }
        
        try {
            long fileSize = file.length();
            FileOutputStream fos = new FileOutputStream(file);
            
            // Overwrite with random data multiple times
            for (int pass = 0; pass < 3; pass++) {
                fos.getChannel().position(0);
                
                for (long written = 0; written < fileSize; written += 8192) {
                    byte[] randomData = new byte[(int) Math.min(8192, fileSize - written)];
                    new java.security.SecureRandom().nextBytes(randomData);
                    fos.write(randomData);
                }
                
                fos.flush();
                fos.getFD().sync(); // Force write to disk
            }
            
            fos.close();
            
            // Finally delete the file
            boolean deleted = file.delete();
            if (deleted) {
                Log.d(TAG, "Securely deleted file: " + file.getAbsolutePath());
            }
            
            return deleted;
            
        } catch (IOException e) {
            Log.e(TAG, "Error securely deleting file: " + file.getAbsolutePath(), e);
            return false;
        }
    }
}

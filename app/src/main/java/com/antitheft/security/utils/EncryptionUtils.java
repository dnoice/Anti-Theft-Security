// File Path: app/src/main/java/com/antitheft/security/utils/EncryptionUtils.java
package com.antitheft.security.utils;

import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {
    
    private static final String TAG = "EncryptionUtils";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String KEY_DERIVATION_ALGORITHM = "SHA-256";
    private static final String DEFAULT_PASSWORD = "AntiTheftSecurity2024!";
    
    /**
     * Encrypts a file using AES encryption
     */
    public static boolean encryptFile(File file) {
        try {
            // Read file content
            byte[] fileContent = readFileBytes(file);
            if (fileContent == null) {
                return false;
            }
            
            // Generate key from default password
            SecretKey secretKey = generateKeyFromPassword(DEFAULT_PASSWORD);
            
            // Generate random IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Encrypt content
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedContent = cipher.doFinal(fileContent);
            
            // Write encrypted content with IV prepended
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(iv); // Write IV first
            fos.write(encryptedContent); // Write encrypted content
            fos.close();
            
            Log.d(TAG, "File encrypted successfully: " + file.getName());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting file: " + file.getName(), e);
            return false;
        }
    }
    
    /**
     * Decrypts a file using AES encryption
     */
    public static boolean decryptFile(File file) {
        try {
            // Read encrypted file content
            byte[] fileContent = readFileBytes(file);
            if (fileContent == null || fileContent.length < 16) {
                return false;
            }
            
            // Extract IV and encrypted content
            byte[] iv = new byte[16];
            System.arraycopy(fileContent, 0, iv, 0, 16);
            
            byte[] encryptedContent = new byte[fileContent.length - 16];
            System.arraycopy(fileContent, 16, encryptedContent, 0, encryptedContent.length);
            
            // Generate key from default password
            SecretKey secretKey = generateKeyFromPassword(DEFAULT_PASSWORD);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Decrypt content
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedContent = cipher.doFinal(encryptedContent);
            
            // Write decrypted content back to file
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(decryptedContent);
            fos.close();
            
            Log.d(TAG, "File decrypted successfully: " + file.getName());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting file: " + file.getName(), e);
            return false;
        }
    }
    
    /**
     * Encrypts a string and returns Base64 encoded result
     */
    public static String encryptString(String plainText, String password) {
        try {
            SecretKey secretKey = generateKeyFromPassword(password);
            
            // Generate random IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Encrypt
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return Base64.encodeToString(combined, Base64.DEFAULT);
            
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting string", e);
            return null;
        }
    }
    
    /**
     * Decrypts a Base64 encoded string
     */
    public static String decryptString(String encryptedText, String password) {
        try {
            byte[] combined = Base64.decode(encryptedText, Base64.DEFAULT);
            
            if (combined.length < 16) {
                return null;
            }
            
            // Extract IV and encrypted data
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, 16);
            
            byte[] encryptedBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);
            
            // Decrypt
            SecretKey secretKey = generateKeyFromPassword(password);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes);
            
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting string", e);
            return null;
        }
    }
    
    /**
     * Generates a hash of the given text using SHA-256
     */
    public static String generateHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM);
            byte[] hashBytes = digest.digest(text.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating hash", e);
            return null;
        }
    }
    
    /**
     * Generates a SecretKey from a password using SHA-256
     */
    private static SecretKey generateKeyFromPassword(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM);
        byte[] keyBytes = digest.digest(password.getBytes());
        
        // Use only first 256 bits (32 bytes) for AES-256
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, key.length));
        
        return new SecretKeySpec(key, ALGORITHM);
    }
    
    /**
     * Reads all bytes from a file
     */
    private static byte[] readFileBytes(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] content = new byte[(int) file.length()];
            int bytesRead = fis.read(content);
            fis.close();
            
            if (bytesRead != file.length()) {
                Log.w(TAG, "File reading incomplete: " + file.getName());
                return null;
            }
            
            return content;
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + file.getName(), e);
            return null;
        }
    }
    
    /**
     * Generates a secure random key
     */
    public static String generateSecureKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.encodeToString(secretKey.getEncoded(), Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error generating secure key", e);
            return null;
        }
    }
    
    /**
     * Securely wipes sensitive data from memory
     */
    public static void secureWipe(byte[] data) {
        if (data != null) {
            SecureRandom random = new SecureRandom();
            random.nextBytes(data);
        }
    }
}

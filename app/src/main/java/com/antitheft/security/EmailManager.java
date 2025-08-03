// File: app/src/main/java/com/antitheft/security/EmailManager.java

package com.antitheft.security;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailManager {
    private static final String TAG = "AntiTheft_Email";
    
    private Context context;
    private ExecutorService emailExecutor;
    
    public EmailManager(Context context) {
        this.context = context;
        this.emailExecutor = Executors.newSingleThreadExecutor();
    }
    
    public void sendSecurityAlert(String recipient, String subject, String body, List<String> evidencePaths) {
        emailExecutor.execute(() -> {
            try {
                sendEmailWithAttachments(recipient, subject, body, evidencePaths);
            } catch (Exception e) {
                Log.e(TAG, "Error sending security email", e);
            }
        });
    }
    
    private void sendEmailWithAttachments(String recipient, String subject, String body, List<String> evidencePaths) {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            emailIntent.setType("message/rfc822");
            
            // Set recipient
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
            
            // Set subject and body
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
            
            // Add evidence attachments
            if (evidencePaths != null && !evidencePaths.isEmpty()) {
                ArrayList<Uri> attachmentUris = new ArrayList<>();
                
                for (String path : evidencePaths) {
                    File file = new File(path);
                    if (file.exists()) {
                        try {
                            Uri uri = FileProvider.getUriForFile(
                                context,
                                context.getPackageName() + ".fileprovider",
                                file
                            );
                            attachmentUris.add(uri);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating URI for file: " + path, e);
                        }
                    }
                }
                
                if (!attachmentUris.isEmpty()) {
                    emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris);
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
            
            // Add flags to start activity from service context
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Create chooser
            Intent chooser = Intent.createChooser(emailIntent, "Send Security Alert");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(chooser);
            Log.i(TAG, "Email intent created for recipient: " + recipient);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating email intent", e);
        }
    }
    
    public void cleanup() {
        if (emailExecutor != null && !emailExecutor.isShutdown()) {
            emailExecutor.shutdown();
        }
    }
}

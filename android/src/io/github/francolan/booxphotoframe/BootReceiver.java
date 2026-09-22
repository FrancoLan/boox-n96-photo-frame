package io.github.francolan.booxphotoframe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent received) {
        String action = received.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        context.startService(new Intent(context, ControlService.class));
        if (!autoStartEnabled() || disabled()) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
    }

    private boolean disabled() {
        File root = new File(Environment.getExternalStorageDirectory(), "BooxPhotoframe");
        return new File(root, "disabled").isFile();
    }

    private boolean autoStartEnabled() {
        File root = new File(Environment.getExternalStorageDirectory(), "BooxPhotoframe");
        File config = new File(root, "config.properties");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(config));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equalsIgnoreCase("autostart=true")) return true;
                }
            } finally { reader.close(); }
        } catch (Exception ignored) {}
        return false;
    }
}

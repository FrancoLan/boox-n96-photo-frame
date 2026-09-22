package io.github.francolan.booxphotoframe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class MainActivity extends Activity {
    public static final String ACTION_CONTROL = "io.github.francolan.booxphotoframe.CONTROL";
    private static volatile boolean visible;
    private static final int SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
    private static final long DEFAULT_MIN_INTERVAL_MS = 600000L;
    private static final long DEFAULT_MAX_INTERVAL_MS = 1200000L;
    private static final long RESYNC_INTERVAL_MS = 3600000L;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024;

    private final Handler handler = new Handler();
    private final Random random = new Random();
    private final ArrayList<File> playlist = new ArrayList<File>();
    private FrameView frameView;
    private File rootDir;
    private File cacheDir;
    private int currentIndex = -1;
    private long minIntervalMs = DEFAULT_MIN_INTERVAL_MS;
    private long maxIntervalMs = DEFAULT_MAX_INTERVAL_MS;
    private long lastSyncAt;
    private boolean active;
    private boolean syncing;
    private BroadcastReceiver controlReceiver;

    private final Runnable advanceRunnable = new Runnable() {
        public void run() {
            if (!active) return;
            showRelative(1);
            if (System.currentTimeMillis() - lastSyncAt >= RESYNC_INTERVAL_MS) syncInBackground();
            scheduleNext();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        frameView = new FrameView();
        setContentView(frameView);
        hideSystemUi();

        rootDir = new File(Environment.getExternalStorageDirectory(), "BooxPhotoframe");
        cacheDir = new File(rootDir, "cache");
        cacheDir.mkdirs();
        new File(rootDir, "disabled").delete();
        startService(new Intent(this, ControlService.class));
        controlReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String command = intent.getStringExtra("command");
                if ("next".equals(command)) { showRelative(1); scheduleNext(); }
                else if ("previous".equals(command)) { showRelative(-1); scheduleNext(); }
                else if ("sync".equals(command)) syncInBackground();
                else if ("restart".equals(command)) { loadCachedPlaylist(); showRelative(1); syncInBackground(); scheduleNext(); }
                else if ("disable".equals(command)) finish();
            }
        };
        registerReceiver(controlReceiver, new IntentFilter(ACTION_CONTROL));
        loadIntervals();
        loadCachedPlaylist();
        if (!playlist.isEmpty()) showRelative(1);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        active = true;
        hideSystemUi();
        syncInBackground();
        scheduleNext();
    }

    @Override protected void onPause() {
        visible = false;
        active = false;
        handler.removeCallbacks(advanceRunnable);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (controlReceiver != null) unregisterReceiver(controlReceiver);
        super.onDestroy();
    }

    public static boolean isVisible() { return visible; }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        frameView.setSystemUiVisibility(SYSTEM_UI_FLAGS);
    }

    private void loadIntervals() {
        Map<String, String> config = readConfig();
        minIntervalMs = parseSeconds(config.get("interval_min_seconds"), 600) * 1000L;
        maxIntervalMs = parseSeconds(config.get("interval_max_seconds"), 1200) * 1000L;
        if (minIntervalMs < 10000L) minIntervalMs = 10000L;
        if (maxIntervalMs < minIntervalMs) maxIntervalMs = minIntervalMs;
    }

    private static long parseSeconds(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; }
    }

    private void scheduleNext() {
        handler.removeCallbacks(advanceRunnable);
        long range = maxIntervalMs - minIntervalMs;
        long delay = minIntervalMs + (range <= 0 ? 0 : (Math.abs(random.nextLong()) % (range + 1)));
        handler.postDelayed(advanceRunnable, delay);
    }

    private synchronized void loadCachedPlaylist() {
        File[] files = cacheDir.listFiles();
        playlist.clear();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].getName().matches("[a-f0-9]{64}\\.png")) playlist.add(files[i]);
            }
        }
        Collections.sort(playlist);
        if (currentIndex >= playlist.size()) currentIndex = -1;
    }

    private synchronized void showRelative(int delta) {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + delta + playlist.size()) % playlist.size();
        final Bitmap next = BitmapFactory.decodeFile(playlist.get(currentIndex).getAbsolutePath());
        if (next != null) frameView.setBitmap(next);
        hideSystemUi();
    }

    private void syncInBackground() {
        synchronized (this) {
            if (syncing) return;
            syncing = true;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    syncPhotos();
                    lastSyncAt = System.currentTimeMillis();
                    handler.post(new Runnable() {
                        public void run() {
                            boolean wasEmpty = playlist.isEmpty();
                            loadCachedPlaylist();
                            if (wasEmpty && !playlist.isEmpty()) showRelative(1);
                        }
                    });
                } catch (Exception error) {
                    android.util.Log.w("BooxPhotoframe", "Photo sync failed; keeping cache", error);
                } finally {
                    synchronized (MainActivity.this) { syncing = false; }
                }
            }
        }, "boox-photo-sync").start();
    }

    private void syncPhotos() throws Exception {
        Map<String, String> config = readConfig();
        String server = config.get("server_url");
        String token = config.get("auth_token");
        if (server == null || token == null || !token.matches("[a-f0-9]{64}")) {
            throw new Exception("Missing or invalid " + new File(rootDir, "config.properties"));
        }
        while (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        byte[] manifestBytes = request(server + "/v1/manifest", token, MAX_MANIFEST_BYTES);
        String manifest = new String(manifestBytes, "UTF-8");
        String[] lines = manifest.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].startsWith("# kindle-photoframe-manifest-v1\t")) {
            throw new Exception("Invalid manifest");
        }
        HashSet<String> expectedFiles = new HashSet<String>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].length() == 0) continue;
            String[] fields = lines[i].split("\\t");
            if (fields.length != 3 || !fields[0].matches("[a-f0-9]{64}")) throw new Exception("Invalid manifest row");
            expectedFiles.add(fields[0] + ".png");
            long expectedSize = Long.parseLong(fields[1]);
            if (expectedSize <= 0 || expectedSize > MAX_IMAGE_BYTES) throw new Exception("Invalid image size");
            if (!fields[2].equals("/v1/images/" + fields[0] + ".png")) throw new Exception("Invalid image path");
            File target = new File(cacheDir, fields[0] + ".png");
            if (target.isFile() && target.length() == expectedSize && fields[0].equals(sha256(target))) continue;
            byte[] image = request(server + fields[2], token, MAX_IMAGE_BYTES);
            if (image.length != expectedSize || !fields[0].equals(sha256(image))) throw new Exception("Image checksum mismatch");
            File temporary = new File(cacheDir, fields[0] + ".download");
            FileOutputStream output = new FileOutputStream(temporary);
            try { output.write(image); output.getFD().sync(); } finally { output.close(); }
            if (!temporary.renameTo(target)) throw new Exception("Unable to publish cached image");
        }
        File[] cachedFiles = cacheDir.listFiles();
        if (cachedFiles != null) {
            for (int i = 0; i < cachedFiles.length; i++) {
                String name = cachedFiles[i].getName();
                if (name.matches("[a-f0-9]{64}\\.png") && !expectedFiles.contains(name)) {
                    if (!cachedFiles[i].delete()) throw new Exception("Unable to remove stale cached image");
                }
            }
        }
    }

    private Map<String, String> readConfig() {
        HashMap<String, String> values = new HashMap<String, String>();
        File config = new File(rootDir, "config.properties");
        if (!config.isFile()) return values;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(config));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int split = line.indexOf('=');
                    if (split > 0 && !line.startsWith("#")) values.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            } finally { reader.close(); }
        } catch (Exception error) {
            android.util.Log.w("BooxPhotoframe", "Unable to read config", error);
        }
        return values;
    }

    private static byte[] request(String address, String token, int maximumBytes) throws Exception {
        URL url = new URL(address);
        if (!"http".equals(url.getProtocol()) || url.getHost().length() == 0) throw new Exception("Only local HTTP URLs are supported");
        int port = url.getPort() < 0 ? 80 : url.getPort();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(url.getHost(), port), 10000);
            socket.setSoTimeout(120000);
            OutputStream output = socket.getOutputStream();
            String path = url.getFile().length() == 0 ? "/" : url.getFile();
            String request = "GET " + path + " HTTP/1.0\r\n"
                    + "Host: " + url.getHost() + ":" + port + "\r\n"
                    + "Authorization: Bearer " + token + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(request.getBytes("US-ASCII"));
            output.flush();

            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            String status = readAsciiLine(input);
            if (status == null || !(status.startsWith("HTTP/1.0 200 ") || status.startsWith("HTTP/1.1 200 "))) {
                throw new Exception("Unexpected HTTP response: " + status);
            }
            int contentLength = -1;
            String line;
            while ((line = readAsciiLine(input)) != null && line.length() != 0) {
                int split = line.indexOf(':');
                if (split > 0 && line.substring(0, split).trim().equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(line.substring(split + 1).trim());
                }
                if (split > 0 && line.substring(0, split).trim().equalsIgnoreCase("Transfer-Encoding")) {
                    throw new Exception("Chunked responses are not supported");
                }
            }
            if (contentLength < 0 || contentLength > maximumBytes) throw new Exception("Invalid Content-Length");
            byte[] body = new byte[contentLength];
            int offset = 0;
            while (offset < body.length) {
                int count = input.read(body, offset, body.length - offset);
                if (count < 0) throw new Exception("Truncated HTTP response");
                offset += count;
            }
            return body;
        } finally { try { socket.close(); } catch (Exception ignored) {} }
    }

    private static String readAsciiLine(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int previous = -1;
        while (bytes.size() <= 8192) {
            int current = input.read();
            if (current < 0) return bytes.size() == 0 ? null : new String(bytes.toByteArray(), "US-ASCII");
            if (previous == '\r' && current == '\n') {
                byte[] value = bytes.toByteArray();
                return new String(value, 0, Math.max(0, value.length - 1), "US-ASCII");
            }
            bytes.write(current);
            previous = current;
        }
        throw new Exception("HTTP header line too long");
    }

    private static String sha256(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } finally { input.close(); }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) value.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
        return value.toString();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            showRelative(1); scheduleNext(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            showRelative(-1); scheduleNext(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private final class FrameView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap bitmap;
        private float downX;

        FrameView() { super(MainActivity.this); setBackgroundColor(Color.WHITE); }

        void setBitmap(Bitmap next) {
            Bitmap previous = bitmap;
            bitmap = next;
            invalidate();
            if (previous != null && previous != next) previous.recycle();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            float scaledWidth = bitmap.getWidth() * scale;
            float scaledHeight = bitmap.getHeight() * scale;
            float left = getWidth() - scaledWidth;
            float top = (getHeight() - scaledHeight) / 2.0f;
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float movement = event.getX() - downX;
                if (movement < -30 || event.getX() > getWidth() * 0.55f) showRelative(1);
                else showRelative(-1);
                scheduleNext();
                hideSystemUi();
                return true;
            }
            return true;
        }
    }
}

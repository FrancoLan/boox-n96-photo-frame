package io.github.francolan.booxphotoframe;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class ControlService extends Service {
    private static final String VERSION = "1.1.2";
    private static final long POLL_MS = 60000L;
    private static final long HEARTBEAT_MS = 300000L;
    private static final int MAX_COMMAND_BYTES = 16 * 1024;
    private static final int MAX_APK_BYTES = 50 * 1024 * 1024;
    private volatile boolean stopping;
    private Thread worker;
    private File rootDir;

    @Override public void onCreate() {
        super.onCreate();
        rootDir = new File(Environment.getExternalStorageDirectory(), "BooxPhotoframe");
        rootDir.mkdirs();
        worker = new Thread(new Runnable() {
            public void run() { controlLoop(); }
        }, "boox-wireless-control");
        worker.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopping = true;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    private void controlLoop() {
        long lastHeartbeat = 0;
        while (!stopping) {
            Map<String, String> config = readConfig();
            String server = config.get("server_url");
            String token = config.get("auth_token");
            if (server != null && token != null && token.matches("[a-f0-9]{64}")) {
                while (server.endsWith("/")) server = server.substring(0, server.length() - 1);
                boolean commandHandled = false;
                try { commandHandled = pollCommand(server, token); }
                catch (Exception error) { android.util.Log.w("BooxControl", "Command poll failed", error); }
                long now = System.currentTimeMillis();
                if (commandHandled) lastHeartbeat = now;
                if (now - lastHeartbeat >= HEARTBEAT_MS) {
                    try { postStatus(server, token, "", "heartbeat", "ok", "Wireless control is online"); lastHeartbeat = now; }
                    catch (Exception error) { android.util.Log.w("BooxControl", "Heartbeat failed", error); }
                }
            }
            try { Thread.sleep(POLL_MS); } catch (InterruptedException ignored) {}
        }
    }

    private boolean pollCommand(String server, String token) throws Exception {
        byte[] bytes;
        try { bytes = Http.get(server + "/v1/boox/control/command", token, MAX_COMMAND_BYTES); }
        catch (HttpStatusException error) { if (error.status == 503) return false; else throw error; }
        String text = new String(bytes, "UTF-8");
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0 || !"# boox-photoframe-control-v1".equals(lines[0])) return false;
        HashMap<String, String> command = new HashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int split = lines[i].indexOf('\t');
            if (split > 0) command.put(lines[i].substring(0, split), lines[i].substring(split + 1));
        }
        String action = command.get("action");
        if (action == null || "none".equals(action)) return false;
        if (!isAllowed(action)) throw new Exception("Rejected unsupported command");
        String id = command.get("id");
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new Exception("Invalid command id");
        long now = System.currentTimeMillis() / 1000L;
        long created = parseLong(command.get("created"));
        long expires = parseLong(command.get("expires"));
        if (created <= 0 || created > now + 300 || expires < now || expires > created + 86400) throw new Exception("Expired or invalid command");
        SharedPreferences preferences = getSharedPreferences("control", MODE_PRIVATE);
        if (id.equals(preferences.getString("last_command_id", ""))) return false;
        postStatus(server, token, id, action, "running", "Command received");
        try {
            String detail = execute(action, id, command, server, token);
            preferences.edit().putString("last_command_id", id).commit();
            postStatus(server, token, id, action, "ok", detail);
        } catch (Exception error) {
            postStatus(server, token, id, action, "error", error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        return true;
    }

    private String execute(String action, String id, Map<String, String> command, String server, String token) throws Exception {
        if ("next".equals(action) || "previous".equals(action) || "sync".equals(action) || "restart".equals(action)) {
            if (!MainActivity.isVisible() && !"sync".equals(action)) launchFrame();
            sendControl(action);
            return "Command completed";
        }
        if ("disable".equals(action)) {
            FileOutputStream output = new FileOutputStream(new File(rootDir, "disabled"));
            output.write("disabled\n".getBytes("UTF-8"));
            output.close();
            sendControl("disable");
            return "Photo frame disabled; wireless control remains online";
        }
        if ("enable".equals(action)) {
            new File(rootDir, "disabled").delete();
            launchFrame();
            return "Photo frame enabled";
        }
        if ("diagnose".equals(action)) {
            uploadDiagnostics(server, token, id);
            return "Diagnostic uploaded";
        }
        if ("update".equals(action)) {
            return downloadUpdate(server, token, id, command);
        }
        throw new Exception("Unsupported command");
    }

    private void sendControl(String command) {
        Intent intent = new Intent(MainActivity.ACTION_CONTROL);
        intent.setPackage(getPackageName());
        intent.putExtra("command", command);
        sendBroadcast(intent);
    }

    private void launchFrame() {
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
    }

    private String downloadUpdate(String server, String token, String id, Map<String, String> command) throws Exception {
        String sha = command.get("packageSha256");
        long expectedBytes = parseLong(command.get("packageBytes"));
        String path = command.get("packagePath");
        if (sha == null || !sha.matches("[a-f0-9]{64}")) throw new Exception("Invalid APK hash");
        if (expectedBytes <= 0 || expectedBytes > MAX_APK_BYTES) throw new Exception("Invalid APK size");
        if (!('/' + "v1/boox/control/packages/" + sha + ".apk").equals(path)) throw new Exception("Invalid APK path");
        byte[] apk = Http.get(server + path, token, MAX_APK_BYTES);
        if (apk.length != expectedBytes || !sha.equals(sha256(apk))) throw new Exception("APK integrity check failed");
        File target = new File(rootDir, "update-" + id + ".apk");
        FileOutputStream output = new FileOutputStream(target);
        try { output.write(apk); output.getFD().sync(); } finally { output.close(); }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(Uri.fromFile(target), "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
        return "APK verified; confirm installation on the device";
    }

    private void uploadDiagnostics(String server, String token, String id) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(raw);
        String report = "collectedAt=" + System.currentTimeMillis() + "\n"
                + "device=boox-n96\nmodel=" + Build.MODEL + "\nrelease=" + Build.VERSION.RELEASE + "\nsdk=" + Build.VERSION.SDK_INT + "\n"
                + "appVersion=" + VERSION + "\nappState=" + appState() + "\nuptimeMs=" + SystemClock.elapsedRealtime() + "\n"
                + "cacheFiles=" + cacheCount() + "\nfreeBytes=" + rootDir.getFreeSpace() + "\ntotalBytes=" + rootDir.getTotalSpace() + "\n";
        gzip.write(report.getBytes("UTF-8"));
        gzip.finish();
        gzip.close();
        Http.post(server + "/v1/boox/control/diagnostics/" + id, token, "application/gzip", raw.toByteArray(), 1024);
    }

    private void postStatus(String server, String token, String id, String action, String result, String detail) throws Exception {
        String clean = String.valueOf(detail).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (clean.length() > 180) clean = clean.substring(0, 180);
        String body = "# boox-photoframe-status-v1\n"
                + "device\tboox-n96\n"
                + (id.length() == 0 ? "" : "commandId\t" + id + "\n")
                + "action\t" + action + "\nresult\t" + result + "\nappState\t" + appState() + "\n"
                + "appVersion\t" + VERSION + "\ndetail\t" + clean + "; cache=" + cacheCount() + "\n";
        Http.post(server + "/v1/boox/control/status", token, "text/tab-separated-values; charset=utf-8", body.getBytes("UTF-8"), 1024);
    }

    private String appState() { return MainActivity.isVisible() ? "running" : "stopped"; }
    private int cacheCount() {
        File[] files = new File(rootDir, "cache").listFiles();
        int count = 0;
        if (files != null) for (int i = 0; i < files.length; i++) if (files[i].getName().matches("[a-f0-9]{64}\\.png")) count++;
        return count;
    }

    private Map<String, String> readConfig() {
        HashMap<String, String> values = new HashMap<String, String>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(rootDir, "config.properties")));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int split = line.indexOf('=');
                    if (split > 0 && !line.startsWith("#")) values.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            } finally { reader.close(); }
        } catch (Exception ignored) {}
        return values;
    }

    private static boolean isAllowed(String action) {
        return "next".equals(action) || "previous".equals(action) || "sync".equals(action) || "restart".equals(action)
                || "disable".equals(action) || "enable".equals(action) || "update".equals(action) || "diagnose".equals(action);
    }
    private static long parseLong(String value) { try { return Long.parseLong(value); } catch (Exception ignored) { return -1; } }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; i++) value.append(String.format(Locale.US, "%02x", digest[i] & 0xff));
        return value.toString();
    }

    private static final class HttpStatusException extends Exception {
        final int status;
        HttpStatusException(int value) { super("HTTP " + value); status = value; }
    }

    private static final class Http {
        static byte[] get(String address, String token, int maximumBytes) throws Exception {
            return exchange("GET", address, token, null, null, maximumBytes, 200);
        }
        static void post(String address, String token, String type, byte[] body, int maximumResponseBytes) throws Exception {
            exchange("POST", address, token, type, body, maximumResponseBytes, 204);
        }
        private static byte[] exchange(String method, String address, String token, String type, byte[] body, int maximumBytes, int expectedStatus) throws Exception {
            URL url = new URL(address);
            if (!"http".equals(url.getProtocol()) || url.getHost().length() == 0) throw new Exception("Only local HTTP URLs are supported");
            int port = url.getPort() < 0 ? 80 : url.getPort();
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(url.getHost(), port), 10000);
                socket.setSoTimeout(120000);
                OutputStream output = socket.getOutputStream();
                String path = url.getFile().length() == 0 ? "/" : url.getFile();
                String headers = method + " " + path + " HTTP/1.0\r\nHost: " + url.getHost() + ":" + port
                        + "\r\nAuthorization: Bearer " + token + "\r\nConnection: close\r\n"
                        + (body == null ? "" : "Content-Type: " + type + "\r\nContent-Length: " + body.length + "\r\n") + "\r\n";
                output.write(headers.getBytes("US-ASCII"));
                if (body != null) output.write(body);
                output.flush();
                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                String statusLine = readAsciiLine(input);
                if (statusLine == null || !statusLine.startsWith("HTTP/")) throw new Exception("Invalid HTTP response");
                String[] statusParts = statusLine.split(" ");
                int status = statusParts.length > 1 ? Integer.parseInt(statusParts[1]) : 0;
                int contentLength = -1;
                String line;
                while ((line = readAsciiLine(input)) != null && line.length() != 0) {
                    int split = line.indexOf(':');
                    if (split > 0 && line.substring(0, split).trim().equalsIgnoreCase("Content-Length")) contentLength = Integer.parseInt(line.substring(split + 1).trim());
                    if (split > 0 && line.substring(0, split).trim().equalsIgnoreCase("Transfer-Encoding")) throw new Exception("Chunked response rejected");
                }
                if (status != expectedStatus) throw new HttpStatusException(status);
                if (expectedStatus == 204 && contentLength < 0) contentLength = 0;
                if (contentLength < 0 || contentLength > maximumBytes) throw new Exception("Invalid Content-Length");
                byte[] response = new byte[contentLength];
                int offset = 0;
                while (offset < response.length) {
                    int count = input.read(response, offset, response.length - offset);
                    if (count < 0) throw new Exception("Truncated HTTP response");
                    offset += count;
                }
                return response;
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
                bytes.write(current); previous = current;
            }
            throw new Exception("HTTP header line too long");
        }
    }
}

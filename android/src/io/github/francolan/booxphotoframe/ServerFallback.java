package io.github.francolan.booxphotoframe;

import java.net.URL;

final class ServerFallback {
    interface Probe { void check(String server) throws Exception; }
    static String choose(String primary, String backup, Probe probe) throws Exception {
        String first = normalize(primary);
        try { probe.check(first); return first; }
        catch (Exception firstError) {
            if (backup == null || backup.trim().length() == 0) throw firstError;
            String second = normalize(backup);
            if (first.equals(second)) throw firstError;
            probe.check(second);
            return second;
        }
    }
    private static String normalize(String value) throws Exception {
        if (value == null) throw new Exception("Missing server URL");
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        URL url = new URL(result);
        if (!"http".equals(url.getProtocol()) || url.getHost().length() == 0 || url.getUserInfo() != null
                || url.getQuery() != null || url.getRef() != null || url.getPath().length() != 0) throw new Exception("Invalid server URL");
        return result;
    }
}

#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
cp "$ROOT/android/src/io/github/francolan/booxphotoframe/ServerFallback.java" "$TEMP/ServerFallback.java"
cat > "$TEMP/TestFallback.java" <<'JAVA'
package io.github.francolan.booxphotoframe;
public class TestFallback {
    public static void main(String[] args) throws Exception {
        final String primary = "http://127.0.0.1:8787";
        final String backup = "http://127.0.0.2:8787";
        ServerFallback.Probe good = new ServerFallback.Probe() { public void check(String server) {} };
        if (!primary.equals(ServerFallback.choose(primary, backup, good))) throw new Exception("primary priority");
        ServerFallback.Probe failPrimary = new ServerFallback.Probe() { public void check(String server) throws Exception { if (primary.equals(server)) throw new Exception("offline"); } };
        if (!backup.equals(ServerFallback.choose(primary, backup, failPrimary))) throw new Exception("fallback");
        if (!primary.equals(ServerFallback.choose(primary, backup, good))) throw new Exception("failback");
        boolean failed = false;
        try { ServerFallback.choose(primary, backup, new ServerFallback.Probe() { public void check(String server) throws Exception { throw new Exception("offline"); } }); } catch (Exception expected) { failed = true; }
        if (!failed) throw new Exception("both offline must fail");
        failed = false;
        try { ServerFallback.choose("http://user:password@127.0.0.1", backup, good); } catch (Exception expected) { failed = true; }
        if (!failed) throw new Exception("credentials in URL rejected");
        System.out.println("Server fallback checks passed.");
    }
}
JAVA
javac -source 7 -target 7 -Xlint:-options -d "$TEMP" "$TEMP/ServerFallback.java" "$TEMP/TestFallback.java"
java -cp "$TEMP" io.github.francolan.booxphotoframe.TestFallback

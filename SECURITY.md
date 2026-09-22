# Security policy

## Intended deployment

Run the server only on a trusted private network. Bind it to the Mac's private LAN address and allow access only from trusted devices. Do not expose port 8787 through router port forwarding.

The protocol authenticates requests with a random 256-bit bearer token but uses plain HTTP for compatibility with Android 4.0.4. Anyone able to capture traffic on an untrusted network may recover that token.

For access away from home, connect both endpoints through a private VPN. Do not solve remote access by enabling wireless ADB.

## Secrets that must never be committed

- `server/config.local.json`
- `data/server-token`
- `android/build/config.properties`
- `android/build/debug.keystore`
- generated photos, diagnostics, logs, or shared-album metadata

The repository `.gitignore` excludes these default paths. Re-run a secret scan before publishing from a nonstandard layout.

## Reporting a vulnerability

Open a GitHub security advisory rather than a public issue when the repository owner has enabled private vulnerability reporting. Otherwise contact the maintainer privately before disclosure.

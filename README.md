# King-Lavalink

King-Lavalink provides a Lavalink 4.2.2 instance, configuration, and tools for development and integration with the King bot. It includes local monitoring, YouTube plugin support, and a ShardCloud packaging example that can be adapted for other providers.

## Requirements

- Java 17 or newer to run Lavalink.
- JDK 17 or newer and Git to build the custom plugin. The ShardCloud packaging example also requires PowerShell, `javac`, and `jar`.
- Python 3 (standard library only) to run the local dashboard.
- Internet access to download Lavalink and, when building the plugin, clone its upstream repository.

## Run locally

From the repository root, set up and start the service.

### Windows (PowerShell)

```powershell
.\scripts\setup.ps1
.\scripts\start.ps1
```

### Linux/macOS (Bash)

```bash
chmod +x scripts/setup.sh scripts/start.sh scripts/panel.sh
./scripts/setup.sh
./scripts/start.sh
```

The setup script downloads Lavalink 4.2.2 if `Lavalink.jar` is missing and creates `application.yml` with a random password if the configuration does not exist. Existing files are preserved. The service runs in the foreground; press `Ctrl+C` to stop it.

The local configuration binds Lavalink to `127.0.0.1:2333`. Check the API using the password from `application.yml`:

```bash
curl -H "Authorization: YOUR_PASSWORD" http://127.0.0.1:2333/version
```

A version response confirms that the API is reachable. Also verify that the configured audio sources and playback work.

## Local dashboard

With Lavalink running, open another terminal and start the dashboard:

```powershell
.\scripts\panel.ps1
```

On Linux/macOS, run `./scripts/panel.sh`. The dashboard uses Python 3 and its standard library, listens on `127.0.0.1:8080`, and displays status, players, resource usage, logs, and connection history. Create `.env` from the template and replace `PANEL_PASSWORD` with a strong password. The local Python dashboard and Java launcher read this file; variables already set in the process environment take precedence. When prompted by the browser, use `admin` as the HTTP Basic username and the configured password.

```powershell
Copy-Item .env.example .env
# Edit .env and set PANEL_PASSWORD before starting the dashboard.
.\scripts\panel.ps1
```

```bash
cp .env.example .env
# Edit .env and set PANEL_PASSWORD before starting the dashboard.
./scripts/panel.sh
```

Set `PANEL_PORT` to change the local dashboard port. The dashboard can download the complete `lavalink.log` file and expand or collapse the scrollable log viewer. Authentication protects the dashboard and its administrative routes. Keep the dashboard local or publish it only behind HTTPS; Lavalink continues to use its own API password.

## YouTube plugin

Lavalink's built-in YouTube source is disabled (`lavalink.server.sources.youtube: false`); the plugin is loaded from a JAR in `plugins/`. The Maven `lavalink.plugins` list is empty to prevent a second copy of the plugin from loading. The example configuration enables search, direct IDs, the MUSIC, TV, ANDROID_VR, WEB, and WEBEMBEDDED clients, optional OAuth, and a community-hosted cipher service.

The `TVHTML5` client's User-Agent uses an experimental workaround with no guarantee of future compatibility. To build the JAR on Windows, with Git and JDK 17 or newer, run:

```powershell
.\scripts\build-youtube-plugin-patched.ps1
```

The artifact is created at `dist/youtube-plugin-1.18.2-king-tv-ua.jar`; copy it to `plugins/` for local use. The build clones tag 1.18.2, applies the versioned patch, and runs `:plugin:build`. See [docs/youtube-tv-user-agent-fix.md](docs/youtube-tv-user-agent-fix.md) for installation, rollback, and limitations. The public cipher service has no availability guarantee. For continuous use, host your own [yt-cipher](https://github.com/Sagexdd/yt-cipher) instance and update `plugins.youtube.remoteCipher.url`.

If `application.yml` predates configuration changes, the setup script will not update it. Merge the required options from `application.example.yml`, preserve your password, and keep each YAML section defined only once.

## Deploy to other systems

Lavalink can run in any environment that provides Java 17 or newer. Use `application.example.yml` as a starting point, store the password in your provider's secret store, and configure the bot to reach the node over a private or protected public address. For reverse proxies, containers, or managed platforms, adapt the bind address, port, forwarding for `/version`, `/v3`, and `/v4`, and WebSocket support. Use TLS (`https`/`wss`) for connections over the internet, and avoid exposing Lavalink's internal port directly.

### Example: ShardCloud

This repository includes a ShardCloud-specific packager as an adaptation example. The package uses a Java runtime: `.shardcloud` starts `king-lavalink-launcher.jar`, which downloads Lavalink 4.2.2 on first startup if needed, keeps the service on `127.0.0.1:2333`, and exposes the dashboard and proxy on port 80. Set `PANEL_PASSWORD` as an environment variable or platform secret before starting the application. HTTP Basic authentication protects the dashboard; Lavalink routes (`/version`, `/v3`, `/v4`, and WebSocket) remain available to the bot and use Lavalink's own password. The platform must provide HTTPS for external access.

Build the package on Windows with PowerShell, JDK 17 or newer, and the plugin JAR in `dist/` (the script builds the plugin if needed):

```powershell
.\scripts\package-shardcloud.ps1
```

Upload `dist/king-lavalink-shardcloud.zip` to a Java application on ShardCloud. The packager creates `dist/shardcloud/` with the launcher, `application.yml`, dashboard, `.shardcloud`, and plugin. It preserves the password in an existing `dist/shardcloud/application.yml`; if that directory is removed, a new password is generated. Update the `LAVALINK_PASSWORD` secret used by King whenever the password changes. Protect the ZIP because it contains this credential. The package does not include `Lavalink.jar`, `.env`, or your local configuration.

For another provider or system, set `PANEL_PASSWORD` in the process environment and adapt packaging, startup, proxying, and TLS to the available features. `.shardcloud` and the ZIP format are specific to this example.

## Integrate with King

To connect a local King instance to the local service, set these values in the bot's `.env`. Use the same password as in `application.yml`:

```env
LAVALINK_LOCAL=true
LAVALINK_HOST=127.0.0.1
LAVALINK_PORT=2333
LAVALINK_SECURE=false
LAVALINK_PASSWORD=PASSWORD_FROM_APPLICATION_YML
```

Restart the bot and confirm in its logs that the node connected. When the bot and Lavalink run in separate containers, `127.0.0.1` refers to the current container. Use the service's private address or DNS name, and configure the bind address and firewall accordingly.

## Configuration and security

- `.env.example`: environment variable template for the dashboard; copy it to `.env` and set `PANEL_PASSWORD`.
- `application.example.yml`: local configuration template; setup replaces its password placeholder when creating `application.yml`.
- `application.shardcloud.yml`: template used only by the ShardCloud packaging example.
- `PANEL_PASSWORD`: required dashboard password; configure it in the process environment or `.env`, and never commit the secret.
- `application.yml`: local configuration, ignored by Git. Do not publish or share it.
- `Lavalink.jar`: local runtime binary; setup downloads it if missing.
- `plugins/`: directory for Lavalink plugin JARs.
- Do not expose internal port 2333 directly to the internet. Restrict access to a private network or use a proxy with TLS and WebSocket support; the ShardCloud example launcher keeps Lavalink bound to loopback.

## Project structure

```text
King-Lavalink/
├── .env.example
├── application.example.yml
├── application.shardcloud.yml
├── docs/
│   ├── local-testing.md
│   └── youtube-tv-user-agent-fix.md
├── patches/
│   └── youtube-source-1.18.2-tv-user-agent.patch
├── scripts/
│   ├── build-youtube-plugin-patched.ps1
│   ├── package-shardcloud.ps1
│   ├── panel.*
│   ├── setup.*
│   └── start.*
└── src/main/java/app/kinglavalink/Launcher.java
```

The `dist/`, `plugins/`, and `logs/` directories contain generated artifacts or runtime data and are not part of the tracked project structure. See [docs/local-testing.md](docs/local-testing.md) for the local validation checklist.

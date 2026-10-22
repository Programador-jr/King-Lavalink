import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Runs Lavalink privately and serves the authenticated dashboard plus HTTP/WebSocket proxy. */
public final class KingLauncher {
    static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    static final Path LOG = ROOT.resolve("logs/lavalink.log");
    static final Path HISTORY = ROOT.resolve("logs/connections.jsonl");
    static final int PANEL_PORT = Integer.parseInt(System.getenv().getOrDefault("PANEL_PORT", "80"));
    static final int LL_PORT = Integer.parseInt(System.getenv().getOrDefault("LAVALINK_PORT", "2333"));
    static final String LL_PASSWORD = first(System.getenv("LAVALINK_SERVER_PASSWORD"), System.getenv("LAVALINK_PASSWORD"));
    static final String PANEL_PASSWORD = first(System.getenv("PANEL_PASSWORD"), LL_PASSWORD);
    static final String PANEL_USER = System.getenv().getOrDefault("PANEL_USERNAME", "admin");
    static final ExecutorService CLIENTS = Executors.newCachedThreadPool();
    static Process lavalink;

    public static void main(String[] args) throws Exception {
        Path jar = ROOT.resolve("Lavalink.jar");
        Path config = Files.isRegularFile(ROOT.resolve("application.yml")) ? ROOT.resolve("application.yml") : ROOT.resolve("application.shardcloud.yml");
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Lavalink.jar not found");
        if (!Files.isRegularFile(config)) throw new IllegalStateException("application.yml/application.shardcloud.yml not found");
        Files.createDirectories(ROOT.resolve("logs"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        lavalink = new ProcessBuilder(java, "-Xms128m", "-Xmx512m", "-jar", jar.toString(),
            "--spring.config.location=file:" + config, "--server.address=127.0.0.1", "--server.port=" + LL_PORT)
            .directory(ROOT.toFile()).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (lavalink != null && lavalink.isAlive()) lavalink.destroy(); CLIENTS.shutdownNow(); }));
        System.out.println("King dashboard: 0.0.0.0:" + PANEL_PORT);
        System.out.println("Lavalink private endpoint: 127.0.0.1:" + LL_PORT);
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", PANEL_PORT));
            while (lavalink.isAlive()) CLIENTS.submit(() -> {
                try { Socket client = server.accept(); CLIENTS.submit(() -> handle(client)); }
                catch (IOException e) { if (lavalink.isAlive()) System.err.println("Accept failed: " + e.getMessage()); }
            });
        } finally { if (lavalink.isAlive()) lavalink.destroy(); }
        throw new IllegalStateException("Lavalink exited with code " + lavalink.waitFor());
    }

    static void handle(Socket client) {
        try (client) {
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = new BufferedOutputStream(client.getOutputStream());
            Req r = Req.read(in); if (r == null) return;
            String path = URI.create(r.target.startsWith("http://") || r.target.startsWith("https://") ? r.target : "http://localhost" + (r.target.startsWith("/") ? r.target : "/" + r.target)).getPath();
            if (Set.of("/", "/api/status", "/api/logs", "/api/history").contains(path)) {
                if (!authorized(r)) { send(out, 401, "Unauthorized", "", Map.of("WWW-Authenticate", "Basic realm=\"King Lavalink Panel\", charset=\"UTF-8\"")); return; }
                switch (path) {
                    case "/" -> page(out);
                    case "/api/status" -> status(out);
                    case "/api/logs" -> send(out, 200, "OK", jsonArray(tail(LOG, 200)), Map.of("Content-Type", "application/json; charset=utf-8"));
                    case "/api/history" -> send(out, 200, "OK", jsonArray(tail(HISTORY, 500)), Map.of("Content-Type", "application/json; charset=utf-8"));
                }
            } else if ("websocket".equalsIgnoreCase(r.header("Upgrade"))) websocket(client, in, out, r);
            else proxy(client, in, out, r);
        } catch (Exception e) { System.err.println("Request failed: " + e.getMessage()); }
    }

    static boolean authorized(Req r) {
        if (PANEL_PASSWORD == null || PANEL_PASSWORD.isBlank()) return false;
        String h = r.header("Authorization");
        if (h == null || !h.startsWith("Basic ")) return false;
        try {
            String v = new String(Base64.getDecoder().decode(h.substring(6)), StandardCharsets.UTF_8);
            int i = v.indexOf(':');
            return i > -1 && secureEquals(PANEL_USER, v.substring(0, i)) && secureEquals(PANEL_PASSWORD, v.substring(i + 1));
        } catch (IllegalArgumentException e) { return false; }
    }
    static boolean secureEquals(String a, String b) { return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }

    static void page(OutputStream out) throws IOException {
        Path p = ROOT.resolve("scripts/panel.html");
        if (!Files.isRegularFile(p)) { send(out, 500, "Panel missing", "scripts/panel.html not found", Map.of("Content-Type", "text/plain; charset=utf-8")); return; }
        sendBytes(out, 200, "OK", Files.readAllBytes(p), Map.of("Content-Type", "text/html; charset=utf-8"));
    }

    static void status(OutputStream out) throws IOException {
        String at = Instant.now().toString(), stats = null, error = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + LL_PORT + "/v4/stats").openConnection();
            c.setConnectTimeout(1500); c.setReadTimeout(2500);
            c.setRequestProperty("Authorization", LL_PASSWORD == null ? "" : LL_PASSWORD);
            try (InputStream in = c.getInputStream()) { stats = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            c.disconnect();
        } catch (Exception e) { error = e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "unavailable"); }
        if (stats == null) { send(out, 200, "OK", "{\"checkedAt\":" + quote(at) + ",\"connected\":false,\"error\":" + quote(error) + "}", Map.of("Content-Type", "application/json; charset=utf-8")); return; }
        String line = "{\"at\":" + quote(at) + ",\"players\":" + number(stats, "players", "0") + ",\"playingPlayers\":" + number(stats, "playingPlayers", "0") + ",\"uptime\":" + number(stats, "uptime", "null") + "}" + System.lineSeparator();
        try { Files.createDirectories(HISTORY.getParent()); Files.writeString(HISTORY, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (IOException e) { System.err.println("History write failed: " + e.getMessage()); }
        send(out, 200, "OK", "{\"checkedAt\":" + quote(at) + ",\"connected\":true,\"stats\":" + stats + "}", Map.of("Content-Type", "application/json; charset=utf-8"));
    }

    static void proxy(Socket client, InputStream in, OutputStream out, Req r) throws IOException {
        try (Socket up = new Socket()) {
            up.connect(new InetSocketAddress("127.0.0.1", LL_PORT), 5000); up.setSoTimeout(120000);
            OutputStream uo = new BufferedOutputStream(up.getOutputStream());
            StringBuilder h = new StringBuilder(r.method).append(' ').append(r.target).append(" HTTP/1.1\r\n");
            r.headers.forEach((k,v) -> { if (!k.equalsIgnoreCase("Host") && !k.equalsIgnoreCase("Connection") && !k.equalsIgnoreCase("Proxy-Connection")) h.append(k).append(": ").append(v).append("\r\n"); });
            h.append("Host: 127.0.0.1:").append(LL_PORT).append("\r\nConnection: close\r\n\r\n");
            uo.write(h.toString().getBytes(StandardCharsets.ISO_8859_1)); copyExactly(in, uo, r.length); uo.flush();
            new BufferedInputStream(up.getInputStream()).transferTo(out); out.flush();
        } catch (IOException e) {
            if (!client.isClosed()) send(out, 502, "Bad Gateway", "{\"error\":\"Lavalink proxy unavailable\"}", Map.of("Content-Type", "application/json; charset=utf-8"));
        }
    }

    static void websocket(Socket client, InputStream in, OutputStream out, Req r) throws IOException {
        try (Socket up = new Socket()) {
            up.connect(new InetSocketAddress("127.0.0.1", LL_PORT), 5000);
            OutputStream uo = up.getOutputStream();
            StringBuilder h = new StringBuilder(r.method).append(' ').append(r.target).append(" HTTP/1.1\r\n");
            r.headers.forEach((k,v) -> h.append(k).append(": ").append(k.equalsIgnoreCase("Host") ? "127.0.0.1:" + LL_PORT : v).append("\r\n"));
            uo.write((h + "\r\n").getBytes(StandardCharsets.ISO_8859_1)); uo.flush();
            InputStream ui = new BufferedInputStream(up.getInputStream());
            ByteArrayOutputStream handshake = new ByteArrayOutputStream(); int matched = 0; byte[] end = {13,10,13,10};
            while (handshake.size() < 65536 && matched < 4) { int b = ui.read(); if (b < 0) break; handshake.write(b); if (b == end[matched]) matched++; else matched = b == 13 ? 1 : 0; }
            byte[] response = handshake.toByteArray(); out.write(response); out.flush();
            if (!new String(response, StandardCharsets.ISO_8859_1).split("\r\n", 2)[0].contains(" 101 ")) return;
            client.setSoTimeout(0); up.setSoTimeout(0);
            Thread a = new Thread(() -> pump(in, uo, up), "ws-to-lavalink");
            Thread b = new Thread(() -> pump(ui, out, client), "ws-to-client");
            a.start(); b.start(); try { a.join(); b.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    static void pump(InputStream in, OutputStream out, Socket target) {
        byte[] b = new byte[16384];
        try { int n; while ((n = in.read(b)) >= 0) { out.write(b, 0, n); out.flush(); } } catch (IOException ignored) {}
        finally { try { target.shutdownOutput(); } catch (IOException ignored) {} }
    }
    static void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        byte[] b = new byte[8192]; long left = length;
        while (left > 0) { int n = in.read(b, 0, (int)Math.min(b.length, left)); if (n < 0) throw new EOFException("Incomplete request body"); out.write(b, 0, n); left -= n; }
    }

    static List<String> tail(Path p, int max) {
        try { if (!Files.isRegularFile(p)) return List.of(); List<String> l = Files.readAllLines(p, StandardCharsets.UTF_8); return l.subList(Math.max(0, l.size() - max), l.size()); }
        catch (IOException e) { return List.of(); }
    }
    static String jsonArray(List<String> lines) { return "[" + String.join(",", lines.stream().map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.startsWith("{") ? s : quote(s)).toList()) + "]"; }
    static String number(String json, String key, String fallback) { Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json); return m.find() ? m.group(1) : fallback; }
    static String quote(String s) { if (s == null) return "null"; return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    static String first(String a, String b) { return a != null && !a.isBlank() ? a : b != null && !b.isBlank() ? b : null; }
    static void send(OutputStream out, int code, String reason, String body, Map<String,String> headers) throws IOException { sendBytes(out, code, reason, body.getBytes(StandardCharsets.UTF_8), headers); }
    static void sendBytes(OutputStream out, int code, String reason, byte[] body, Map<String,String> headers) throws IOException {
        StringBuilder h = new StringBuilder("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        headers.forEach((k,v) -> h.append(k).append(": ").append(v).append("\r\n"));
        h.append("Content-Length: ").append(body.length).append("\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1)); out.write(body); out.flush();
    }
    static final class Req {
        String method, target; int length; final LinkedHashMap<String,String> headers = new LinkedHashMap<>();
        String header(String name) { return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null); }
        static Req read(InputStream in) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(); int matched = 0; byte[] end = {13,10,13,10};
            while (raw.size() < 65536 && matched < 4) { int b = in.read(); if (b < 0) return null; raw.write(b); if (b == end[matched]) matched++; else matched = b == 13 ? 1 : 0; }
            if (matched < 4) throw new IOException("Malformed HTTP headers");
            String[] lines = raw.toString(StandardCharsets.ISO_8859_1).split("\r\n"); if (lines.length == 0) return null;
            String[] start = lines[0].split(" ", 3); if (start.length < 2) throw new IOException("Malformed HTTP request");
            Req r = new Req(); r.method = start[0]; r.target = start[1];
            for (int i=1;i<lines.length;i++) { int p=lines[i].indexOf(':'); if (p>0) r.headers.put(lines[i].substring(0,p).trim(), lines[i].substring(p+1).trim()); }
            try { r.length = Integer.parseInt(Objects.toString(r.header("Content-Length"), "0")); } catch (NumberFormatException e) { r.length = 0; }
            return r;
        }
    }
}
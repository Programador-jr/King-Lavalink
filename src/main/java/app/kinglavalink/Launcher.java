package app.kinglavalink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Starts Lavalink privately and serves the dashboard plus a REST/WebSocket proxy. */
public final class Launcher {
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Path CONFIG = ROOT.resolve("application.yml");
    private static final Path LOG_FILE = ROOT.resolve("logs/lavalink.log");
    private static final Path HISTORY_FILE = ROOT.resolve("logs/connections.jsonl");
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static String panelHtml;
    private static String lavalinkPassword;
    private static String panelPassword;
    private static int lavalinkPort;

    private Launcher() {}

    public static void main(String[] args) throws Exception {
        String config = Files.readString(CONFIG, StandardCharsets.UTF_8);
        lavalinkPassword = yamlValue(config, "password", "");
        panelPassword = envValue("PANEL_PASSWORD", "");
        if (panelPassword.isBlank()) throw new IllegalStateException("Defina PANEL_PASSWORD no ambiente.");
        lavalinkPort = Integer.parseInt(yamlValue(config, "port", "2333"));
        String panelHost = sectionValue(config, "panel", "address", "0.0.0.0");
        int panelPort = Integer.parseInt(sectionValue(config, "panel", "port", "80"));
        panelHtml = Files.readString(ROOT.resolve("scripts/panel.html"), StandardCharsets.UTF_8);
        Files.createDirectories(ROOT.resolve("logs"));

        Path lavalinkJar = ROOT.resolve("Lavalink.jar");
        downloadLavalinkIfMissing(lavalinkJar);
        Process lavalink = new ProcessBuilder("java", "-jar", lavalinkJar.toString())
                .directory(ROOT.toFile())
                .inheritIO()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            lavalink.destroy();
            try {
                if (!lavalink.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) lavalink.destroyForcibly();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));

        System.out.printf("Painel/proxy em http://%s:%d; Lavalink interno em 127.0.0.1:%d%n",
                panelHost, panelPort, lavalinkPort);
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(panelHost, panelPort));
            server.setSoTimeout(2000);
            while (lavalink.isAlive()) {
                try {
                    Socket client = server.accept();
                    new Thread(() -> handleSafely(client), "king-lavalink-client").start();
                } catch (java.net.SocketTimeoutException ignored) {
                    // Poll Lavalink so a failed child process also stops the public listener.
                }
            }
            throw new IllegalStateException("Lavalink encerrou com código " + lavalink.exitValue());
        }
    }

    private static void downloadLavalinkIfMissing(Path jar) throws Exception {
        if (Files.exists(jar) && Files.size(jar) > 1_000_000) return;
        String version = System.getenv().getOrDefault("LAVALINK_VERSION", "4.2.2");
        URI release = URI.create("https://github.com/lavalink-devs/Lavalink/releases/download/"
                + version + "/Lavalink.jar");
        System.out.println("Baixando Lavalink " + version + "...");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(release).GET().build();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(jar, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        if (response.statusCode() < 200 || response.statusCode() >= 300 || Files.size(jar) < 1_000_000) {
            Files.deleteIfExists(jar);
            throw new IOException("Download do Lavalink falhou (HTTP " + response.statusCode() + ")");
        }
    }

    private static String yamlValue(String yaml, String key, String fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s+" + Pattern.quote(key)
                + ":\\s*[\\\"']?([^\\\"'\\s#]+)").matcher(yaml);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String envValue(String key, String fallback) throws IOException {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) return value;
        Path envFile = ROOT.resolve(".env");
        if (!Files.exists(envFile)) return fallback;
        for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int separator = trimmed.indexOf('=');
            if (separator < 0 || !trimmed.substring(0, separator).trim().equals(key)) continue;
            String configured = trimmed.substring(separator + 1).trim();
            if (configured.length() >= 2 && ((configured.startsWith("\"") && configured.endsWith("\""))
                    || (configured.startsWith("'") && configured.endsWith("'")))) {
                configured = configured.substring(1, configured.length() - 1);
            }
            return configured;
        }
        return fallback;
    }

    private static String sectionValue(String yaml, String section, String key, String fallback) {
        Matcher block = Pattern.compile("(?ms)^" + Pattern.quote(section)
                + ":\\s*\\n((?:[ \\t]+.*\\n?)*)").matcher(yaml);
        if (!block.find()) return fallback;
        Matcher value = Pattern.compile("(?m)^\\s+" + Pattern.quote(key) + ":\\s*([^\\s#]+)")
                .matcher(block.group(1));
        return value.find() ? value.group(1) : fallback;
    }

    private static void handleSafely(Socket socket) {
        try (socket) {
            socket.setSoTimeout(30_000);
            Request request = Request.read(socket.getInputStream());
            if (request == null) return;
            String path = URI.create(request.target).getPath();
            if (isAdminPath(path) && !authorized(request)) {
                writeResponse(socket.getOutputStream(), 401, Map.of(
                        "Content-Type", List.of("text/plain; charset=utf-8"),
                        "WWW-Authenticate", List.of("Basic realm=\"King-Lavalink Admin\", charset=\"UTF-8\"")),
                        "Authentication required".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (request.headers.getOrDefault("upgrade", "").equalsIgnoreCase("websocket")) {
                if (isLavalinkPath(path)) {
                    socket.setSoTimeout(0);
                    proxyWebSocket(socket, request);
                }
                else respond(socket.getOutputStream(), 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            } else if (request.method.equals("GET") && path.equals("/")) {
                respond(socket.getOutputStream(), 200, "text/html; charset=utf-8", panelHtml.getBytes(StandardCharsets.UTF_8));
            } else if (request.method.equals("GET") && path.equals("/api/status")) {
                status(socket.getOutputStream());
            } else if (request.method.equals("GET") && path.equals("/api/logs")) {
                json(socket.getOutputStream(), jsonArray(tail(LOG_FILE, 200)));
            } else if (request.method.equals("GET") && path.equals("/api/logs/download")) {
                byte[] log = Files.exists(LOG_FILE) ? Files.readAllBytes(LOG_FILE) : new byte[0];
                writeResponse(socket.getOutputStream(), 200, Map.of(
                        "Content-Type", List.of("text/plain; charset=utf-8"),
                        "Content-Disposition", List.of("attachment; filename=\"lavalink.log\""),
                        "X-Content-Type-Options", List.of("nosniff")), log);
            } else if (request.method.equals("GET") && path.equals("/api/history")) {
                json(socket.getOutputStream(), historyJson());
            } else if (isLavalinkPath(path)) {
                proxyHttp(socket.getOutputStream(), request);
            } else {
                respond(socket.getOutputStream(), 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            System.err.println("Falha ao atender requisição: " + error.getMessage());
        }
    }

    private static boolean isLavalinkPath(String path) {
        return path.equals("/version") || path.startsWith("/v3/") || path.startsWith("/v4/");
    }

    private static boolean isAdminPath(String path) {
        return path.equals("/") || path.startsWith("/api/");
    }

    private static boolean authorized(Request request) {
        String header = request.headers.getOrDefault("authorization", "");
        if (!header.startsWith("Basic ")) return false;
        try {
            byte[] supplied = Base64.getDecoder().decode(header.substring(6));
            byte[] expected = Base64.getEncoder().encode(("admin:" + panelPassword).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(supplied, expected);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static void status(OutputStream client) throws IOException {
        String checkedAt = Instant.now().toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://127.0.0.1:" + lavalinkPort + "/v4/stats").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Authorization", lavalinkPassword);
            byte[] stats = connection.getInputStream().readAllBytes();
            recordHistory(checkedAt, new String(stats, StandardCharsets.UTF_8));
            json(client, "{\"checkedAt\":" + quote(checkedAt) + ",\"connected\":true,\"stats\":"
                    + new String(stats, StandardCharsets.UTF_8) + "}");
        } catch (Exception error) {
            json(client, "{\"checkedAt\":" + quote(checkedAt) + ",\"connected\":false,\"error\":"
                    + quote(error.getMessage() == null ? "Lavalink indisponível" : error.getMessage()) + "}");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void recordHistory(String at, String stats) throws IOException {
        String players = jsonNumber(stats, "players", "0");
        String playing = jsonNumber(stats, "playingPlayers", "0");
        String uptime = jsonNumber(stats, "uptime", "null");
        String entry = "{\"at\":" + quote(at) + ",\"players\":" + players
                + ",\"playingPlayers\":" + playing + ",\"uptime\":" + uptime + "}\n";
        Files.writeString(HISTORY_FILE, entry, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String jsonNumber(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static void proxyHttp(OutputStream client, Request request) throws IOException {
        try (Socket upstream = new Socket("127.0.0.1", lavalinkPort)) {
            upstream.setSoTimeout(120_000);
            OutputStream upstreamOut = upstream.getOutputStream();
            upstreamOut.write((request.method + " " + request.target + " " + request.version + "\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            for (String line : request.headerLines) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("host:") && !lower.startsWith("connection:")
                        && !lower.startsWith("upgrade:") && !lower.startsWith("transfer-encoding:")) {
                    upstreamOut.write((line + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                }
            }
            upstreamOut.write(("Host: 127.0.0.1:" + lavalinkPort + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            upstreamOut.write(request.body);
            upstreamOut.flush();
            upstream.getInputStream().transferTo(client);
            client.flush();
        }
    }

    private static void proxyWebSocket(Socket client, Request request) throws IOException, InterruptedException {
        Socket upstream = new Socket("127.0.0.1", lavalinkPort);
        upstream.setSoTimeout(0);
        OutputStream upstreamOut = upstream.getOutputStream();
        upstreamOut.write((request.method + " " + request.target + " " + request.version + "\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        for (String line : request.headerLines) {
            if (!line.toLowerCase(Locale.ROOT).startsWith("host:")) {
                upstreamOut.write((line + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        upstreamOut.write(("Host: 127.0.0.1:" + lavalinkPort + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        upstreamOut.flush();
        byte[] handshake = readHeader(upstream.getInputStream());
        client.getOutputStream().write(handshake);
        client.getOutputStream().flush();
        Thread inbound = new Thread(() -> pipe(client, upstream), "king-lavalink-ws-in");
        Thread outbound = new Thread(() -> pipe(upstream, client), "king-lavalink-ws-out");
        inbound.start();
        outbound.start();
        inbound.join();
        outbound.join();
        upstream.close();
    }

    private static void pipe(Socket source, Socket destination) {
        try {
            source.getInputStream().transferTo(destination.getOutputStream());
            destination.shutdownOutput();
        } catch (IOException ignored) {
            try { destination.close(); } catch (IOException ignoredAgain) {}
        }
    }

    private static byte[] readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int state = 0;
        while (header.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) throw new IOException("Conexão WebSocket fechada durante handshake");
            header.write(value);
            state = switch (state) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> state;
            };
            if (state == 4) return header.toByteArray();
        }
        throw new IOException("Cabeçalho WebSocket muito grande");
    }

    private static void writeResponse(OutputStream output, int status, Map<String, List<String>> headers, byte[] body)
            throws IOException {
        String reason = switch (status) {
            case 200 -> "OK"; case 201 -> "Created"; case 204 -> "No Content";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 404 -> "Not Found";
            case 409 -> "Conflict"; case 429 -> "Too Many Requests"; default -> "Upstream Response";
        };
        output.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key == null || List.of("connection", "transfer-encoding", "content-length", "keep-alive")
                    .contains(key.toLowerCase(Locale.ROOT))) continue;
            for (String value : entry.getValue()) {
                output.write((key + ": " + value + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        output.write(("Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private static void respond(OutputStream output, int status, String contentType, byte[] body) throws IOException {
        writeResponse(output, status, Map.of("Content-Type", List.of(contentType)), body);
    }

    private static void json(OutputStream output, String value) throws IOException {
        respond(output, 200, "application/json; charset=utf-8", value.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonArray(List<String> rows) {
        return "[" + String.join(",", rows) + "]";
    }

    private static List<String> tail(Path path, int limit) throws IOException {
        List<String> rows = tailRaw(path, limit);
        List<String> result = new ArrayList<>(rows.size());
        for (String line : rows) result.add(quote(line));
        return result;
    }

    private static String historyJson() throws IOException {
        List<String> rows = tailRaw(HISTORY_FILE, 500);
        rows.removeIf(line -> !line.startsWith("{") || !line.endsWith("}"));
        return "[" + String.join(",", rows) + "]";
    }

    private static List<String> tailRaw(Path path, int limit) throws IOException {
        if (!Files.exists(path)) return List.of();
        Deque<String> lines = new ArrayDeque<>();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == limit) lines.removeFirst();
                lines.addLast(line);
            }
        }
        return new ArrayList<>(lines);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static void writeResponse(OutputStream output, int status, String contentType, byte[] body)
            throws IOException {
        writeResponse(output, status, Map.of("Content-Type", List.of(contentType)), body);
    }

    private static final class Request {
        final String method;
        final String target;
        final String version;
        final List<String> headerLines;
        final Map<String, String> headers;
        final byte[] body;

        private Request(String method, String target, String version, List<String> lines,
                        Map<String, String> headers, byte[] body) {
            this.method = method; this.target = target; this.version = version;
            this.headerLines = lines; this.headers = headers; this.body = body;
        }

        static Request read(InputStream input) throws IOException {
            byte[] bytes = readHeader(input);
            String[] rows = new String(bytes, StandardCharsets.ISO_8859_1).split("\\r\\n");
            if (rows.length == 0 || rows[0].isBlank()) return null;
            String[] first = rows[0].split(" ", 3);
            if (first.length != 3) throw new IOException("Linha HTTP inválida");
            List<String> lines = new ArrayList<>();
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < rows.length; i++) {
                if (rows[i].isEmpty()) break;
                lines.add(rows[i]);
                int colon = rows[i].indexOf(':');
                if (colon > 0) headers.put(rows[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        rows[i].substring(colon + 1).trim());
            }
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            if (length < 0 || length > MAX_BODY_BYTES) throw new IOException("Corpo HTTP muito grande");
            byte[] body = input.readNBytes(length);
            if (body.length != length) throw new IOException("Corpo HTTP incompleto");
            return new Request(first[0], first[1], first[2], lines, headers, body);
        }
    }
}

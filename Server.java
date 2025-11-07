import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    // Track visits in memory (for private/unpublished games)
    private static ConcurrentHashMap<Integer, Integer> visitsMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/user", new UserHandler());
        server.createContext("/visits", new VisitsHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
    }

    // ---------------- POST /visits to increment a user's visits ----------------
    static class VisitsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"POST only\"}");
                return;
            }

            Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
            String body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();

            try {
                // Expecting JSON: {"userId": 12345}
                Pattern userIdPattern = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)");
                Matcher matcher = userIdPattern.matcher(body);
                if (matcher.find()) {
                    int userId = Integer.parseInt(matcher.group(1));
                    visitsMap.putIfAbsent(userId, 0);
                    visitsMap.put(userId, visitsMap.get(userId) + 1);

                    String json = "{ \"success\": true, \"totalVisits\": " + visitsMap.get(userId) + " }";
                    sendResponse(exchange, 200, json);
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid request\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid request\"}");
            }
        }
    }

    // ---------------- GET /user/{userId} ----------------
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "{\"error\":\"GET only\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String userIdStr = path.replaceFirst("/user/?", "").trim();

            if (userIdStr.isEmpty() || !userIdStr.matches("\\d+")) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid UserId\"}");
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            // Calculate totalVisits: API + internal map
            int totalVisits = getTotalVisits(userId) + visitsMap.getOrDefault(userId, 0);
            int followers = getFollowers(userId);
            String joinDate = getJoinDate(userId);

            // Build JSON response
            String json = "{"
                    + "\"totalVisits\":" + totalVisits + ","
                    + "\"followers\":" + followers + ","
                    + "\"joinDate\":\"" + joinDate + "\""
                    + "}";

            sendResponse(exchange, 200, json);
        }
    }

    // ---------------- Roblox API calls ----------------
    private static int getFollowers(int userId) {
        try {
            URL url = new URL("https://friends.roblox.com/v1/users/" + userId + "/followers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return 0;
            }

            Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            conn.disconnect();

            Pattern p = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");
            Matcher m = p.matcher(response);
            if (m.find()) return Integer.parseInt(m.group(1));

        } catch (Exception ignored) {}
        return 0;
    }

    private static String getJoinDate(int userId) {
        try {
            URL url = new URL("https://users.roblox.com/v1/users/" + userId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return "Unknown";
            }

            Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            conn.disconnect();

            Pattern p = Pattern.compile("\"created\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(response);
            if (m.find()) return m.group(1).trim();

        } catch (Exception ignored) {}
        return "Unknown";
    }

    private static int getTotalVisits(int userId) {
        int totalVisits = 0;
        try {
            String baseUrl = "https://games.roblox.com/v2/users/" + userId + "/games?sortOrder=Asc&limit=100";
            String nextUrl = baseUrl;

            Pattern visitsPattern = Pattern.compile("\"placeVisits\"\\s*:\\s*(\\d+)");
            Pattern cursorPattern = Pattern.compile("\"nextPageCursor\"\\s*:\\s*\"([^\"]+)\"");

            while (nextUrl != null) {
                URL url = new URL(nextUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    break;
                }

                Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                conn.disconnect();

                Matcher vm = visitsPattern.matcher(response);
                while (vm.find()) {
                    try { totalVisits += Integer.parseInt(vm.group(1)); }
                    catch (NumberFormatException ignored) {}
                }

                Matcher cm = cursorPattern.matcher(response);
                if (cm.find()) {
                    String cursor = cm.group(1);
                    nextUrl = (cursor != null && !cursor.isEmpty()) ? baseUrl + "&cursor=" + URLEncoder.encode(cursor, "UTF-8") : null;
                } else {
                    nextUrl = null;
                }
            }

        } catch (Exception ignored) {}
        return totalVisits;
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

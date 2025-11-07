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

    // ---------------- POST /visits ----------------
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

            int totalVisits = getTotalVisits(userId) + visitsMap.getOrDefault(userId, 0);
            int followers = getFollowers(userId);
            String joinDate = getJoinDate(userId);

            String json = "{"
                    + "\"totalVisits\":" + totalVisits + ","
                    + "\"followers\":" + followers + ","
                    + "\"joinDate\":\"" + joinDate + "\""
                    + "}";

            sendResponse(exchange, 200, json);
        }
    }

    // ---------------- Roblox API Calls ----------------
    private static int getFollowers(int userId) {
        try {
            URL url = new URL("https://friends.roproxy.com/v1/users/" + userId + "/followers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) return 0;

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
            URL url = new URL("https://users.roproxy.com/v1/users/" + userId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) return "Unknown";

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
            String baseUrl = "https://games.roproxy.com/v2/users/" + userId + "/games?sortOrder=Asc&limit=100";
            String nextCursor = null;

            do {
                String urlStr = nextCursor != null ? baseUrl + "&cursor=" + URLEncoder.encode(nextCursor, "UTF-8") : baseUrl;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != 200) break;

                Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                conn.disconnect();

                Matcher vm = Pattern.compile("\"placeVisits\"\\s*:\\s*(\\d+)").matcher(response);
                while (vm.find()) {
                    try { totalVisits += Integer.parseInt(vm.group(1)); } catch (NumberFormatException ignored) {}
                }

                Matcher cm = Pattern.compile("\"nextPageCursor\"\\s*:\\s*(null|\"([^\"]*)\")").matcher(response);
                if (cm.find()) nextCursor = cm.group(2) != null ? cm.group(2) : null;
                else nextCursor = null;

                if (nextCursor != null) Thread.sleep(200);

            } while (nextCursor != null && !nextCursor.isEmpty());

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

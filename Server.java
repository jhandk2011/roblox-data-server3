import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject; // Add org.json library

public class Server {

    private static ConcurrentHashMap<Integer, Integer> visitsMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/visits", new VisitsHandler());
        server.createContext("/user", new UserHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server running on port " + port);
    }

    // ---------------- Increment visits ----------------
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
                JSONObject json = new JSONObject(body);
                int userId = json.getInt("userId");
                visitsMap.putIfAbsent(userId, 0);
                visitsMap.put(userId, visitsMap.get(userId) + 1);

                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("totalVisits", visitsMap.get(userId));
                sendResponse(exchange, 200, response.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 400, "{\"error\":\"Invalid request\"}");
            }
        }
    }

    // ---------------- User stats ----------------
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
            int visits = visitsMap.getOrDefault(userId, 0);

            int followers = getFollowers(userId);
            String joinDate = getJoinDate(userId);
            int favorites = getFavorites(userId); // optional
            int activePlayers = getActivePlayers(userId); // optional

            JSONObject response = new JSONObject();
            response.put("totalVisits", visits);
            response.put("followers", followers);
            response.put("joinDate", joinDate);
            response.put("favorites", favorites);
            response.put("activePlayers", activePlayers);

            sendResponse(exchange, 200, response.toString());
        }
    }

    // ---------------- Roblox API calls ----------------
    private static int getFollowers(int userId) {
        try {
            URL url = new URL("https://friends.roblox.com/v1/users/" + userId + "/followers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() != 200) return 0;

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();
            int start = response.indexOf("\"count\":") + 8;
            int end = response.indexOf("}", start);
            return Integer.parseInt(response.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getJoinDate(int userId) {
        try {
            URL url = new URL("https://users.roblox.com/v1/users/" + userId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() != 200) return "Unknown";

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();

            int start = response.indexOf("\"created\":\"") + 10;
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // Placeholder: always 0 (can implement later via Roblox APIs if needed)
    private static int getFavorites(int userId) { return 0; }
    private static int getActivePlayers(int userId) { return 0; }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes()); }
    }
}

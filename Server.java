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

    // In-memory visits storage
    private static ConcurrentHashMap<Integer, Integer> visitsMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoint to increment visits
        server.createContext("/visits", new VisitsHandler());

        // Endpoint to fetch user stats
        server.createContext("/user", new UserHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
    }

    // ---------------- Visits Increment ----------------
    static class VisitsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\": \"POST only\"}");
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
                sendResponse(exchange, 400, "{\"error\": \"Invalid request\"}");
            }
        }
    }

    // ---------------- User Stats ----------------
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "{\"error\": \"GET only\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String userIdStr = path.replace("/user/", "").trim();
            if (userIdStr.isEmpty() || !userIdStr.matches("\\d+")) {
                sendResponse(exchange, 400, "{\"error\": \"Invalid UserId\"}");
                return;
            }

            int userId = Integer.parseInt(userIdStr);
            int visits = visitsMap.getOrDefault(userId, 0);

            int followers = getFollowers(userId);
            String joinDate = getJoinDate(userId);

            JSONObject response = new JSONObject();
            response.put("totalVisits", visits);
            response.put("followers", followers);
            response.put("joinDate", joinDate);

            sendResponse(exchange, 200, response.toString());
        }
    }

    // ---------------- Roblox API Calls ----------------
    private static int getFollowers(int userId) {
        try {
            URL url = new URL("https://friends.roblox.com/v1/users/" + userId + "/followers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) return 0;

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();

            String search = "\"count\":";
            int index = response.indexOf(search);
            if (index == -1) return 0;
            int start = index + search.length();
            int end = response.indexOf("}", start);
            String numberStr = response.substring(start, end).trim();
            return Integer.parseInt(numberStr);
        } catch (Exception e) {
            e.printStackTrace();
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

            String search = "\"created\":\"";
            int index = response.indexOf(search);
            if (index == -1) return "Unknown";
            int start = index + search.length();
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

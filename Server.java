import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONObject;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/user", new UserHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }

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

            int totalVisits = getTotalVisits(userId);
            int followers = getFollowers(userId);
            String joinDate = getJoinDate(userId);

            JSONObject response = new JSONObject();
            response.put("totalVisits", totalVisits);
            response.put("followers", followers);
            response.put("joinDate", joinDate);

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

            String search = "\"count\":";
            int index = response.indexOf(search);
            if (index == -1) return 0;

            int start = index + search.length();
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

            String search = "\"created\":\"";
            int index = response.indexOf(search);
            if (index == -1) return "Unknown";

            int start = index + search.length();
            int end = response.indexOf("\"", start);
            if (end == -1) end = response.length();
            return response.substring(start, end).trim();

        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static int getTotalVisits(int userId) {
        int totalVisits = 0;
        try {
            String apiUrl = "https://games.roblox.com/v2/users/" + userId + "/games?sortOrder=Asc&limit=100";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) return 0;

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();

            String search = "\"placeVisits\":";
            int index = 0;
            while ((index = response.indexOf(search, index)) != -1) {
                int start = index + search.length();
                int end = response.indexOf(",", start);
                if (end == -1) end = response.indexOf("}", start);
                if (end == -1) break;
                String numberStr = response.substring(start, end).trim();
                try {
                    totalVisits += Integer.parseInt(numberStr);
                } catch (Exception ignored) {}
                index = end;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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

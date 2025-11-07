import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new PlayerHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
    }

    static class PlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String userId = path.replace("/", "").trim();

            if (userId.isEmpty() || !userId.matches("\\d+")) {
                sendResponse(exchange, 400, "{\"error\": \"Invalid UserId\"}");
                return;
            }

            try {
                int followers = getFollowers(userId);
                int totalVisits = getTotalVisits(userId);

                String json = String.format("{\"totalVisits\": %d, \"followers\": %d}", totalVisits, followers);
                sendResponse(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Failed to fetch data\"}");
            }
        }

        private static int getFollowers(String userId) {
            try {
                URL url = new URL("https://friends.roblox.com/v1/users/" + userId + "/followers/count");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                int status = conn.getResponseCode();
                if (status != 200) return 0;

                String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                conn.disconnect();

                // Parse count manuelt
                String search = "\"count\":";
                int index = response.indexOf(search);
                if (index == -1) return 0;
                int start = index + search.length();
                int end = response.indexOf("}", start);
                String numberStr = response.substring(start, end).trim();
                return Integer.parseInt(numberStr);

            } catch (Exception e) {
                return 0;
            }
        }

        private static int getTotalVisits(String userId) {
            int totalVisits = 0;
            try {
                String apiUrl = "https://games.roblox.com/v2/users/" + userId + "/games?sortOrder=Asc&limit=100";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                int status = conn.getResponseCode();
                if (status != 200) return 0;

                String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                conn.disconnect();

                // Parse placeVisits manuelt
                String search = "\"placeVisits\":";
                int index = 0;
                while ((index = response.indexOf(search, index)) != -1) {
                    int start = index + search.length();
                    int end = response.indexOf(",", start);
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
}

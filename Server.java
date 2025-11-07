import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

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
                String joinDate = getJoinDate(userId);

                String json = String.format(
                    "{\"followers\": %d, \"totalVisits\": %d, \"joinDate\": \"%s\"}",
                    followers, totalVisits, joinDate
                );

                sendResponse(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Failed to fetch data\"}");
            }
        }

        // Get followers count
        private static int getFollowers(String userId) {
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

        // Get total visits across all your public games
        private static int getTotalVisits(String userId) {
            int totalVisits = 0;
            try {
                String apiUrl = "https://games.roblox.com/v2/users/" + userId + "/games?accessFilter=Public&limit=100";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != 200) return 0;

                String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                conn.disconnect();

                // Extract universeIds
                List<String> universeIds = new ArrayList<>();
                String search = "\"universeId\":";
                int index = 0;
                while ((index = response.indexOf(search, index)) != -1) {
                    int start = index + search.length();
                    int end = response.indexOf(",", start);
                    if (end == -1) break;
                    String id = response.substring(start, end).trim();
                    universeIds.add(id);
                    index = end;
                }

                // Fetch visits for each universeId
                for (String universeId : universeIds) {
                    String visitsUrl = "https://games.roblox.com/v1/games?universeIds=" + universeId;
                    URL url2 = new URL(visitsUrl);
                    HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
                    conn2.setRequestMethod("GET");
                    conn2.setRequestProperty("User-Agent", "Mozilla/5.0");

                    if (conn2.getResponseCode() != 200) continue;

                    String visitsResponse = new Scanner(conn2.getInputStream()).useDelimiter("\\A").next();
                    conn2.disconnect();

                    String visitSearch = "\"visits\":";
                    int visitIndex = visitsResponse.indexOf(visitSearch);
                    if (visitIndex != -1) {
                        int start = visitIndex + visitSearch.length();
                        int end = visitsResponse.indexOf(",", start);
                        if (end == -1) end = visitsResponse.indexOf("}", start);
                        if (end != -1) {
                            String numberStr = visitsResponse.substring(start, end).trim();
                            try {
                                totalVisits += Integer.parseInt(numberStr);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return totalVisits;
        }

        // Get join date
        private static String getJoinDate(String userId) {
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
}

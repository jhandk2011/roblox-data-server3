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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            // Build JSON manually
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
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return 0;
            }

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").hasNext() ? new Scanner(conn.getInputStream()).useDelimiter("\\A").next() : "";
            conn.disconnect();

            // Robust JSON extraction for "count"
            Pattern p = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");
            Matcher m = p.matcher(response);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            } else {
                return 0;
            }
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
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return "Unknown";
            }

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").hasNext() ? new Scanner(conn.getInputStream()).useDelimiter("\\A").next() : "";
            conn.disconnect();

            // Use regex to extract the created date value precisely
            Pattern p = Pattern.compile("\"created\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(response);
            if (m.find()) {
                return m.group(1).trim();
            } else {
                return "Unknown";
            }

        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static int getTotalVisits(int userId) {
        int totalVisits = 0;
        try {
            String baseUrl = "https://games.roblox.com/v2/users/" + userId + "/games?sortOrder=Asc&limit=100";
            String nextUrl = baseUrl;
            Pattern visitsPattern = Pattern.compile("\"(?:placeVisits|visits)\"\\s*:\\s*(\\d+)");
            Pattern cursorPattern = Pattern.compile("\"nextPageCursor\"\\s*:\\s*\"([^\"]+)\"");
            while (nextUrl != null) {
                URL url = new URL(nextUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Accept", "application/json");

                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    break;
                }

                String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").hasNext() ? new Scanner(conn.getInputStream()).useDelimiter("\\A").next() : "";
                conn.disconnect();

                // Sum up all occurrences of placeVisits or visits
                Matcher vm = visitsPattern.matcher(response);
                while (vm.find()) {
                    try {
                        totalVisits += Integer.parseInt(vm.group(1));
                    } catch (NumberFormatException ignored) {}
                }

                // Find nextPageCursor to continue pagination (if present)
                Matcher cm = cursorPattern.matcher(response);
                if (cm.find()) {
                    String cursor = cm.group(1);
                    if (cursor == null || cursor.isEmpty()) {
                        nextUrl = null;
                    } else {
                        // Append cursor param properly (URL-encode it)
                        String encoded = URLEncoder.encode(cursor, "UTF-8");
                        nextUrl = baseUrl + "&cursor=" + encoded;
                    }
                } else {
                    nextUrl = null;
                }
            }

        } catch (Exception e) {
            // If something fails, return what we've accumulated so far (could be 0)
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

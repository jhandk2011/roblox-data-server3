import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
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

            int followers = getFollowers(userId);

            // Return only followers in JSON
            String json = "{ \"followers\": " + followers + " }";

            sendResponse(exchange, 200, json);
        }
    }

    // ---------------- Roblox API call for followers ----------------
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

            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").hasNext()
                    ? new Scanner(conn.getInputStream()).useDelimiter("\\A").next() : "";
            conn.disconnect();

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

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

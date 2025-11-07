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
            if (userId.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\": \"Missing UserId\"}");
                return;
            }

            try {
                int followers = getFollowers(userId);
                int visits = getVisits(userId);

                String json = String.format("{\"totalVisits\": %d, \"followers\": %d}", visits, followers);
                sendResponse(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Failed to fetch data\"}");
            }
        }

        private static int getFollowers(String userId) throws IOException {
            URL url = new URL("https://friends.roblox.com/v1/users/" + userId + "/followers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            String json = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();

            String countStr = json.replaceAll("\\D+", "");
            return countStr.isEmpty() ? 0 : Integer.parseInt(countStr);
        }

        private static int getVisits(String userId) throws IOException {
            URL url = new URL("https://games.roblox.com/v2/users/" + userId + "/games");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            String json = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            conn.disconnect();

            int totalVisits = 0;
            String[] parts = json.split("\"visits\":");
            for (int i = 1; i < parts.length; i++) {
                String number = parts[i].split("[,}]")[0];
                try {
                    totalVisits += Integer.parseInt(number.trim());
                } catch (Exception ignored) {}
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

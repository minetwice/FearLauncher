package net.kdt.pojavlaunch.authenticator;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import androidx.preference.PreferenceManager;
import net.kdt.pojavlaunch.Tools;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class LocalSkinServer {
    private static final String TAG = "LocalSkinServer";
    private static final int PORT = 25599;
    private static Thread sServerThread = null;
    private static ServerSocket sServerSocket = null;
    private static volatile boolean sRunning = false;

    public static synchronized void start(Context context) {
        if (sRunning) return;
        sRunning = true;
        sServerThread = new Thread(() -> {
            try {
                sServerSocket = new ServerSocket(PORT);
                Log.i(TAG, "Local Skin Server started on port " + PORT);
                while (sRunning) {
                    Socket socket = sServerSocket.accept();
                    handleClient(context, socket);
                }
            } catch (Exception e) {
                Log.d(TAG, "Server socket closed: " + e.getMessage());
            }
        });
        sServerThread.setDaemon(true);
        sServerThread.start();
    }

    public static synchronized void stop() {
        sRunning = false;
        if (sServerSocket != null) {
            try {
                sServerSocket.close();
            } catch (IOException ignored) {}
            sServerSocket = null;
        }
        if (sServerThread != null) {
            sServerThread.interrupt();
            sServerThread = null;
        }
        Log.i(TAG, "Local Skin Server stopped.");
    }

    private static void handleClient(Context context, Socket socket) {
        new Thread(() -> {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line = reader.readLine();
                if (line == null) return;

                String[] parts = line.split(" ");
                if (parts.length < 2) return;

                String method = parts[0];
                String path = parts[1];

                if (path.equals("/")) {
                    sendJson(out, "{\n" +
                            "  \"meta\": {\n" +
                            "    \"serverName\": \"FEARSkinServer\",\n" +
                            "    \"implementationName\": \"FEARSkinServer\",\n" +
                            "    \"implementationVersion\": \"1.0.0\"\n" +
                            "  },\n" +
                            "  \"skinDomains\": [\n" +
                            "    \"127.0.0.1\",\n" +
                            "    \"localhost\"\n" +
                            "  ]\n" +
                            "}");
                } else if (path.startsWith("/session/minecraft/profile/")) {
                    String[] pathParts = path.split("/");
                    String uuid = pathParts[pathParts.length - 1];
                    String username = "Player";

                    // Retrieve active username from selected account
                    try {
                        net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount currentAcc = net.kdt.pojavlaunch.authenticator.accounts.Accounts.getCurrent();
                        if (currentAcc != null) {
                            username = currentAcc.username;
                        }
                    } catch (Exception ignored) {}

                    String texturesJson = "{\n" +
                            "  \"timestamp\": " + System.currentTimeMillis() + ",\n" +
                            "  \"profileId\": \"" + uuid + "\",\n" +
                            "  \"profileName\": \"" + username + "\",\n" +
                            "  \"textures\": {\n" +
                            "    \"SKIN\": {\n" +
                            "      \"url\": \"http://127.0.0.1:" + PORT + "/skin.png\"\n" +
                            "    }\n" +
                            "  }\n" +
                            "}";

                    String texturesBase64 = Base64.encodeToString(texturesJson.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

                    String profileJson = "{\n" +
                            "  \"id\": \"" + uuid + "\",\n" +
                            "  \"name\": \"" + username + "\",\n" +
                            "  \"properties\": [\n" +
                            "    {\n" +
                            "      \"name\": \"textures\",\n" +
                            "      \"value\": \"" + texturesBase64 + "\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}";

                    sendJson(out, profileJson);
                } else if (path.equals("/skin.png")) {
                    sendSkin(context, out);
                } else {
                    send404(out);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error handling client", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }).start();
    }

    private static void sendJson(OutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private static void sendSkin(Context context, OutputStream out) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String activeSkinPath = prefs.getString("active_skin_path", "steve");

        byte[] skinBytes = null;
        if (!"steve".equals(activeSkinPath) && !"alex".equals(activeSkinPath)) {
            File skinFile = new File(activeSkinPath);
            if (skinFile.exists()) {
                skinBytes = readAllBytes(skinFile);
            }
        }

        if (skinBytes == null) {
            // Default Steve skin fallback
            try {
                byte[] decoded = Base64.decode(com.kdt.mcgui.MinecraftSkinView.DEFAULT_STEVE_BASE64, Base64.DEFAULT);
                if (decoded != null) {
                    skinBytes = decoded;
                }
            } catch (Exception ignored) {}
        }

        if (skinBytes != null) {
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/png\r\n" +
                    "Content-Length: " + skinBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(skinBytes);
        } else {
            send404(out);
        }
        out.flush();
    }

    private static void send404(OutputStream out) throws IOException {
        String resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            int numRead = 0;
            while (offset < data.length && (numRead = fis.read(data, offset, data.length - offset)) >= 0) {
                offset += numRead;
            }
            return data;
        }
    }
}

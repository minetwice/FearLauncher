package net.kdt.pojavlaunch.skins;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalSkinServer {
    private static final String TAG = "LocalSkinServer";
    private static final int PORT = 25599;
    private static LocalSkinServer sInstance;

    private ServerSocket mServerSocket;
    private ExecutorService mThreadPool;
    private boolean mIsRunning = false;

    private KeyPair mKeyPair;
    private String mPemPublicKey;
    private String mUsername = "Steve";
    private String mUserUuid = "00000000000000000000000000000000";
    private boolean mIsAlex = false;
    private String mActiveSkinPath = "steve";
    private net.kdt.pojavlaunch.authenticator.AuthType mAuthType = net.kdt.pojavlaunch.authenticator.AuthType.LOCAL;
    private Context mContext;

    public static synchronized LocalSkinServer getInstance() {
        if (sInstance == null) {
            sInstance = new LocalSkinServer();
        }
        return sInstance;
    }

    private LocalSkinServer() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(1024);
            mKeyPair = kpg.generateKeyPair();
            PublicKey publicKey = mKeyPair.getPublic();
            mPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP) +
                    "\n-----END PUBLIC KEY-----";
            Log.i(TAG, "Generated RSA keypair for LocalSkinServer successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate RSA keypair", e);
        }
    }

    public synchronized void start(Context context, MinecraftAccount account) {
        if (mIsRunning) {
            stop();
        }
        mContext = context.getApplicationContext();
        if (account != null) {
            mUsername = account.username;
            mUserUuid = account.profileId != null ? account.profileId.replace("-", "").toLowerCase() : "00000000000000000000000000000000";
            mAuthType = account.authType;
        } else {
            mAuthType = net.kdt.pojavlaunch.authenticator.AuthType.LOCAL;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mActiveSkinPath = prefs.getString("active_skin_path", "steve");
        mIsAlex = prefs.getBoolean("active_skin_is_alex", false);

        Log.i(TAG, "Starting LocalSkinServer for " + mUsername + " (" + mUserUuid + "), skin path: " + mActiveSkinPath + ", authType: " + mAuthType);

        mIsRunning = true;
        mThreadPool = Executors.newCachedThreadPool();

        try {
            mServerSocket = new ServerSocket();
            mServerSocket.setReuseAddress(true);
            mServerSocket.bind(new InetSocketAddress("127.0.0.1", PORT));

            mThreadPool.execute(this::acceptLoop);
            Log.i(TAG, "LocalSkinServer successfully started on port " + PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start LocalSkinServer ServerSocket", e);
            mIsRunning = false;
        }
    }

    public synchronized void stop() {
        mIsRunning = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
                Log.i(TAG, "LocalSkinServer ServerSocket closed.");
            } catch (Exception e) {
                Log.e(TAG, "Error closing LocalSkinServer ServerSocket", e);
            }
            mServerSocket = null;
        }
        if (mThreadPool != null) {
            try {
                mThreadPool.shutdownNow();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down thread pool", e);
            }
            mThreadPool = null;
        }
    }

    private void acceptLoop() {
        while (mIsRunning) {
            try {
                Socket client = mServerSocket.accept();
                if (mThreadPool != null && !mThreadPool.isShutdown()) {
                    mThreadPool.execute(() -> handleClient(client));
                } else {
                    client.close();
                }
            } catch (Exception e) {
                if (mIsRunning) {
                    Log.e(TAG, "Error in acceptLoop", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket s = client;
             InputStream is = s.getInputStream();
             OutputStream os = s.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;

            String method = parts[0];
            String path = parts[1];

            // Drain remaining headers and check content length
            int contentLength = 0;
            String contentTypeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {}
                } else if (line.toLowerCase().startsWith("content-type:")) {
                    contentTypeHeader = line.substring(13).trim();
                }
            }
            byte[] requestBody = null;
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(bodyChars, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                String bodyStr = new String(bodyChars);
                requestBody = bodyStr.getBytes(StandardCharsets.UTF_8);
            }

            if (path.equals("/") || path.equals("")) {
                // Root metadata endpoint
                JsonObject response = new JsonObject();
                JsonObject meta = new JsonObject();
                meta.addProperty("serverName", "FEAR Local Skin Server");
                meta.addProperty("implementationName", "LocalSkinServer");
                meta.addProperty("implementationVersion", "1.0.0");
                response.add("meta", meta);

                JsonArray skinDomains = new JsonArray();
                skinDomains.add("localhost");
                skinDomains.add("127.0.0.1");
                skinDomains.add("textures.minecraft.net");
                skinDomains.add("farmer-my1t.onrender.com");
                response.add("skinDomains", skinDomains);

                response.addProperty("signaturePublickey", mPemPublicKey);

                byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
                sendResponse(os, 200, "application/json; charset=utf-8", body);
            } else if (path.startsWith("/sessionserver/session/minecraft/join")) {
                if (mAuthType == net.kdt.pojavlaunch.authenticator.AuthType.CRAFTYN_MC) {
                    int[] statusCode = new int[1];
                    String[] outContentType = new String[1];
                    byte[] responseBody = proxyRequest(method, path, requestBody, contentTypeHeader, statusCode, outContentType);
                    sendResponse(os, statusCode[0], outContentType[0] != null ? outContentType[0] : "application/json; charset=utf-8", responseBody);
                } else {
                    // Join server handshake
                    Log.i(TAG, "Join handshake received");
                    sendResponse(os, 204, "application/json; charset=utf-8", new byte[0]);
                }
            } else if (path.startsWith("/sessionserver/session/minecraft/hasJoined")) {
                // Parse username first
                String username = "";
                int uIdx = path.indexOf("username=");
                if (uIdx != -1) {
                    int endIdx = path.indexOf('&', uIdx);
                    if (endIdx == -1) {
                        username = path.substring(uIdx + 9);
                    } else {
                        username = path.substring(uIdx + 9, endIdx);
                    }
                }
                Log.i(TAG, "hasJoined query received for username: " + username);

                // If verifying our own username, always serve our custom local skin directly
                if (username.equalsIgnoreCase(mUsername)) {
                    JsonObject profile = createLocalProfile(mUserUuid);
                    byte[] body = profile.toString().getBytes(StandardCharsets.UTF_8);
                    sendResponse(os, 200, "application/json; charset=utf-8", body);
                } else if (mAuthType == net.kdt.pojavlaunch.authenticator.AuthType.CRAFTYN_MC) {
                    int[] statusCode = new int[1];
                    String[] outContentType = new String[1];
                    byte[] responseBody = proxyRequest(method, path, requestBody, contentTypeHeader, statusCode, outContentType);
                    if (statusCode[0] == 200 && responseBody != null && responseBody.length > 0) {
                        try {
                            String respStr = new String(responseBody, StandardCharsets.UTF_8);
                            JsonObject profileObj = new Gson().fromJson(respStr, JsonObject.class);
                            JsonObject resignedObj = resignProfile(profileObj);
                            byte[] resignedBody = resignedObj.toString().getBytes(StandardCharsets.UTF_8);
                            sendResponse(os, 200, "application/json; charset=utf-8", resignedBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse/resign Craftyn hasJoined profile", e);
                            sendResponse(os, statusCode[0], outContentType[0] != null ? outContentType[0] : "application/json; charset=utf-8", responseBody);
                        }
                    } else {
                        sendResponse(os, statusCode[0], outContentType[0] != null ? outContentType[0] : "application/json; charset=utf-8", responseBody);
                    }
                } else {
                    String fetchedUuid = fetchUuidByUsername(username);
                    if (fetchedUuid != null) {
                        JsonObject mojangProfile = fetchMojangProfile(fetchedUuid);
                        if (mojangProfile != null) {
                            JsonObject signedProfile = resignProfile(mojangProfile);
                            byte[] body = signedProfile.toString().getBytes(StandardCharsets.UTF_8);
                            sendResponse(os, 200, "application/json; charset=utf-8", body);
                        } else {
                            sendResponse(os, 204, "application/json; charset=utf-8", new byte[0]);
                        }
                    } else {
                        sendResponse(os, 204, "application/json; charset=utf-8", new byte[0]);
                    }
                }
            } else if (path.startsWith("/sessionserver/session/minecraft/profile/")) {
                // Profile endpoint
                String uuidStr = path.substring(path.lastIndexOf('/') + 1);
                // Strip optional query params if present, e.g. ?unsigned=false
                int qIdx = uuidStr.indexOf('?');
                if (qIdx != -1) {
                    uuidStr = uuidStr.substring(0, qIdx);
                }
                uuidStr = uuidStr.replace("-", "").toLowerCase().trim();

                Log.i(TAG, "Profile query received for UUID: " + uuidStr);

                // Compute standard offline player UUID based on user's active username
                String offlineUuidStr = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + mUsername).getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", "").toLowerCase();

                // If querying our own UUID or offline UUID, always serve our custom local skin directly
                if (uuidStr.equals(mUserUuid) || uuidStr.equals(offlineUuidStr)) {
                    JsonObject profile = createLocalProfile(uuidStr);
                    byte[] body = profile.toString().getBytes(StandardCharsets.UTF_8);
                    sendResponse(os, 200, "application/json; charset=utf-8", body);
                } else if (mAuthType == net.kdt.pojavlaunch.authenticator.AuthType.CRAFTYN_MC) {
                    int[] statusCode = new int[1];
                    String[] outContentType = new String[1];
                    byte[] responseBody = proxyRequest(method, path, requestBody, contentTypeHeader, statusCode, outContentType);
                    if (statusCode[0] == 200 && responseBody != null && responseBody.length > 0) {
                        try {
                            String respStr = new String(responseBody, StandardCharsets.UTF_8);
                            JsonObject profileObj = new Gson().fromJson(respStr, JsonObject.class);
                            JsonObject resignedObj = resignProfile(profileObj);
                            byte[] resignedBody = resignedObj.toString().getBytes(StandardCharsets.UTF_8);
                            sendResponse(os, 200, "application/json; charset=utf-8", resignedBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse/resign Craftyn profile", e);
                            sendResponse(os, statusCode[0], outContentType[0] != null ? outContentType[0] : "application/json; charset=utf-8", responseBody);
                        }
                    } else {
                        sendResponse(os, statusCode[0], outContentType[0] != null ? outContentType[0] : "application/json; charset=utf-8", responseBody);
                    }
                } else {
                    JsonObject mojangProfile = fetchMojangProfile(uuidStr);
                    if (mojangProfile != null) {
                        JsonObject signedProfile = resignProfile(mojangProfile);
                        byte[] body = signedProfile.toString().getBytes(StandardCharsets.UTF_8);
                        sendResponse(os, 200, "application/json; charset=utf-8", body);
                    } else {
                        sendResponse(os, 204, "application/json; charset=utf-8", new byte[0]);
                    }
                }
            } else if (path.contains("/texture/") || path.contains("/textures/") || path.contains("skin")) {
                // Texture serving endpoint
                String hash = path.substring(path.lastIndexOf('/') + 1);
                int qIdx = hash.indexOf('?');
                if (qIdx != -1) {
                    hash = hash.substring(0, qIdx);
                }
                hash = hash.toLowerCase().trim();

                String myHash = getSHA256(mUserUuid).toLowerCase().trim();
                String offlineUuidStr = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + mUsername).getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", "").toLowerCase();
                String myOfflineHash = getSHA256(offlineUuidStr).toLowerCase().trim();

                if (hash.equals(myHash) || hash.equals(myOfflineHash) || hash.equals("skin")) {
                    Log.i(TAG, "Serving local skin for hash: " + hash);
                    byte[] imgBytes = null;
                    if (mActiveSkinPath != null && !mActiveSkinPath.equals("steve") && !mActiveSkinPath.equals("alex")) {
                        File skinFile = new File(mActiveSkinPath);
                        if (skinFile.exists() && skinFile.isFile()) {
                            try (FileInputStream fis = new FileInputStream(skinFile);
                                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                                byte[] buf = new byte[1024];
                                int read;
                                while ((read = fis.read(buf)) != -1) {
                                    bos.write(buf, 0, read);
                                }
                                imgBytes = bos.toByteArray();
                            }
                        }
                    }

                    if (imgBytes == null) {
                        sendResponse(os, 404, "image/png", new byte[0]);
                    } else {
                        sendResponse(os, 200, "image/png", imgBytes);
                    }
                } else {
                    // Proxy to textures.minecraft.net
                    Log.i(TAG, "Proxying texture request to textures.minecraft.net for hash: " + hash);
                    byte[] imgBytes = fetchMojangTexture(hash);
                    if (imgBytes != null) {
                        sendResponse(os, 200, "image/png", imgBytes);
                    } else {
                        sendResponse(os, 404, "image/png", new byte[0]);
                    }
                }
            } else {
                sendResponse(os, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling client connection", e);
        }
    }

    private void sendResponse(OutputStream os, int statusCode, String contentType, byte[] body) throws IOException {
        String statusStr = "200 OK";
        if (statusCode == 204) {
            statusStr = "204 No Content";
        } else if (statusCode == 404) {
            statusStr = "404 Not Found";
        }

        os.write(("HTTP/1.1 " + statusStr + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Length: " + body.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        if (body.length > 0) {
            os.write(body);
        }
        os.flush();
    }

    private String getSHA256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // Fallback to a 64-character hex string based on input hashcode
            return String.format("%064x", Math.abs(input.hashCode()));
        }
    }

    private byte[] fetchMojangTexture(String hash) {
        try {
            URL url = new URL("https://textures.minecraft.net/texture/" + hash);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        bos.write(buf, 0, read);
                    }
                    return bos.toByteArray();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to proxy texture for " + hash, e);
        }
        return null;
    }

    private JsonObject createLocalProfile(String uuid) throws Exception {
        JsonObject profile = new JsonObject();
        profile.addProperty("id", uuid);
        profile.addProperty("name", mUsername);

        JsonArray properties = new JsonArray();
        JsonObject texturesProp = new JsonObject();
        texturesProp.addProperty("name", "textures");

        JsonObject payload = new JsonObject();
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.addProperty("profileId", uuid);
        payload.addProperty("profileName", mUsername);

        JsonObject textures = new JsonObject();
        JsonObject skin = new JsonObject();
        // Point to textures.minecraft.net to pass client domain whitelisting, which authlib-injector intercepts
        String skinHash = getSHA256(uuid);
        skin.addProperty("url", "https://textures.minecraft.net/texture/" + skinHash);

        if (mIsAlex) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("model", "slim");
            skin.add("metadata", metadata);
        }

        textures.add("SKIN", skin);
        payload.add("textures", textures);

        String base64Value = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        texturesProp.addProperty("value", base64Value);

        String signature = signData(base64Value);
        texturesProp.addProperty("signature", signature);

        properties.add(texturesProp);
        profile.add("properties", properties);

        return profile;
    }

    private String fetchUuidByUsername(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[1024];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        bos.write(buf, 0, read);
                    }
                    String responseStr = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                    JsonObject obj = new Gson().fromJson(responseStr, JsonObject.class);
                    if (obj != null && obj.has("id")) {
                        return obj.get("id").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not fetch UUID for username: " + username, e);
        }
        return null;
    }

    private JsonObject fetchMojangProfile(String uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[1024];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        bos.write(buf, 0, read);
                    }
                    String responseStr = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                    return new Gson().fromJson(responseStr, JsonObject.class);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not fetch Mojang profile for " + uuid, e);
        }
        return null;
    }

    private JsonObject resignProfile(JsonObject mojangProfile) {
        try {
            JsonObject resigned = new JsonObject();
            resigned.addProperty("id", mojangProfile.get("id").getAsString());
            resigned.addProperty("name", mojangProfile.get("name").getAsString());

            JsonArray resignedProps = new JsonArray();
            JsonArray originalProps = mojangProfile.getAsJsonArray("properties");
            if (originalProps != null) {
                for (JsonElement propElem : originalProps) {
                    JsonObject prop = propElem.getAsJsonObject();
                    String name = prop.get("name").getAsString();
                    if (name.equals("textures")) {
                        JsonObject resignedTextures = new JsonObject();
                        resignedTextures.addProperty("name", "textures");
                        String val = prop.get("value").getAsString();
                        resignedTextures.addProperty("value", val);
                        resignedTextures.addProperty("signature", signData(val));
                        resignedProps.add(resignedTextures);
                    } else {
                        resignedProps.add(prop);
                    }
                }
            }
            resigned.add("properties", resignedProps);
            return resigned;
        } catch (Exception e) {
            Log.e(TAG, "Error resigning profile", e);
            return mojangProfile;
        }
    }

    private byte[] proxyRequest(String method, String path, byte[] requestBody, String contentType, int[] outStatusCode, String[] outContentType) {
        try {
            URL url = new URL("https://farmer-my1t.onrender.com" + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(false);

            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            if (requestBody != null && requestBody.length > 0) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody);
                }
            }

            int respCode = conn.getResponseCode();
            outStatusCode[0] = respCode;
            outContentType[0] = conn.getContentType();

            InputStream is;
            if (respCode >= 200 && respCode < 400) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            if (is != null) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        bos.write(buf, 0, read);
                    }
                    return bos.toByteArray();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to proxy request " + method + " " + path, e);
        }
        outStatusCode[0] = 500;
        outContentType[0] = "text/plain";
        return "Internal Proxy Error".getBytes(StandardCharsets.UTF_8);
    }

    private String signData(String data) throws Exception {
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(mKeyPair.getPrivate());
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
    }
}

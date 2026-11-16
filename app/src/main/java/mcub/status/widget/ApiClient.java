package mcub.status.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Minimal blocking client for the MCUB web panel REST API. */
public final class ApiClient {
    public static final String DEFAULT_URL = "http://127.0.0.1:8080";
    private static final int TIMEOUT_MS = 4000;

    private ApiClient() {}

    public static String baseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("mcub_status", Context.MODE_PRIVATE);
        String url = prefs.getString("api_url", DEFAULT_URL);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public static String token(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("mcub_status", Context.MODE_PRIVATE);
        return prefs.getString("api_token", "");
    }

    /** Result of a status fetch. */
    public static final class Status {
        public final boolean reachable;
        public final boolean session;
        public final boolean botRunning;
        public final String botUsername;
        public final String error;

        Status(boolean reachable, boolean session, boolean botRunning, String botUsername, String error) {
            this.reachable = reachable;
            this.session = session;
            this.botRunning = botRunning;
            this.botUsername = botUsername;
            this.error = error;
        }

        static Status unreachable(String msg) {
            return new Status(false, false, false, "", msg);
        }
    }

    /** Fetch /status and /api/bot/status. Call from a background thread only. */
    public static Status fetch(Context context) {
        String base = baseUrl(context);
        String token = token(context);
        try {
            JSONObject setup = getJson(base + "/status", token);
            JSONObject bot = getJson(base + "/api/bot/status", token);
            boolean session = setup.optBoolean("has_session", false);
            boolean done = setup.optBoolean("done", false);
            boolean running = bot.optBoolean("running", false);
            String username = bot.optString("username", "");
            boolean online = session || done || running;
            return new Status(true, session || done, running, username, null);
        } catch (Exception e) {
            return Status.unreachable(e.getMessage());
        }
    }

    private static JSONObject getJson(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    public static boolean isOnline(Status s) {
        return s.reachable && (s.session || s.botRunning);
    }
}

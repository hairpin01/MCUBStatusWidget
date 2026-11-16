package mcub.status.widget;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
/**
 * Generic placeholder engine for the MCUB web panel API.
 * Modules are configured in JSON: each defines an endpoint, method,
 * a JSON key path and a placeholder name. Results are collected into a
 * placeholder map that is substituted into the user template.
 */
public final class ApiClient {
    public static final String DEFAULT_URL = "http://127.0.0.1:8080";
    public static final String DEFAULT_MODULES = "["
            + "{\"name\":\"status\",\"placeholder\":\"session\",\"endpoint\":\"/status\",\"method\":\"GET\",\"key\":\"has_session\",\"params\":\"\"},"
            + "{\"name\":\"bot\",\"placeholder\":\"bot\",\"endpoint\":\"/api/bot/status\",\"method\":\"GET\",\"key\":\"running\",\"params\":\"\"},"
            + "{\"name\":\"bot_user\",\"placeholder\":\"bot_user\",\"endpoint\":\"/api/bot/status\",\"method\":\"GET\",\"key\":\"username\",\"params\":\"\"},"
            + "{\"name\":\"meta_uptime\",\"placeholder\":\"uptime\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.uptime\",\"params\":\"\"},"
            + "{\"name\":\"meta_version\",\"placeholder\":\"version\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.version\",\"params\":\"\"},"
            + "{\"name\":\"meta_modules\",\"placeholder\":\"modules\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.modules_count\",\"params\":\"\"},"
            + "{\"name\":\"meta_commands\",\"placeholder\":\"commands\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.commands_count\",\"params\":\"\"},"
            + "{\"name\":\"user\",\"placeholder\":\"user\",\"endpoint\":\"/api/ext/get_me\",\"method\":\"GET\",\"key\":\"data.username\",\"params\":\"\"}"
            + "]";
    public static final String DEFAULT_TEMPLATE = "MCUB {session}\n"
            + "@{user} · {version} · up {uptime}";
    private static final int TIMEOUT_MS = 5000;
    private ApiClient() {}
    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("mcub_status", Context.MODE_PRIVATE);
    }
    public static String baseUrl(Context context) {
        String url = prefs(context).getString("api_url", DEFAULT_URL);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.isEmpty() ? DEFAULT_URL : url;
    }
    public static String modulesJson(Context context) {
        return prefs(context).getString("modules_json", DEFAULT_MODULES);
    }
    public static String template(Context context) {
        return prefs(context).getString("template", DEFAULT_TEMPLATE);
    }
    public static long pollIntervalMs(Context context) {
        long minutes = prefs(context).getLong("poll_minutes", 1L);
        if (minutes < 1) minutes = 1;
        return minutes * 60000L;
    }

    public static final class Result {
        public final boolean reachable;
        public final Map<String, String> placeholders;
        public final int latencyMs;
        public final String error;
        Result(boolean reachable, Map<String, String> placeholders, int latencyMs, String error) {
            this.reachable = reachable;
            this.placeholders = placeholders;
            this.latencyMs = latencyMs;
            this.error = error;
        }
    }

    /** Fetch all configured modules. Call from a background thread only. */
    public static Result fetch(Context context) {
        String base = baseUrl(context);
        String token = prefs(context).getString("api_token", "");
        Map<String, String> placeholders = new HashMap<>();
        long t0 = System.currentTimeMillis();
        boolean anyOk = false;
        String lastError = null;
        JSONArray modules;
        try {
            modules = new JSONArray(modulesJson(context));
        } catch (Exception e) {
            return new Result(false, placeholders, 0, "modules JSON: " + e.getMessage());
        }
        for (int i = 0; i < modules.length(); i++) {
            JSONObject m = modules.optJSONObject(i);
            if (m == null) continue;
            String placeholder = m.optString("placeholder", "");
            if (placeholder.isEmpty()) continue;
            String endpoint = m.optString("endpoint", "");
            String method = m.optString("method", "GET").toUpperCase();
            String key = m.optString("key", "");
            String params = m.optString("params", "");
            try {
                JSONObject json = request(base, endpoint, method, params, token);
                String value = extract(json, key);
                placeholders.put(placeholder, value == null ? "?" : value);
                anyOk = true;
            } catch (Exception e) {
                placeholders.put(placeholder, "?");
                lastError = e.getMessage();
            }
        }
        int latency = (int) (System.currentTimeMillis() - t0);
        if (!anyOk && lastError != null) {
            return new Result(false, placeholders, latency, lastError);
        }
        return new Result(true, placeholders, latency, null);
    }

    /** Substitute {placeholders} in a template line. */
    public static String render(String template, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end > i) {
                    String key = template.substring(i + 1, end).trim();
                    String v = values.get(key);
                    sb.append(v != null ? v : "{" + key + "}");
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public static String formatUptime(Object secondsObj) {
        double seconds;
        if (secondsObj instanceof Number) {
            seconds = ((Number) secondsObj).doubleValue();
        } else {
            try { seconds = Double.parseDouble(String.valueOf(secondsObj)); }
            catch (Exception e) { return String.valueOf(secondsObj); }
        }
        long s = (long) seconds;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return s + "s";
    }

    private static JSONObject request(String base, String endpoint, String method,
                                      String params, String token) throws Exception {
        StringBuilder urlSb = new StringBuilder(base).append(endpoint);
        String body = null;
        if ("GET".equals(method) && params != null && !params.trim().isEmpty()) {
            urlSb.append(urlSb.indexOf("?") >= 0 ? "&" : "?").append(params);
        } else if (!"GET".equals(method) && params != null && !params.trim().isEmpty()) {
            try { new JSONObject(params); body = params; }
            catch (Exception e) { body = params; }
        }
        URL url = new URL(urlSb.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (!"GET".equals(method)) {
                conn.setRequestMethod(method);
                conn.setDoOutput(true);
                if (body != null) {
                    byte[] bytes = body.getBytes("UTF-8");
                    conn.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
                }
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private static final String[] UPTIME_KEYS = {"uptime", "up_time", "upTime"};

    /** Extract a value by dot-path; supports array indexes like items.0.name. */
    private static String extract(Object root, String path) {
        if (path == null || path.isEmpty()) return null;
        Object cur = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (cur == null) return null;
            if (cur instanceof JSONObject) {
                cur = ((JSONObject) cur).opt(part);
            } else if (cur instanceof JSONArray) {
                try { cur = ((JSONArray) cur).opt(Integer.parseInt(part)); }
                catch (NumberFormatException e) { return null; }
            } else {
                return null;
            }
        }
        if (cur == null || cur == JSONObject.NULL) return null;
        for (String u : UPTIME_KEYS) {
            if (path.endsWith(u)) return formatUptime(cur);
        }
        if (cur instanceof JSONObject || cur instanceof JSONArray) return cur.toString();
        String s = String.valueOf(cur);
        return s.isEmpty() ? "—" : s;
    }
}

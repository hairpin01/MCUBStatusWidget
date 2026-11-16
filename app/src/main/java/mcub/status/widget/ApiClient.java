package mcub.status.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Generic placeholder engine for the MCUB web panel API.
 * Optimizations:
 * 1. Lazy fetch: only requests modules whose placeholders are referenced
 *    by the active templates (title, status, footer).
 * 2. Per-cycle request deduplication: if multiple placeholders come from the
 *    same endpoint/method/params, it executes only one HTTP call and reuses
 *    the parsed response for all keys.
 * 3. Ping fallback: if no placeholders are used at all, executes a single
 *    cheap status call to determine reachability (Online / Offline).
 */
public final class ApiClient {

    public static final String DEFAULT_URL = "http://127.0.0.1:8080";
    public static final String DEFAULT_MODULES = "["
            + "{\"name\":\"status\",\"placeholder\":\"session\",\"endpoint\":\"/status\",\"method\":\"GET\",\"key\":\"has_session\",\"params\":\"\"},"
            + "{\"name\":\"bot\",\"placeholder\":\"bot\",\"endpoint\":\"/api/bot/status\",\"method\":\"GET\",\"key\":\"running\",\"params\":\"\"},"
            + "{\"name\":\"bot_user\",\"placeholder\":\"bot_user\",\"endpoint\":\"/api/bot/status\",\"method\":\"GET\",\"key\":\"username\",\"params\":\"\"},"
            + "{\"name\":\"meta_uptime\",\"placeholder\":\"uptime\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.uptime\",\"params\":\"\"},"
            + "{\"name\":\"meta_version\",\"placeholder\":\"version\",\"endpoint\":\"/api/ext/meta\",\"method\":\"GET\",\"key\":\"data.version\",\"params\":\"\"},"
            + "{\"name\":\"meta_modules\",\"placeholder\":\"modules\",\"endpoint\":\"/api/ext/meta\",\"key\":\"data.modules_count\",\"method\":\"GET\",\"params\":\"\"},"
            + "{\"name\":\"meta_commands\",\"placeholder\":\"commands\",\"endpoint\":\"/api/ext/meta\",\"key\":\"data.commands_count\",\"method\":\"GET\",\"params\":\"\"},"
            + "{\"name\":\"user\",\"placeholder\":\"user\",\"endpoint\":\"/api/ext/get_me\",\"method\":\"GET\",\"key\":\"data.username\",\"params\":\"\"}"
            + "]";

    public static final String DEFAULT_TITLE_TEMPLATE = "MCUB";
    public static final String DEFAULT_STATUS_TEMPLATE = "Online";
    public static final String DEFAULT_TEMPLATE = "@{user} · {version} · up {uptime}";

    public static final String ALIGN_LEFT = "left";
    public static final String ALIGN_CENTER = "center";
    public static final String ALIGN_RIGHT = "right";

    public static final String LAYOUT_BAR = "bar";
    public static final String LAYOUT_RING = "ring";

    public static final int DEFAULT_THICKNESS_DP = 4;
    private static final int TIMEOUT_MS = 5000;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

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

    public static String titleTemplate(Context context) {
        return prefs(context).getString("template_title", DEFAULT_TITLE_TEMPLATE);
    }

    public static String statusTemplate(Context context) {
        return prefs(context).getString("template_status", DEFAULT_STATUS_TEMPLATE);
    }

    public static String footerTemplate(Context context) {
        return prefs(context).getString("template", DEFAULT_TEMPLATE);
    }

    public static String alignment(Context context) {
        String a = prefs(context).getString("align", "");
        return a.isEmpty() ? ALIGN_LEFT : a;
    }

    public static String layout(Context context) {
        String l = prefs(context).getString("layout", "");
        return LAYOUT_RING.equals(l) ? LAYOUT_RING : LAYOUT_BAR;
    }

    public static int thicknessDp(Context context) {
        int t = prefs(context).getInt("thickness", DEFAULT_THICKNESS_DP);
        if (t < 2) t = 2;
        if (t > 12) t = 12;
        return t;
    }

    public static Integer parseColor(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        try {
            return android.graphics.Color.parseColor(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static long pollIntervalMs(Context context) {
        long minutes = prefs(context).getLong("poll_minutes", 1L);
        if (minutes < 1) minutes = 1;
        return minutes * 60000L;
    }

    /** Extract all {names} from the provided templates. */
    public static Set<String> extractPlaceholders(String... templates) {
        Set<String> set = new HashSet<>();
        if (templates == null) return set;
        for (String t : templates) {
            if (t == null || t.isEmpty()) continue;
            Matcher m = PLACEHOLDER_PATTERN.matcher(t);
            while (m.find()) {
                String name = m.group(1).trim();
                if (!name.isEmpty()) set.add(name);
            }
        }
        return set;
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

    /**
     * Fetch only the modules needed by active templates, deduplicating HTTP
     * requests for identical endpoints within the same cycle.
     * Call from a background thread only.
     */
    public static Result fetch(Context context) {
        String base = baseUrl(context);
        String token = prefs(context).getString("api_token", "");
        Map<String, String> placeholders = new HashMap<>();

        Set<String> needed = extractPlaceholders(
                titleTemplate(context),
                statusTemplate(context),
                footerTemplate(context));

        long t0 = System.currentTimeMillis();
        boolean anyOk = false;
        String lastError = null;

        JSONArray modules;
        try {
            modules = new JSONArray(modulesJson(context));
        } catch (Exception e) {
            return new Result(false, placeholders, 0, "modules JSON: " + e.getMessage());
        }

        // Cache responses keyed by "METHOD:endpoint:params" so multiple placeholders
        // referencing the same endpoint (e.g. meta) only make 1 HTTP request.
        Map<String, JSONObject> responseCache = new HashMap<>();

        for (int i = 0; i < modules.length(); i++) {
            JSONObject m = modules.optJSONObject(i);
            if (m == null) continue;
            String placeholder = m.optString("placeholder", "");
            if (placeholder.isEmpty()) continue;

            // Lazy: skip modules that aren't used in any template
            if (!needed.contains(placeholder)) continue;

            String endpoint = m.optString("endpoint", "");
            String method = m.optString("method", "GET").toUpperCase();
            String key = m.optString("key", "");
            String params = m.optString("params", "");

            String cacheKey = method + ":" + endpoint + ":" + params;
            try {
                JSONObject json = responseCache.get(cacheKey);
                if (json == null) {
                    json = request(base, endpoint, method, params, token);
                    responseCache.put(cacheKey, json);
                }
                String value = extract(json, key);
                placeholders.put(placeholder, value == null ? "?" : value);
                anyOk = true;
            } catch (Exception e) {
                placeholders.put(placeholder, "?");
                lastError = e.getMessage();
            }
        }

        // Fallback: if no placeholders were requested (static templates), execute a
        // single ping to the first available endpoint to verify reachability.
        if (responseCache.isEmpty()) {
            String pingEndpoint = "/status";
            String pingMethod = "GET";
            String pingParams = "";
            if (modules.length() > 0) {
                JSONObject first = modules.optJSONObject(0);
                if (first != null) {
                    pingEndpoint = first.optString("endpoint", "/status");
                    pingMethod = first.optString("method", "GET").toUpperCase();
                    pingParams = first.optString("params", "");
                }
            }
            try {
                request(base, pingEndpoint, pingMethod, pingParams, token);
                anyOk = true;
            } catch (Exception e) {
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

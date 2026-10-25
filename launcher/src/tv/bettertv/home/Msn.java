package tv.bettertv.home;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weather as MSN (Bing) Weather shows it. The page itself loads its numbers from MSN's own JSON
 * service (api.msn.com/weather/overview; forecasts by Foreca, India AQI included). That service
 * wants the key MSN's page carries in its script bundle, so the key is found the same way the
 * page finds it: open the page, follow it to its script bundle, read the key, and keep it until
 * the service refuses it. Unofficial: MSN can change any of this.
 */
final class Msn {
    static final String KEY = "weather.msnkey";
    private static final String PAGE = "https://www.msn.com/en-in/weather/forecast/";
    private static final Pattern BUNDLE = Pattern.compile("bundles/v1/weather/latest/common\\.[0-9a-f]+\\.js");
    private static final Pattern API_KEY = Pattern.compile("let t=\"([A-Za-z0-9]{36,48})\";switch\\(this\\.appType\\)");

    private Msn() {}

    /** The script bundle's address in MSN's weather page, or null. */
    static String bundle(String page) {
        Matcher m = BUNDLE.matcher(page);
        return m.find() ? "https://assets.msn.com/" + m.group() : null;
    }

    /** The web page's default service key in the bundle, or null. */
    static String key(String bundle) {
        Matcher m = API_KEY.matcher(bundle);
        return m.find() ? m.group(1) : null;
    }

    private static String discover(Config cfg) throws IOException {
        String b = bundle(Live.get(PAGE));
        String k = b == null ? null : key(Live.get(b));
        if (k == null) throw new IOException("MSN key not found");
        cfg.set(KEY, k);
        return k;
    }

    /** MSN's forecast for a place: {"current", "forecast": {"days": [...]}, ...}. Slow: off the UI thread. */
    static JSONObject overview(Config cfg, String lat, String lon) throws IOException, JSONException {
        String k = cfg.get(KEY, null);
        for (int attempt = 0; attempt < 2; attempt++) {
            if (k == null) k = discover(cfg);
            try {
                String body = Live.get("https://api.msn.com/weather/overview?apikey=" + k
                        + "&ocid=Peregrine&cm=en-in&it=web&units=C&days=10&insights=1&wrapodata=false"
                        + "&lat=" + lat + "&lon=" + lon);
                return new JSONObject(body).getJSONArray("responses").getJSONObject(0)
                        .getJSONArray("weather").getJSONObject(0);
            } catch (IOException refused) {
                // a rotated key answers 401: find the new one once
                k = null;
            }
        }
        throw new IOException("MSN weather unavailable");
    }

    /** Hourly "yyyy-MM-ddTHH:mm" and rain chance in percent, today and tomorrow, for {@link Rain#check}. */
    static Object[] hourly(JSONObject w) throws JSONException {
        JSONArray days = w.getJSONObject("forecast").getJSONArray("days");
        java.util.List<String> t = new java.util.ArrayList<>();
        java.util.List<Integer> p = new java.util.ArrayList<>();
        for (int d = 0; d < Math.min(2, days.length()); d++) {
            JSONArray h = days.getJSONObject(d).getJSONArray("hourly");
            for (int i = 0; i < h.length(); i++) {
                t.add(h.getJSONObject(i).getString("valid").substring(0, 16));
                p.add((int) Math.round(h.getJSONObject(i).optDouble("precip", -1)));
            }
        }
        int[] chance = new int[p.size()];
        for (int i = 0; i < chance.length; i++) chance[i] = p.get(i);
        return new Object[] {t.toArray(new String[0]), chance};
    }
}

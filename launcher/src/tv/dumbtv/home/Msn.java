package tv.dumbtv.home;

import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Current weather as MSN (Bing) Weather shows it, read from its public page; it has no free API.
 * The page carries today's numbers as text: temperature, condition, feels like, India's AQI with
 * its band and main pollutant, wind, humidity, visibility, pressure and dew point.
 */
final class Msn {
    private Msn() {}

    /** MSN's page for any place, by name, state and coordinates. */
    static String url(String name, String state, String lat, String lon) {
        String loc = "{\"l\":\"" + name + "\",\"r\":\"" + state + "\",\"c\":\"India\",\"i\":\"IN\",\"t\":102,"
                + "\"g\":\"en-in\",\"x\":\"" + lon + "\",\"y\":\"" + lat + "\"}";
        String b64 = Base64.encodeToString(loc.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "https://www.msn.com/en-in/weather/forecast/in-" + name.replace(' ', '-') + "?loc=" + b64
                + "&weadegreetype=C";
    }

    static Map<String, String> fetch(String url) throws IOException {
        return parse(Live.get(url));
    }

    /**
     * Keys: temp, condition, feels, summary, aqi, aqiBand, pollutant, wind, humidity, visibility,
     * pressure, dew. A key is missing when the page did not have it.
     */
    static Map<String, String> parse(String html) {
        List<String> l = lines(html);
        Map<String, String> out = new LinkedHashMap<>();
        int start = Math.max(0, l.indexOf("Current weather"));
        int c = l.subList(start, l.size()).indexOf("°C");
        if (c >= 0) c += start;
        if (c > 0) {
            out.put("temp", l.get(c - 1));
            if (c + 1 < l.size()) out.put("condition", l.get(c + 1));
        }
        after(l, "Feels like", "-?\\d+°", "feels", out);
        int f = l.indexOf("Feels like");
        for (int i = Math.max(0, f); f >= 0 && i < Math.min(l.size(), f + 6); i++) {
            if (l.get(i).startsWith("Expect") || l.get(i).endsWith(".")) {
                out.put("summary", l.get(i).replaceAll("\\s+", " "));
                break;
            }
        }
        int a = l.indexOf("Air quality");
        if (a >= 0 && a + 1 < l.size()) out.put("aqiBand", l.get(a + 1));
        int p = l.indexOf("Primary pollutant");
        if (p >= 0 && p + 2 < l.size()) {
            out.put("pollutant", l.get(p + 1));
            if (l.get(p + 2).matches("\\d+")) out.put("aqi", l.get(p + 2));
        }
        after(l, "Wind", "\\d+ km/h", "wind", out);
        after(l, "Humidity", "\\d+%", "humidity", out);
        after(l, "Visibility", "[\\d.]+ km", "visibility", out);
        after(l, "Pressure", "[\\d,]+ mb", "pressure", out);
        after(l, "Dew point", "-?\\d+°", "dew", out);
        return out;
    }

    private static void after(List<String> l, String label, String value, String key, Map<String, String> out) {
        Pattern p = Pattern.compile(value);
        for (int i = l.indexOf(label) + 1; i > 0 && i < l.size(); i++) {
            if (p.matcher(l.get(i)).matches()) {
                out.put(key, l.get(i));
                return;
            }
        }
    }

    private static List<String> lines(String html) {
        String text = html.replaceAll("(?s)<script.*?</script>|<style.*?</style>", "")
                .replaceAll("<[^>]+>", "\n")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("‎", "");
        List<String> lines = new ArrayList<>();
        for (String s : text.split("\n")) {
            if (!s.trim().isEmpty()) lines.add(s.trim());
        }
        return lines;
    }

    /** Places offered before searching: big cities, then the family's own places. {name, state, lat, lon}. */
    static final String[][] PLACES = {
        {"Palam", "Delhi", "28.5904", "77.0780"},
        {"New Delhi", "Delhi", "28.6139", "77.2090"},
        {"Mumbai", "Maharashtra", "19.0760", "72.8777"},
        {"Kolkata", "West Bengal", "22.5726", "88.3639"},
        {"Bengaluru", "Karnataka", "12.9716", "77.5946"},
        {"Chennai", "Tamil Nadu", "13.0827", "80.2707"},
        {"Hyderabad", "Telangana", "17.3850", "78.4867"},
        {"Patna", "Bihar", "25.5941", "85.1356"},
        {"Darbhanga", "Bihar", "26.1522", "85.8971"},
        {"Gangta", "Bihar", "25.0389", "86.5031"},
        {"Kharagpur", "Bihar", "25.1245", "86.5558"},
        {"Bariarpur", "Bihar", "25.2879", "86.5764"},
        {"Deoghar", "Jharkhand", "24.4898", "86.6990"},
        {"Haridwar", "Uttarakhand", "29.9479", "78.1603"},
        {"Vrindavan", "Uttar Pradesh", "27.5811", "77.6966"},
    };
}

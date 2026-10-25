package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Weather from MSN (Bing) Weather's own data service (see {@link Msn}): the pill beside the clock
 * and this screen. Open-Meteo stands in for the pill when MSN does not answer.
 */
public final class Weather extends Activity {
    private static final String NAME = "weather.name", LAT = "weather.lat", LON = "weather.lon";
    private static final long FRESH_MS = 30 * 60_000L;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

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

    private static String now;
    private static long nowAt;
    private static String nowFor;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private TextView place, current, details;
    private LinearLayout days;
    private android.widget.GridLayout cards;

    /** "☾ 22°C · AQI 261 · ☂ 4 PM" for the home screen, fetched at most every 30 minutes. */
    static void current(Config cfg, ExecutorService io, Handler ui, Consumer<String> show) {
        String where = lat(cfg) + "," + lon(cfg) + "," + cfg.get(Config.WEATHER);
        if (now != null && where.equals(nowFor) && System.currentTimeMillis() - nowAt < FRESH_MS) {
            show.accept(now);
            return;
        }
        io.execute(() -> {
            String text;
            try {
                JSONObject w = data(cfg);
                JSONObject c = w.getJSONObject("current");
                text = glyph(c.optString("cap"), !"n".equals(c.optString("daytime"))) + " "
                        + Math.round(c.getDouble("temp")) + "°C";
                if (c.has("aqi")) text += "  ·  AQI " + Math.round(c.getDouble("aqi"));
                String rain = rain(w, c.optString("created"));
                if (rain != null) text += "  ·  " + rain;
            } catch (IOException | JSONException | RuntimeException e) {
                return; // no network at all: keep whatever is showing and try again on the next visit
            }
            String shown = text;
            now = text;
            nowAt = System.currentTimeMillis();
            nowFor = where;
            ui.post(() -> show.accept(shown));
        });
    }

    private static final String CACHE = "weather.json";

    /**
     * MSN (Bing) first; if it fails, its last good answer for this place from the last 6 hours,
     * marked with its age; else Open-Meteo in the same shape. Each Bing failure leaves a line in
     * crash.log. Slow: off the UI thread.
     */
    static JSONObject data(Config cfg) throws IOException, JSONException {
        java.io.File cache = new java.io.File(cfg.dir, CACHE);
        String where = lat(cfg) + "," + lon(cfg);
        if ("openmeteo".equals(cfg.get(Config.WEATHER))) return openMeteo(cfg).put("source", "Open-Meteo");
        try {
            JSONObject w = Msn.overview(cfg, lat(cfg), lon(cfg));
            w.put("where", where).put("at", System.currentTimeMillis());
            try (java.io.Writer out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(cache),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                out.write(w.toString());
            }
            return w;
        } catch (IOException | JSONException | RuntimeException bing) {
            Crash.note(cfg, "Bing weather failed: " + bing);
        }
        try {
            JSONObject w = new JSONObject(new String(java.nio.file.Files.readAllBytes(cache.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
            long age = System.currentTimeMillis() - w.optLong("at");
            if (where.equals(w.optString("where")) && age < 6 * 3_600_000L) {
                return w.put("source", "MSN Weather · updated " + Math.max(1, age / 60_000) + " min ago");
            }
        } catch (IOException | JSONException | RuntimeException none) {
            // no usable copy: Open-Meteo below
        }
        return openMeteo(cfg);
    }

    /** Open-Meteo's forecast and air quality, put in MSN's shape so the rest of the code needs no second path. */
    static JSONObject openMeteo(Config cfg) throws IOException, JSONException {
        JSONObject f = new JSONObject(Live.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat(cfg)
                + "&longitude=" + lon(cfg) + "&timezone=auto&forecast_days=10"
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,"
                + "wind_gusts_10m,wind_direction_10m,surface_pressure,cloud_cover,dew_point_2m,visibility,uv_index"
                + "&hourly=precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                + "apparent_temperature_max,apparent_temperature_min,uv_index_max,sunrise,sunset"));
        JSONObject o = f.getJSONObject("current");
        JSONObject c = new JSONObject()
                .put("temp", o.getDouble("temperature_2m")).put("feels", o.getDouble("apparent_temperature"))
                .put("rh", o.getDouble("relative_humidity_2m")).put("cap", words(o.getInt("weather_code")))
                .put("daytime", o.optInt("is_day", 1) == 1 ? "d" : "n").put("windSpd", o.getDouble("wind_speed_10m"))
                .put("windGust", o.optDouble("wind_gusts_10m", 0)).put("windDir", o.optDouble("wind_direction_10m", 0))
                .put("baro", o.optDouble("surface_pressure", 0)).put("cloudCover", o.optDouble("cloud_cover", 0))
                .put("dewPt", o.optDouble("dew_point_2m", 0)).put("vis", o.optDouble("visibility", 0) / 1000)
                .put("uv", o.optDouble("uv_index", 0)).put("created", o.getString("time"));
        try {
            JSONObject a = new JSONObject(Live.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude="
                    + lat(cfg) + "&longitude=" + lon(cfg) + "&current=us_aqi,pm2_5")).getJSONObject("current");
            if (!a.isNull("us_aqi")) {
                c.put("aqi", a.getDouble("us_aqi")).put("aqiSeverity", "US AQI (Open-Meteo)")
                        .put("primaryPollutant", "PM2.5 " + Math.round(a.optDouble("pm2_5")) + " µg/m³");
            }
        } catch (IOException | JSONException ignored) {
            // the rest still shows
        }
        JSONObject d = f.getJSONObject("daily"), h = f.getJSONObject("hourly");
        JSONArray days = new JSONArray(), ht = h.getJSONArray("time"), hp = h.getJSONArray("precipitation_probability");
        for (int i = 0; i < d.getJSONArray("time").length(); i++) {
            String day = d.getJSONArray("time").getString(i);
            JSONArray hours = new JSONArray();
            for (int k = 0; k < ht.length(); k++) {
                if (ht.getString(k).startsWith(day)) {
                    hours.put(new JSONObject().put("valid", ht.getString(k)).put("precip", hp.optDouble(k, -1)));
                }
            }
            days.put(new JSONObject()
                    .put("daily", new JSONObject().put("valid", day)
                            .put("tempHi", d.getJSONArray("temperature_2m_max").getDouble(i))
                            .put("tempLo", d.getJSONArray("temperature_2m_min").getDouble(i))
                            .put("precip", d.getJSONArray("precipitation_probability_max").optDouble(i, 0))
                            .put("feelsHi", d.getJSONArray("apparent_temperature_max").optDouble(i, 0))
                            .put("feelsLo", d.getJSONArray("apparent_temperature_min").optDouble(i, 0))
                            .put("uv", d.getJSONArray("uv_index_max").optDouble(i, 0))
                            .put("pvdrCap", words(d.getJSONArray("weather_code").getInt(i))))
                    .put("hourly", hours)
                    .put("almanac", new JSONObject().put("sunrise", d.getJSONArray("sunrise").getString(i) + ":00")
                            .put("sunset", d.getJSONArray("sunset").getString(i) + ":00")));
        }
        return new JSONObject().put("current", c).put("forecast", new JSONObject().put("days", days))
                .put("source", "Open-Meteo (Bing unavailable)");
    }

    /** WMO weather code in words, for Open-Meteo. */
    private static String words(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 67 || code >= 80 && code <= 82) return "Rain";
        if (code <= 77 || code == 85 || code == 86) return "Snow";
        return "Thunderstorm";
    }

    /** {@link Rain#check} over MSN's hourly rain chances; "now" is MSN's own timestamp for the place. */
    private static String rain(JSONObject w, String created) throws JSONException {
        Object[] h = Msn.hourly(w);
        String[] times = (String[]) h[0];
        if (times.length == 0 || created.length() < 13) return null;
        String today = created.substring(0, 10);
        String tomorrow = times[times.length - 1].substring(0, 10);
        return Rain.check(times, (int[]) h[1], today, tomorrow, Integer.parseInt(created.substring(11, 13)));
    }

    private static String lat(Config cfg) {
        return cfg.get(LAT, "28.5904");
    }

    private static String lon(Config cfg) {
        return cfg.get(LON, "77.0780");
    }

    /** MSN's caption to a plain text symbol; U+FE0E keeps it out of colour emoji. */
    static String glyph(String cap, boolean day) {
        String c = cap.toLowerCase(Locale.ROOT), g;
        if (c.contains("thunder") || c.contains("storm")) g = "☈";
        else if (c.contains("rain") || c.contains("shower") || c.contains("drizzle")) g = "☂";
        else if (c.contains("snow") || c.contains("sleet")) g = "❄";
        else if (c.contains("fog") || c.contains("haze") || c.contains("mist") || c.contains("smoke")) g = "≋";
        else if (c.contains("cloud") || c.contains("overcast")) g = "☁";
        else g = day ? "☀" : "☾";
        return g + "︎";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        cfg.refresh();
        Ui.fonts(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(40), dp(56), dp(32));

        place = Ui.text(this, 18, Ui.MUTED, Ui.outfitMedium);
        current = Ui.text(this, 64, Ui.TEXT, Ui.outfitLight);
        current.setPadding(0, dp(10), 0, dp(6));
        details = Ui.text(this, 16, Ui.MUTED, Ui.outfitLight);
        page.addView(place);
        page.addView(current);
        page.addView(details);

        days = new LinearLayout(this);
        days.setPadding(0, dp(28), 0, dp(20));
        page.addView(days, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        cards = new android.widget.GridLayout(this);
        cards.setColumnCount(5);
        page.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        page.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, dp(18)));

        TextView change = Ui.text(this, 15, Ui.TEXT, Ui.outfitMedium);
        change.setText("Change location");
        change.setPadding(dp(18), dp(10), dp(18), dp(10));
        change.setFocusable(true);
        change.setClickable(true);
        Ui.focusStyle(change, cfg.accent(), 0x14FFFFFF, dp(18));
        change.setOnClickListener(v -> places());
        page.addView(change, new LinearLayout.LayoutParams(WRAP, WRAP));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackground(Ui.backdrop(this, cfg, 0xFF0C0C0E));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        setContentView(scroll);
        change.requestFocus();
        load();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        place.setText(cfg.get(NAME, "Palam, Delhi"));
        current.setText("…");
        details.setText("");
        days.removeAllViews();
        cards.removeAllViews();
        io.execute(() -> {
            try {
                JSONObject w = data(cfg);
                ui.post(() -> {
                    try {
                        show(w);
                    } catch (JSONException | RuntimeException e) {
                        current.setText("Weather unavailable");
                    }
                });
            } catch (IOException | JSONException e) {
                ui.post(() -> current.setText("No internet"));
            }
        });
    }

    private void show(JSONObject w) throws JSONException {
        JSONObject c = w.getJSONObject("current");
        JSONArray all = w.getJSONObject("forecast").getJSONArray("days");
        JSONObject today = all.getJSONObject(0), d = today.getJSONObject("daily"), sky = today.getJSONObject("almanac");
        current.setText(glyph(c.optString("cap"), !"n".equals(c.optString("daytime"))) + " " + Math.round(c.getDouble("temp")) + "°C");
        JSONObject part = d.optJSONObject("n".equals(c.optString("daytime")) ? "night" : "day");
        details.setText(c.optString("cap") + "  ·  feels like " + Math.round(c.getDouble("feels")) + "°"
                + (part == null ? "" : "\n" + part.optString("summary")) + "\n" + w.optString("source", "MSN Weather · forecast by Foreca"));

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT), eee = new SimpleDateFormat("EEE", Locale.ENGLISH);
        for (int i = 0; i < all.length(); i++) {
            JSONObject x = all.getJSONObject(i).getJSONObject("daily");
            String day;
            try {
                day = i == 0 ? "Today" : eee.format(iso.parse(x.getString("valid").substring(0, 10)));
            } catch (ParseException e) {
                day = "";
            }
            TextView t = Ui.text(this, 15, Ui.TEXT, Ui.outfitLight);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(dp(5), 1f);
            t.setText(day + "\n" + glyph(x.optString("pvdrCap"), true) + "\n" + Math.round(x.getDouble("tempHi")) + "°  "
                    + Math.round(x.getDouble("tempLo")) + "°\n☂︎ " + Math.round(x.optDouble("precip", 0)) + "%");
            days.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }

        String rain = rain(w, c.optString("created"));
        if (c.has("aqi")) card("Air quality", Math.round(c.getDouble("aqi")) + "", c.optString("aqiSeverity") + "\n" + c.optString("primaryPollutant"));
        card("Rain check", rain == null ? "No rain" : rain, Math.round(d.optDouble("precip", 0)) + "% chance today");
        card("Wind", Math.round(c.getDouble("windSpd")) + " km/h", "Gusts " + Math.round(c.optDouble("windGust", 0))
                + " km/h\nFrom " + compass(c.optDouble("windDir", 0)));
        card("Humidity", Math.round(c.getDouble("rh")) + "%", "Dew point " + Math.round(c.optDouble("dewPt", 0)) + "°");
        card("UV", Math.round(c.optDouble("uv", 0)) + "", (c.optString("uvDesc").isEmpty() ? "" : c.optString("uvDesc") + " · ") + "today's max " + Math.round(d.optDouble("uv", 0)));
        card("Visibility", Math.round(c.optDouble("vis", 0)) + " km", "Cloud cover " + Math.round(c.optDouble("cloudCover", 0)) + "%");
        card("Pressure", Math.round(c.optDouble("baro", 0)) + " mb", "");
        card("Temperature", Math.round(d.getDouble("tempHi")) + "° / " + Math.round(d.getDouble("tempLo")) + "°",
                "Feels " + Math.round(d.optDouble("feelsHi", 0)) + "° / " + Math.round(d.optDouble("feelsLo", 0)) + "°");
        card("Sun", time(sky.optString("sunrise")) + " ↑", time(sky.optString("sunset")) + " ↓");
        if (sky.has("moonPhase")) card("Moon", sky.optString("moonPhase"), "Rises " + time(sky.optString("moonrise")) + " · sets " + time(sky.optString("moonset")));
    }

    /** "2026-10-24T06:27:33+05:30" is "06:27". */
    private static String time(String iso) {
        return iso.length() >= 16 ? iso.substring(11, 16) : "–";
    }

    private void card(String title, String big, String small) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackground(Ui.glassy(cfg) ? Ui.glass(dp(14), dp(1)) : Ui.round(0x14FFFFFF, dp(14)));
        TextView a = Ui.text(this, 12, Ui.FAINT, Ui.outfitMedium), b = Ui.text(this, 26, Ui.TEXT, Ui.outfitLight),
                s = Ui.text(this, 12, Ui.MUTED, Ui.outfitLight);
        a.setText(title.toUpperCase(Locale.ROOT));
        a.setLetterSpacing(0.12f);
        b.setText(big);
        b.setPadding(0, dp(4), 0, dp(2));
        s.setText(small);
        box.addView(a);
        box.addView(b);
        box.addView(s);
        android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(0, 0, dp(12), dp(12));
        cards.addView(box, lp);
    }

    private static String compass(double degrees) {
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return d[(int) Math.round(((degrees % 360) + 360) % 360 / 45) % 8];
    }

    private void search() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("City or area, e.g. Palam");
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        AlertDialog d = new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle("Weather location")
                .setView(box)
                .setPositiveButton("Search", (dialog, w) -> find(field.getText().toString()))
                .setNegativeButton("Cancel", null)
                .create();
        field.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH && (e == null || e.getKeyCode() != KeyEvent.KEYCODE_ENTER)) {
                return false;
            }
            d.dismiss();
            find(field.getText().toString());
            return true;
        });
        d.show();
    }

    /** Big cities and the family's places; the last line opens a search for anywhere else in India. */
    private void places() {
        String[] names = new String[PLACES.length + 1];
        for (int i = 0; i < PLACES.length; i++) names[i] = PLACES[i][0] + ", " + PLACES[i][1];
        names[names.length - 1] = "Search another place…";
        new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle("Weather location")
                .setItems(names, (d, which) -> {
                    if (which == PLACES.length) {
                        search();
                        return;
                    }
                    String[] p = PLACES[which];
                    choose(p[0], p[1], p[2], p[3]);
                })
                .show();
    }

    private void choose(String place, String state, String lat, String lon) {
        cfg.set(LAT, lat);
        cfg.set(LON, lon);
        cfg.set(NAME, place + (state.isEmpty() ? "" : ", " + state));
        now = null;
        load();
    }

    private void find(String query) {
        if (query.trim().isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject j = new JSONObject(Live.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&countryCode=IN&name="
                        + URLEncoder.encode(query.trim(), "UTF-8")));
                JSONArray results = j.optJSONArray("results");
                ui.post(() -> pick(results));
            } catch (IOException | JSONException e) {
                ui.post(() -> Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void pick(JSONArray results) {
        if (results == null || results.length() == 0) {
            Toast.makeText(this, "No place found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[results.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject r = results.optJSONObject(i);
            StringBuilder s = new StringBuilder(r.optString("name"));
            for (String k : new String[] {"admin2", "admin1"}) {
                if (!r.optString(k).isEmpty()) s.append(", ").append(r.optString(k));
            }
            names[i] = s.toString();
        }
        new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle("Pick a place")
                .setItems(names, (d, which) -> {
                    JSONObject r = results.optJSONObject(which);
                    choose(r.optString("name"), r.optString("admin1"), String.valueOf(r.optDouble("latitude")),
                            String.valueOf(r.optDouble("longitude")));
                })
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

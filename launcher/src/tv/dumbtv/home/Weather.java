package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
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
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Open-Meteo (free, no key): the temperature beside the clock, and this forecast screen. */
public final class Weather extends Activity {
    private static final String NAME = "weather.name", LAT = "weather.lat", LON = "weather.lon";
    /** The place and state as MSN names them, for its page; NAME is what this screen shows. */
    private static final String PLACE = "weather.place", STATE = "weather.state";
    private static final long FRESH_MS = 30 * 60_000L;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static String now;
    private static long nowAt;
    private static String nowFor;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private TextView place, current, details;
    private LinearLayout days;
    private android.widget.GridLayout cards;

    /** "☀ 26°C" for the home screen, fetched at most every 30 minutes; null while unknown. */
    static void current(Config cfg, ExecutorService io, Handler ui, Consumer<String> show) {
        String where = lat(cfg) + "," + lon(cfg);
        if (now != null && where.equals(nowFor) && System.currentTimeMillis() - nowAt < FRESH_MS) {
            show.accept(now);
            return;
        }
        io.execute(() -> {
            try {
                JSONObject j = new JSONObject(Live.get(url(cfg, "&current=temperature_2m,weather_code,is_day"
                        + "&hourly=precipitation_probability&forecast_days=2")));
                JSONObject c = j.getJSONObject("current");
                String text = glyph(c.getInt("weather_code"), c.optInt("is_day", 1) == 1) + " "
                        + Math.round(c.getDouble("temperature_2m")) + "°C";
                Map<String, String> msn = msn(cfg);
                if (msn.containsKey("temp")) {
                    text = glyph(c.getInt("weather_code"), c.optInt("is_day", 1) == 1) + " " + msn.get("temp") + "°C";
                }
                int aqi = msn.containsKey("aqi") ? Integer.parseInt(msn.get("aqi")) : aqi(cfg);
                if (aqi >= 0) text += "  ·  AQI " + aqi;
                String rain = rain(j);
                if (rain != null) text += "  ·  " + rain;
                String shown = text;
                now = text;
                nowAt = System.currentTimeMillis();
                nowFor = where;
                ui.post(() -> show.accept(shown));
            } catch (IOException | JSONException ignored) {
                // no network: keep whatever is showing and try again on the next visit
            }
        });
    }

    /** Today's numbers from MSN (Bing) Weather for the chosen place; empty when it does not answer. */
    private static Map<String, String> msn(Config cfg) {
        try {
            return Msn.fetch(Msn.url(cfg.get(PLACE, "Palam"), cfg.get(STATE, "Delhi"), lat(cfg), lon(cfg)));
        } catch (IOException | RuntimeException e) {
            return new java.util.HashMap<>();
        }
    }

    /** The US AQI now from Open-Meteo's air-quality service, or -1. */
    private static int aqi(Config cfg) {
        try {
            JSONObject a = new JSONObject(Live.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude="
                    + lat(cfg) + "&longitude=" + lon(cfg) + "&current=us_aqi,pm2_5,pm10")).getJSONObject("current");
            return a.isNull("us_aqi") ? -1 : a.getInt("us_aqi");
        } catch (IOException | JSONException e) {
            return -1;
        }
    }

    /** {@link Rain#check} over the forecast's hourly chances; the clock is the place's own. */
    private static String rain(JSONObject j) throws JSONException {
        JSONObject h = j.getJSONObject("hourly");
        JSONArray t = h.getJSONArray("time"), p = h.getJSONArray("precipitation_probability");
        String[] times = new String[t.length()];
        int[] chance = new int[t.length()];
        for (int i = 0; i < times.length; i++) {
            times[i] = t.getString(i);
            chance[i] = p.isNull(i) ? -1 : p.getInt(i);
        }
        String now = j.getJSONObject("current").getString("time");
        String today = now.substring(0, 10);
        String tomorrow = times[times.length - 1].substring(0, 10);
        return Rain.check(times, chance, today, tomorrow, Integer.parseInt(now.substring(11, 13)));
    }

    private static String lat(Config cfg) {
        return cfg.get(LAT, "28.5904");
    }

    private static String lon(Config cfg) {
        return cfg.get(LON, "77.0780");
    }

    private static String url(Config cfg, String fields) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat(cfg) + "&longitude=" + lon(cfg)
                + "&timezone=auto" + fields;
    }

    /** WMO weather code to a plain text symbol; U+FE0E keeps it out of colour emoji. */
    static String glyph(int code, boolean day) {
        String g;
        if (code <= 1) g = day ? "☀" : "☾";
        else if (code <= 3) g = "☁";
        else if (code <= 48) g = "≋";
        else if (code >= 71 && code <= 77 || code == 85 || code == 86) g = "❄";
        else if (code >= 95) g = "☈";
        else g = "☂";
        return g + "︎";
    }

    static String words(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Ui.fonts(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0C0C0E);
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
        scroll.setBackgroundColor(0xFF0C0C0E);
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
        io.execute(() -> {
            try {
                JSONObject j = new JSONObject(Live.get(url(cfg,
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
                                + ",wind_gusts_10m,wind_direction_10m,surface_pressure,cloud_cover,dew_point_2m,visibility,uv_index"
                                + "&hourly=precipitation_probability,precipitation"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                + ",sunrise,sunset,uv_index_max,precipitation_sum&forecast_days=7")));
                JSONObject air = null;
                try {
                    air = new JSONObject(Live.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude="
                            + lat(cfg) + "&longitude=" + lon(cfg) + "&current=us_aqi,pm2_5,pm10")).getJSONObject("current");
                } catch (IOException | JSONException ignored) {
                    // the forecast still shows without air quality
                }
                JSONObject airNow = air;
                Map<String, String> bing = msn(cfg);
                ui.post(() -> {
                    try {
                        show(j);
                        details(j, airNow, bing);
                    } catch (JSONException e) {
                        current.setText("Weather unavailable");
                    }
                });
            } catch (IOException | JSONException e) {
                ui.post(() -> current.setText("No connection"));
            }
        });
    }

    private void show(JSONObject j) throws JSONException {
        JSONObject c = j.getJSONObject("current");
        int code = c.getInt("weather_code");
        current.setText(glyph(code, c.optInt("is_day", 1) == 1) + " " + Math.round(c.getDouble("temperature_2m")) + "°C");
        details.setText(words(code) + "  ·  feels like " + Math.round(c.getDouble("apparent_temperature"))
                + "°  ·  humidity " + c.getInt("relative_humidity_2m") + "%  ·  wind "
                + Math.round(c.getDouble("wind_speed_10m")) + " km/h");
        JSONObject d = j.getJSONObject("daily");
        JSONArray time = d.getJSONArray("time"), codes = d.getJSONArray("weather_code");
        JSONArray hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
        JSONArray rain = d.getJSONArray("precipitation_probability_max");
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        for (int i = 0; i < time.length(); i++) {
            String day;
            try {
                Date date = iso.parse(time.getString(i));
                day = i == 0 ? "Today" : DateFormat.format("EEE", date).toString();
            } catch (ParseException e) {
                day = time.getString(i);
            }
            TextView t = Ui.text(this, 16, Ui.TEXT, Ui.outfitLight);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(dp(6), 1f);
            t.setText(day + "\n" + glyph(codes.getInt(i), true) + "\n" + Math.round(hi.getDouble(i)) + "°  "
                    + Math.round(lo.getDouble(i)) + "°\n" + (rain.isNull(i) ? "" : "☂︎ " + rain.getInt(i) + "%"));
            days.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
    }

    /** The rest of MSN Weather's sections, as cards: air, rain, wind, sun, moon and more. */
    private void details(JSONObject j, JSONObject air, Map<String, String> bing) throws JSONException {
        cards.removeAllViews();
        JSONObject c = j.getJSONObject("current"), d = j.getJSONObject("daily");
        String rain = rain(j);
        double rain24 = 0;
        JSONArray amount = j.getJSONObject("hourly").getJSONArray("precipitation");
        for (int i = 0; i < Math.min(24, amount.length()); i++) rain24 += amount.optDouble(i, 0);
        if (bing.containsKey("temp")) {
            current.setText(current.getText().toString().replaceAll("-?\\d+°C$", bing.get("temp") + "°C"));
            details.setText(bing.getOrDefault("condition", "") + "  ·  feels like " + bing.getOrDefault("feels", "?")
                    + (bing.containsKey("summary") ? "\n" + bing.get("summary") : "") + "\nMSN Weather");
        }
        if (bing.containsKey("aqi")) {
            card("Air quality", bing.get("aqi"), bing.getOrDefault("aqiBand", "") + "\n" + bing.getOrDefault("pollutant", ""));
        } else if (air != null && !air.isNull("us_aqi")) {
            int a = air.getInt("us_aqi");
            card("Air quality", a + "", Rain.aqiWord(a) + "\nPM2.5 " + Math.round(air.optDouble("pm2_5")) + " · PM10 "
                    + Math.round(air.optDouble("pm10")) + " µg/m³");
        }
        card("Rain check", rain == null ? "No rain" : rain, String.format(Locale.ROOT, "%.1f mm in the next 24 h", rain24));
        card("Wind", bing.getOrDefault("wind", Math.round(c.getDouble("wind_speed_10m")) + " km/h"), "Gusts " + Math.round(c.getDouble("wind_gusts_10m"))
                + " km/h\nFrom " + compass(c.getDouble("wind_direction_10m")));
        card("Humidity", bing.getOrDefault("humidity", c.getInt("relative_humidity_2m") + "%"),
                "Dew point " + bing.getOrDefault("dew", Math.round(c.getDouble("dew_point_2m")) + "°"));
        card("UV", Math.round(c.getDouble("uv_index")) + "", "Today's max " + Math.round(d.getJSONArray("uv_index_max").getDouble(0)));
        card("Visibility", bing.getOrDefault("visibility", Math.round(c.getDouble("visibility") / 1000) + " km"), "Cloud cover " + c.getInt("cloud_cover") + "%");
        card("Pressure", bing.getOrDefault("pressure", Math.round(c.getDouble("surface_pressure")) + " mb"), "");
        card("Sun", d.getJSONArray("sunrise").getString(0).substring(11) + " ↑", d.getJSONArray("sunset").getString(0).substring(11) + " ↓");
        card("Moon", Math.round(Rain.moonLit(System.currentTimeMillis()) * 100) + "%", "of the moon lit tonight");
    }

    private void card(String title, String big, String small) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackground(Ui.round(0x14FFFFFF, dp(14)));
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
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
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

    /** Big cities and the family's places; the last line opens a search for anywhere else. */
    private void places() {
        String[] names = new String[Msn.PLACES.length + 1];
        for (int i = 0; i < Msn.PLACES.length; i++) names[i] = Msn.PLACES[i][0] + ", " + Msn.PLACES[i][1];
        names[names.length - 1] = "Search another place…";
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Weather location")
                .setItems(names, (d, which) -> {
                    if (which == Msn.PLACES.length) {
                        search();
                        return;
                    }
                    String[] p = Msn.PLACES[which];
                    choose(p[0], p[1], p[2], p[3]);
                })
                .show();
    }

    private void choose(String place, String state, String lat, String lon) {
        cfg.set(PLACE, place);
        cfg.set(STATE, state);
        cfg.set(LAT, lat);
        cfg.set(LON, lon);
        cfg.set(NAME, place + ", " + state);
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
            for (String k : new String[] {"admin2", "admin1", "country"}) {
                if (!r.optString(k).isEmpty()) s.append(", ").append(r.optString(k));
            }
            names[i] = s.toString();
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
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

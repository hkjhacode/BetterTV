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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Open-Meteo (free, no key): the temperature beside the clock, and this forecast screen. */
public final class Weather extends Activity {
    private static final String NAME = "weather.name", LAT = "weather.lat", LON = "weather.lon";
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

    /** "☀ 26°C" for the home screen, fetched at most every 30 minutes; null while unknown. */
    static void current(Config cfg, ExecutorService io, Handler ui, Consumer<String> show) {
        String where = lat(cfg) + "," + lon(cfg);
        if (now != null && where.equals(nowFor) && System.currentTimeMillis() - nowAt < FRESH_MS) {
            show.accept(now);
            return;
        }
        io.execute(() -> {
            try {
                JSONObject c = new JSONObject(Live.get(url(cfg, "&current=temperature_2m,weather_code,is_day")))
                        .getJSONObject("current");
                String text = glyph(c.getInt("weather_code"), c.optInt("is_day", 1) == 1) + " "
                        + Math.round(c.getDouble("temperature_2m")) + "°C";
                now = text;
                nowAt = System.currentTimeMillis();
                nowFor = where;
                ui.post(() -> show.accept(text));
            } catch (IOException | JSONException ignored) {
                // no network: keep whatever is showing and try again on the next visit
            }
        });
    }

    private static String lat(Config cfg) {
        return cfg.get(LAT, "28.5926");
    }

    private static String lon(Config cfg) {
        return cfg.get(LON, "77.0855");
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
        days.setPadding(0, dp(36), 0, dp(36));
        page.addView(days, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        TextView change = Ui.text(this, 15, Ui.TEXT, Ui.outfitMedium);
        change.setText("Change location");
        change.setPadding(dp(18), dp(10), dp(18), dp(10));
        change.setFocusable(true);
        change.setClickable(true);
        Ui.focusStyle(change, cfg.accent(), 0x14FFFFFF, dp(18));
        change.setOnClickListener(v -> search());
        page.addView(change, new LinearLayout.LayoutParams(WRAP, WRAP));

        setContentView(page);
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
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                + "&forecast_days=7")));
                ui.post(() -> {
                    try {
                        show(j);
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

    private void find(String query) {
        if (query.trim().isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject j = new JSONObject(Live.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name="
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
                    cfg.set(LAT, String.valueOf(r.optDouble("latitude")));
                    cfg.set(LON, String.valueOf(r.optDouble("longitude")));
                    cfg.set(NAME, r.optString("name") + (r.optString("admin1").isEmpty() ? "" : ", " + r.optString("admin1")));
                    load();
                })
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

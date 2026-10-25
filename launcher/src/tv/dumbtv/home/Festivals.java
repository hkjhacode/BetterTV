package tv.dumbtv.home;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hindu festivals kept in Bihar, Mithila and across India, read from festivals.tsv: start, end,
 * Hindi name, colour, English name, blessing, blessing in English (ISO dates; a series such as
 * Navratri or Chhath spans its days). The file is made from Drik Panchang (Patna) by
 * work/gen_festivals.py and ships in assets; a festivals.tsv next to home.conf replaces it, and
 * home.conf lines like {@code festival.2027-11-04=दीपावली} add or correct one day.
 */
final class Festivals {
    private static String[][] days = new String[0][];

    private Festivals() {}

    /** Reads the calendar once; call before the first {@link #on}. */
    static synchronized void load(Context c) {
        if (days.length > 0) return;
        File own = new File(Config.get(c).dir, "festivals.tsv");
        try (InputStream in = own.isFile() ? new FileInputStream(own) : c.getAssets().open("festivals.tsv")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            days = parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // no calendar: ordinary greetings every day
        }
    }

    /** Tab-separated rows of seven fields; "#" lines are comments. */
    static String[][] parse(String text) {
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] f = line.replace("\r", "").split("\t", -1);
            if (f.length >= 7 && f[0].length() == 10) rows.add(f);
        }
        return rows.toArray(new String[0][]);
    }

    /** The festival on {@code day} (yyyy-MM-dd) by its Hindi name, or null. */
    static String on(Config cfg, String day) {
        String own = cfg.get("festival." + day, null);
        if (own != null) return own;
        String[] f = find(day);
        return f == null ? null : f[2];
    }

    /** The day's wish in {@code lang}, or null on an ordinary day. */
    static String wish(Config cfg, String lang, String day) {
        String own = cfg.get("festival." + day, null);
        if (own != null) return Lang.wish(lang, own, own, null, null);
        String[] f = find(day);
        return f == null ? null : Lang.wish(lang, f[2], f[4], f[5], f[6]);
    }

    /** The festival's colour on {@code day} as #RRGGBB, or null on an ordinary day. */
    static String colour(String day) {
        String[] f = find(day);
        return f == null ? null : f[3];
    }

    /** The last matching entry, so a day inside a series names its own festival (Ram Navami in Chaiti Navratri). */
    private static String[] find(String day) {
        String[] hit = null;
        for (String[] f : days) {
            if (f[0].compareTo(day) <= 0 && day.compareTo(f[1]) <= 0) hit = f;
        }
        return hit;
    }

    /** The first festival that starts after {@code day} and by {@code until}, as {start, name in lang}. */
    static String[] next(String day, String until, String lang) {
        for (String[] f : days) {
            if (f[0].compareTo(day) > 0 && f[0].compareTo(until) <= 0) {
                return new String[] {f[0], Lang.ENGLISH.equals(lang) ? f[4] : f[2]};
            }
        }
        return null;
    }

    static void use(String[][] rows) {
        days = rows;
    }
}

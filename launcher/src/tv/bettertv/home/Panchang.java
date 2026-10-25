package tv.bettertv.home;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Today's Maithili panchang from mithilapanchang.com: tithi, paksha, nakshatra and the rest. */
final class Panchang {
    static final String URL = "https://mithilapanchang.com/mai/dainik-panchang/";
    /** Drik Panchang's day page for New Delhi (GeoNames 1261481), near the weather's Palam. */
    static final String DRIK = "https://www.drikpanchang.com/panchang/day-panchang.html?geoname-id=1261481";
    private static final String[] TITHI_EN = {
        "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami",
        "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima", "Amavasya",
    };
    private static final String[] TITHI_HI = {
        "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी",
        "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा", "अमावस्या",
    };
    /** Labels as the page writes them, in the order the detail view lists them. */
    static final String[] LABELS = {
        "सप्ताहक दिन", "पक्ष", "तिथि", "नक्षत्र", "योग", "सूर्योदय", "सूर्यास्त", "राहू काल", "आजुक पर्व-उत्सव",
    };

    private Panchang() {}

    /**
     * Mithila's day page for {@code day} (yyyy-MM-dd), plus Drik's tithi: when it ends and which
     * comes next, and when that one ends (from the next day's page), so the top bar can follow the
     * tithi through the day instead of showing one that has already ended.
     */
    static Map<String, String> fetch(String day) throws IOException {
        Map<String, String> p = parse(Live.get(URL));
        try {
            String[] t = drik(Live.get(DRIK + "&date=" + dmy(day, 0)), day);
            if (t != null) {
                p.put(DRIK_KEY, t[0] + " " + t[1].substring(11) + " तक, फिर " + t[2]);
                p.put("drik.name", t[0]);
                p.put("drik.end", t[1]);
                p.put("drik.next", t[2]);
                String[] n = drik(Live.get(DRIK + "&date=" + dmy(day, 1)), dmy(day, 1).replaceAll("(..)/(..)/(....)", "$3-$2-$1"));
                if (n != null && n[0].equals(t[2])) p.put("drik.next.end", n[1]);
            }
        } catch (IOException | RuntimeException ignored) {
            // Mithila alone is enough
        }
        return p;
    }

    static final String DRIK_KEY = "तिथि (Drik, दिल्ली)";

    /** "2026-10-25" plus {@code add} days as Drik's "25/10/2026". */
    private static String dmy(String day, int add) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(day.substring(0, 4)), Integer.parseInt(day.substring(5, 7)) - 1, Integer.parseInt(day.substring(8, 10)));
        c.add(java.util.Calendar.DAY_OF_YEAR, add);
        return String.format(java.util.Locale.ROOT, "%02d/%02d/%04d", c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR));
    }

    /**
     * Drik's day page for {@code day}: {tithi in Hindi, when it ends as "yyyy-MM-dd HH:mm", the next
     * tithi}, or null. An end after midnight is written "01:20 AM, Oct 26", so it moves to the next day.
     */
    static String[] drik(String html, String day) {
        List<String> lines = lines(html);
        int i = lines.indexOf("Tithi");
        if (i < 0 || i + 4 >= lines.size() || !"upto".equals(lines.get(i + 2))) return null;
        int t = java.util.Arrays.asList(TITHI_EN).indexOf(lines.get(i + 1));
        String[] hm = lines.get(i + 3).split(":");
        if (t < 0 || hm.length != 2) return null;
        int h = Integer.parseInt(hm[0]) % 12 + ("PM".equals(lines.get(i + 4)) ? 12 : 0);
        boolean nextDay = i + 6 < lines.size() && ",".equals(lines.get(i + 5)) && lines.get(i + 6).matches("[A-Z][a-z]{2} \\d{1,2}");
        String date = nextDay ? dmy(day, 1).replaceAll("(..)/(..)/(....)", "$3-$2-$1") : day;
        int next = t == 14 || t == 15 ? 0 : t == 13 ? (lines.contains("Krishna Paksha") ? 15 : 14) : t + 1;
        return new String[] {TITHI_HI[t], String.format(java.util.Locale.ROOT, "%s %02d:%s", date, h, hm[1]), TITHI_HI[next]};
    }

    /** Each label's value is the first text line after the line that is exactly the label. */
    static Map<String, String> parse(String html) {
        List<String> lines = lines(html);
        Map<String, String> out = new LinkedHashMap<>();
        for (String label : LABELS) {
            int i = lines.indexOf(label);
            if (i >= 0 && i + 1 < lines.size()) out.put(label, lines.get(i + 1));
        }
        return out;
    }

    /** The saved panchang if it is for {@code day}, else null. One "key=value" per line. */
    static Map<String, String> read(java.io.File f, String day) {
        Map<String, String> out = new LinkedHashMap<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                int eq = l.indexOf('=');
                if (eq > 0) out.put(l.substring(0, eq), l.substring(eq + 1));
            }
        } catch (IOException e) {
            return null;
        }
        return day.equals(out.remove("day")) ? out : null;
    }

    static void write(java.io.File f, String day, Map<String, String> p) {
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f),
                java.nio.charset.StandardCharsets.UTF_8)) {
            w.write("day=" + day + "\n");
            for (Map.Entry<String, String> e : p.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue().replace('\n', ' ') + "\n");
            }
        } catch (IOException ignored) {
            // fetched again next time
        }
    }

    private static List<String> lines(String html) {
        String text = html.replaceAll("(?s)<script.*?</script>|<style.*?</style>", "")
                .replaceAll("<[^>]+>", "\n")
                .replace("&nbsp;", " ").replace("&amp;", "&");
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\n")) {
            if (!l.trim().isEmpty()) lines.add(l.trim());
        }
        return lines;
    }

    /** Today's festival from Mithila's page (its first, if several), or null. */
    static String festival(Map<String, String> p) {
        String f = p.get("आजुक पर्व-उत्सव");
        if (f == null || f.trim().isEmpty() || f.contains("लागू नहि")) return null;
        return f.split(",")[0].trim();
    }

    /**
     * The top bar's line: the tithi in force at {@code now} ("yyyy-MM-dd HH:mm") and when it ends,
     * from Drik ("चतुर्दशी 11:55 AM तक", then "पूर्णिमा कल 09:40 AM तक"), else Mithila's own words.
     */
    static String tithiLine(Map<String, String> p, String now, boolean english) {
        String end = p.get("drik.end");
        if (end != null && now.compareTo(end) < 0) return name(p.get("drik.name"), english) + " " + until(end, now, english);
        if (end != null) {
            String next = name(p.get("drik.next"), english), nextEnd = p.get("drik.next.end");
            if (nextEnd == null || now.compareTo(nextEnd) < 0) return nextEnd == null ? next : next + " " + until(nextEnd, now, english);
        }
        String t = p.get("तिथि");
        return t == null ? null : t.split(",")[0].trim();
    }

    /** "11:55 AM तक" (or "till 11:55 AM"), with "कल" / "tomorrow" when it ends on a later date than {@code now}. */
    private static String until(String end, String now, boolean english) {
        int h = Integer.parseInt(end.substring(11, 13));
        String clock = String.format(java.util.Locale.ROOT, "%02d:%s %s", h % 12 == 0 ? 12 : h % 12, end.substring(14, 16), h < 12 ? "AM" : "PM");
        boolean tomorrow = end.substring(0, 10).compareTo(now.substring(0, 10)) > 0;
        if (english) return "till " + clock + (tomorrow ? " tomorrow" : "");
        return (tomorrow ? "कल " : "") + clock + " तक";
    }

    /** The tithi's name in Hindi as stored, or in English. */
    private static String name(String hindi, boolean english) {
        int i = java.util.Arrays.asList(TITHI_HI).indexOf(hindi);
        return english && i >= 0 ? TITHI_EN[i] : hindi;
    }

    /**
     * The top bar's line: today's festival when there is one (its first, if several), otherwise
     * "शुक्ल द्वादशी", the paksha's first word and the tithi in force now.
     */
    static String brief(Map<String, String> p) {
        String festival = p.get("आजुक पर्व-उत्सव");
        if (festival != null && !festival.trim().isEmpty() && !festival.contains("लागू नहि")) {
            return festival.split(",")[0].trim();
        }
        String tithi = p.get("तिथि"), paksha = p.get("पक्ष");
        if (tithi == null) return null;
        String t = tithi.split("[ ,]")[0];
        return paksha == null ? t : paksha.split(" ")[0] + " " + t;
    }
}

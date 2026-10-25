package tv.dumbtv.home;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Today's Maithili panchang from mithilapanchang.com: tithi, paksha, nakshatra and the rest. */
final class Panchang {
    static final String URL = "https://mithilapanchang.com/mai/dainik-panchang/";
    /** Labels as the page writes them, in the order the detail view lists them. */
    static final String[] LABELS = {
        "सप्ताहक दिन", "पक्ष", "तिथि", "नक्षत्र", "योग", "सूर्योदय", "सूर्यास्त", "राहू काल", "आजुक पर्व-उत्सव",
    };

    private Panchang() {}

    static Map<String, String> fetch() throws IOException {
        return parse(Live.get(URL));
    }

    /** Each label's value is the first text line after the line that is exactly the label. */
    static Map<String, String> parse(String html) {
        String text = html.replaceAll("(?s)<script.*?</script>|<style.*?</style>", "")
                .replaceAll("<[^>]+>", "\n")
                .replace("&nbsp;", " ").replace("&amp;", "&");
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\n")) {
            if (!l.trim().isEmpty()) lines.add(l.trim());
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String label : LABELS) {
            int i = lines.indexOf(label);
            if (i >= 0 && i + 1 < lines.size()) out.put(label, lines.get(i + 1));
        }
        return out;
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

package tv.dumbtv.home;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Plain HTTPS reads, and which of a YouTube channel's live streams most people are watching. */
final class Live {
    private static final String MARK = " watching\"";
    private static final String VIDEO = "\"contentId\":\"";

    private Live() {}

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        // A desktop browser gets the full page with viewer counts; English keeps " watching".
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36");
        c.setRequestProperty("Accept-Language", "en");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** The live video on a channel's Live tab with the most viewers, or null when none is live. */
    static String mostWatched(String page) {
        String best = null;
        double max = -1;
        for (int i = page.indexOf(MARK); i >= 0; i = page.indexOf(MARK, i + MARK.length())) {
            double n = viewers(page.substring(page.lastIndexOf('"', i - 1) + 1, i));
            int v = page.lastIndexOf(VIDEO, i);
            if (v >= 0 && v + VIDEO.length() + 11 <= page.length() && n > max) {
                max = n;
                best = page.substring(v + VIDEO.length(), v + VIDEO.length() + 11);
            }
        }
        return best;
    }

    /**
     * Channels from a YouTube channel search page, as {id, name, avatar URL} (avatar may be
     * empty). The avatar is asked for at 176 px instead of the page's 88.
     */
    static List<String[]> channels(String page) {
        List<String[]> out = new ArrayList<>();
        String mark = "{\"channelRenderer\":{\"channelId\":\"";
        for (int i = page.indexOf(mark); i >= 0; i = page.indexOf(mark, i + mark.length())) {
            int id = i + mark.length(), idEnd = page.indexOf('"', id);
            String name = field(page, i, "\"title\":{\"simpleText\":\"");
            String thumb = field(page, i, "\"thumbnails\":[{\"url\":\"");
            if (idEnd < 0 || name == null) continue;
            if (thumb == null) thumb = "";
            else if (thumb.startsWith("//")) thumb = "https:" + thumb;
            out.add(new String[] {page.substring(id, idEnd), name, thumb.replace("=s88-", "=s176-")});
        }
        return out;
    }

    /** The JSON string value after {@code key} within 3000 characters of {@code from}, or null. */
    private static String field(String page, int from, String key) {
        int k = page.indexOf(key, from);
        if (k < 0 || k - from > 3000) return null;
        int start = k + key.length(), end = page.indexOf('"', start);
        // JSON writes & inside URLs as the six characters \\u0026
        return end < 0 ? null : page.substring(start, end).replace("\\u0026", "&");
    }

    /** "7.9K" is 7900, "1,234" is 1234; -1 when it is not a count. */
    static double viewers(String s) {
        s = s.replace(",", "").trim();
        double scale = 1;
        if (s.endsWith("K")) scale = 1e3;
        else if (s.endsWith("M")) scale = 1e6;
        if (scale > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * scale;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

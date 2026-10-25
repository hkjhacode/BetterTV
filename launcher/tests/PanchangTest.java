package tv.dumbtv.home;

import java.util.Map;

/** Host-side checks for reading mithilapanchang.com's daily page, without a device or network. */
public final class PanchangTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String page = "<html><script>var x='तिथि';</script><div><span>पक्ष</span><span>शुक्ल पक्ष</span></div>"
                + "<p>तिथि</p><p> द्वादशी मध्याह्न १२:११ बजे धरि, त्रयोदशी </p>"
                + "<b>नक्षत्र</b>\n<i>पूर्वा भाद्रपद</i><b>आजुक पर्व-उत्सव</b><i>प्रदोष व्रत</i></html>";
        Map<String, String> p = Panchang.parse(page);
        check("शुक्ल पक्ष".equals(p.get("पक्ष")), "paksha");
        check(p.get("तिथि").startsWith("द्वादशी"), "tithi skips the script copy");
        check("पूर्वा भाद्रपद".equals(p.get("नक्षत्र")), "nakshatra");
        check("प्रदोष व्रत".equals(p.get("आजुक पर्व-उत्सव")), "festival");
        check("प्रदोष व्रत".equals(Panchang.brief(p)), "today's festival first");
        check("शुक्ल द्वादशी".equals(Panchang.brief(Panchang.parse(page.replace("आजुक पर्व-उत्सव", "x")))), "else the tithi");
        check(Panchang.brief(Panchang.parse("<p>nothing</p>")) == null, "no tithi, no line");
        System.out.println("PanchangTest ok");
    }
}

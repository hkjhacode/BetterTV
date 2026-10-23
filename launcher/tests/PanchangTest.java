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
        String drik = "<div class=\"dpTableCell dpTableKey\">Tithi</div><div><a>Trayodashi</a> <span>upto</span> 01:36 "
                + "<span>PM</span></div><div>Paksha</div><div>Shukla Paksha</div>";
        check("त्रयोदशी 01:36 PM तक, फिर चतुर्दशी".equals(Panchang.drik(drik)), "drik tithi and the next one");
        check("चतुर्दशी 09:10 AM तक, फिर अमावस्या".equals(Panchang.drik(drik.replace("Trayodashi", "Chaturdashi")
                .replace("01:36", "09:10").replace(">PM<", ">AM<").replace("Shukla", "Krishna"))), "dark half ends in amavasya");
        check(Panchang.drik("<p>nothing</p>") == null, "no drik tithi");
        p.put(Panchang.DRIK_KEY, "त्रयोदशी 01:36 PM तक, फिर चतुर्दशी");
        check("त्रयोदशी 01:36 PM तक".equals(Panchang.tithiLine(p)), "top bar prefers drik");
        try {
            java.io.File f = java.io.File.createTempFile("panchang", ".txt");
            Panchang.write(f, "2026-10-24", p);
            check(p.equals(Panchang.read(f, "2026-10-24")), "cache round trip");
            check(Panchang.read(f, "2026-10-25") == null, "a new day fetches again");
            f.delete();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        System.out.println("PanchangTest ok");
    }
}

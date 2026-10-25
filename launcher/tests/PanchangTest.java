package tv.bettertv.home;

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
        String[] t = Panchang.drik(drik, "2026-10-24");
        check("त्रयोदशी".equals(t[0]) && "2026-10-24 13:36".equals(t[1]) && "चतुर्दशी".equals(t[2]), "drik tithi, its end and the next one");
        t = Panchang.drik(drik.replace("Trayodashi", "Chaturdashi").replace("01:36", "09:10").replace(">PM<", ">AM<").replace("Shukla", "Krishna"), "2026-10-24");
        check("अमावस्या".equals(t[2]), "dark half ends in amavasya");
        t = Panchang.drik(drik.replace("01:36 ", "01:20 ").replace(">PM<", ">AM<").replace("</span></div><div>Paksha", "</span>, <b>Oct 25</b></div><div>Paksha"), "2026-10-24");
        check("2026-10-25 01:20".equals(t[1]), "an end after midnight is the next day");
        check(Panchang.drik("<p>nothing</p>", "2026-10-24") == null, "no drik tithi");
        p.put("drik.name", "चतुर्दशी");
        p.put("drik.end", "2026-10-25 11:55");
        p.put("drik.next", "पूर्णिमा");
        p.put("drik.next.end", "2026-10-26 09:40");
        check("चतुर्दशी 11:55 AM तक".equals(Panchang.tithiLine(p, "2026-10-25 08:00", false)), "the tithi in force");
        check("पूर्णिमा कल 09:40 AM तक".equals(Panchang.tithiLine(p, "2026-10-25 17:47", false)), "then the next one, ending tomorrow");
        check("पूर्णिमा 09:40 AM तक".equals(Panchang.tithiLine(p, "2026-10-26 02:00", false)), "after midnight it ends today");
        check(Panchang.tithiLine(p, "2026-10-26 10:00", false).startsWith("द्वादशी"), "past both: Mithila's words");
        check("Purnima till 09:40 AM tomorrow".equals(Panchang.tithiLine(p, "2026-10-25 17:47", true)), "in English");
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

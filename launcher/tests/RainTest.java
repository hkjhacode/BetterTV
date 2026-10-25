package tv.dumbtv.home;

/** Host-side checks for the rain check and the greetings, without a device. */
public final class RainTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String[] t = {"2026-10-24T13:00", "2026-10-24T16:00", "2026-10-24T20:00", "2026-10-25T09:00", "2026-10-25T10:00"};
        int[] p = {70, 55, 10, 20, 60};
        check("☂︎ 4 PM".equals(Rain.check(t, p, "2026-10-24", "2026-10-25", 14)), "next likely hour today, past hours skipped");
        check("☂︎ कल 10 AM".equals(Rain.check(t, p, "2026-10-24", "2026-10-25", 17)), "after 5 PM tomorrow is added");
        check(Rain.check(t, new int[] {0, 0, 0, 0, 0}, "2026-10-24", "2026-10-25", 18) == null, "dry");
        check("☂︎ 12 PM".equals(Rain.check(new String[] {"2026-10-24T12:00"}, new int[] {90}, "2026-10-24", "x", 0)), "noon");
        check("साँझक प्रणाम".equals(Lang.greeting(Lang.MAITHILI, 17)), "maithili evening");
        check("Good night".equals(Lang.greeting(Lang.ENGLISH, 2)), "english night");
        check("भोर के प्रणाम".equals(Lang.greeting(Lang.ANGIKA, 6)), "angika morning");
        check("छठ महापर्व के ढेर सिनी शुभकामना · जय छठी मईया".equals(
                Lang.wish(Lang.ANGIKA, "छठ महापर्व", "Chhath", "जय छठी मईया", "Jai Chhathi Maiya")), "angika wish");
        check("Happy Chhath · Jai Chhathi Maiya".equals(
                Lang.wish(Lang.ENGLISH, "छठ महापर्व", "Chhath", "जय छठी मईया", "Jai Chhathi Maiya")), "english wish");
        check("होली की हार्दिक शुभकामनाएँ".equals(Lang.wish(Lang.HINDI, "होली", "Holi", "", "")), "hindi wish, no blessing");
        check("परसों".equals(Lang.daysAway(Lang.HINDI, 2)) && Lang.daysAway(Lang.HINDI, 3) == null, "days away");
        System.out.println("RainTest ok");
    }
}

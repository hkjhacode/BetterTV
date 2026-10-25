package tv.dumbtv.home;

/**
 * Hindu festivals kept in Bihar and across India, as {first day, last day, name} in ISO dates.
 * Dates follow Drik Panchang (New Delhi). Lines in home.conf as {@code festival.2027-11-04=दीपावली}
 * add more or correct one.
 */
final class Festivals {
    // shortcut: the built-in list runs to April 2027; extend it (or home.conf) every year.
    private static final String[][] DAYS = {
        {"2026-10-25", "2026-10-25", "कोजगरा"},
        {"2026-10-29", "2026-10-29", "करवा चौथ"},
        {"2026-11-06", "2026-11-06", "धनतेरस"},
        {"2026-11-08", "2026-11-08", "दीपावली"},
        {"2026-11-10", "2026-11-10", "गोवर्धन पूजा"},
        {"2026-11-11", "2026-11-11", "भैया दूज"},
        {"2026-11-13", "2026-11-16", "छठ महापर्व"},
        {"2026-11-20", "2026-11-20", "देवउठनी एकादशी"},
        {"2026-11-24", "2026-11-24", "कार्तिक पूर्णिमा"},
        {"2026-12-14", "2026-12-14", "विवाह पंचमी"},
        {"2027-01-14", "2027-01-14", "तिला संक्रांति"},
        {"2027-02-11", "2027-02-11", "सरस्वती पूजा"},
        {"2027-03-05", "2027-03-06", "महाशिवरात्रि"},
        {"2027-03-22", "2027-03-22", "होली"},
        {"2027-04-07", "2027-04-07", "चैती नवरात्रि"},
        {"2027-04-15", "2027-04-15", "रामनवमी"},
    };

    private Festivals() {}

    /** The festival on {@code day} (yyyy-MM-dd), or null. */
    static String on(Config cfg, String day) {
        String own = cfg.get("festival." + day, null);
        if (own != null) return own;
        for (String[] f : DAYS) {
            if (f[0].compareTo(day) <= 0 && day.compareTo(f[1]) <= 0) return f[2];
        }
        return null;
    }

    /** The first festival that starts after {@code day} and within {@code days} more, as {start, name}. */
    static String[] next(String day, String until) {
        for (String[] f : DAYS) {
            if (f[0].compareTo(day) > 0 && f[0].compareTo(until) <= 0) return new String[] {f[0], f[2]};
        }
        return null;
    }
}

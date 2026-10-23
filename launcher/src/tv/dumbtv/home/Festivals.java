package tv.dumbtv.home;

/**
 * Hindu festivals kept in Bihar, Mithila and across India: {first day, last day, Hindi name,
 * colour, English name, blessing in Devanagari, blessing in English}, ISO dates. A series like
 * Navratri or Chhath spans its days. Dates follow Drik Panchang (New Delhi). Lines in home.conf as
 * {@code festival.2027-11-04=दीपावली} add more or correct one.
 */
final class Festivals {
    // shortcut: the built-in list runs to April 2027; extend it (or home.conf) every year.
    private static final String[][] DAYS = {
        {"2026-10-11", "2026-10-19", "नवरात्रि", "#E53935", "Navratri", "जय माता दी", "Jai Mata Di"},
        {"2026-10-20", "2026-10-20", "विजयादशमी", "#FF8F00", "Vijayadashami", "जय श्री राम", "Jai Shri Ram"},
        {"2026-10-25", "2026-10-25", "कोजगरा", "#AFC3FF", "Kojagara", "जय माँ लक्ष्मी", "Jai Maa Lakshmi"},
        {"2026-10-29", "2026-10-29", "करवा चौथ", "#E2384D", "Karwa Chauth", "", ""},
        {"2026-11-06", "2026-11-06", "धनतेरस", "#F4B400", "Dhanteras", "जय धन्वन्तरि", "Jai Dhanvantari"},
        {"2026-11-08", "2026-11-08", "दीपावली · काली पूजा", "#FF9A1F", "Diwali · Kali Puja", "जय माँ काली", "Jai Maa Kali"},
        {"2026-11-10", "2026-11-10", "गोवर्धन पूजा", "#6BBF59", "Govardhan Puja", "जय गोवर्धन नाथ", "Jai Govardhan Nath"},
        {"2026-11-11", "2026-11-11", "भैया दूज", "#FF6F91", "Bhai Dooj", "", ""},
        {"2026-11-13", "2026-11-16", "छठ महापर्व", "#FF7A1A", "Chhath", "जय छठी मईया", "Jai Chhathi Maiya"},
        {"2026-11-20", "2026-11-20", "देवउठनी एकादशी", "#F4B400", "Dev Uthani Ekadashi", "जय श्री हरि", "Jai Shri Hari"},
        {"2026-11-24", "2026-11-24", "कार्तिक पूर्णिमा", "#AFC3FF", "Kartik Purnima", "हर हर गंगे", "Har Har Gange"},
        {"2026-12-14", "2026-12-14", "विवाह पंचमी", "#FF4F8B", "Vivah Panchami", "जय सिया राम", "Jai Siya Ram"},
        {"2027-01-14", "2027-01-14", "तिला संक्रांति", "#FFB347", "Makar Sankranti", "", ""},
        {"2027-02-11", "2027-02-11", "सरस्वती पूजा", "#FFD23F", "Saraswati Puja", "जय माँ सरस्वती", "Jai Maa Saraswati"},
        {"2027-03-05", "2027-03-06", "महाशिवरात्रि", "#7C83FD", "Maha Shivaratri", "हर हर महादेव", "Har Har Mahadev"},
        {"2027-03-21", "2027-03-22", "होली", "#FF4FD8", "Holi", "रंग बरसे", "Rang Barse"},
        {"2027-04-07", "2027-04-15", "चैती नवरात्रि", "#E53935", "Chaiti Navratri", "जय माता दी", "Jai Mata Di"},
        {"2027-04-15", "2027-04-15", "रामनवमी", "#FF8F00", "Ram Navami", "जय श्री राम", "Jai Shri Ram"},
    };

    private Festivals() {}

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
        for (String[] f : DAYS) {
            if (f[0].compareTo(day) <= 0 && day.compareTo(f[1]) <= 0) hit = f;
        }
        return hit;
    }

    /** The first festival that starts after {@code day} and by {@code until}, as {start, name in lang}. */
    static String[] next(String day, String until, String lang) {
        for (String[] f : DAYS) {
            if (f[0].compareTo(day) > 0 && f[0].compareTo(until) <= 0) {
                return new String[] {f[0], Lang.ENGLISH.equals(lang) ? f[4] : f[2]};
            }
        }
        return null;
    }
}

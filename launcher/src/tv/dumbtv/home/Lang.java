package tv.dumbtv.home;

/**
 * The home screen's words in Angika, Maithili, Hindi and English: the greeting for the time of
 * day, the festival wish, and "tomorrow" / "day after". Plain Java so it is tested on the host.
 */
final class Lang {
    static final String ANGIKA = "angika", MAITHILI = "maithili", HINDI = "hindi", ENGLISH = "english";

    private Lang() {}

    /** Morning 4-12, afternoon 12-16, evening 16-20, night. */
    static String greeting(String lang, int hour) {
        int part = hour >= 4 && hour < 12 ? 0 : hour >= 12 && hour < 16 ? 1 : hour >= 16 && hour < 20 ? 2 : 3;
        switch (lang) {
            case MAITHILI:
                return new String[] {"भोरक प्रणाम", "दुपहरियाक प्रणाम", "साँझक प्रणाम", "रातिक प्रणाम"}[part];
            case HINDI:
                return new String[] {"सुप्रभात", "नमस्कार", "शुभ संध्या", "शुभ रात्रि"}[part];
            case ENGLISH:
                return new String[] {"Good morning", "Good afternoon", "Good evening", "Good night"}[part];
            default:
                return new String[] {"भोर के प्रणाम", "दुपहरिया के प्रणाम", "साँझ के प्रणाम", "रात के प्रणाम"}[part];
        }
    }

    /**
     * The wish for a festival day, with the festival's own blessing after it, e.g.
     * "छठ महापर्व के ढेर सिनी शुभकामना · जय छठी मईया" or "Happy Chhath · Jai Chhathi Maiya".
     */
    static String wish(String lang, String nameHi, String nameEn, String blessHi, String blessEn) {
        String w;
        switch (lang) {
            case MAITHILI:
                w = nameHi + "क हार्दिक शुभकामना";
                break;
            case HINDI:
                w = nameHi + " की हार्दिक शुभकामनाएँ";
                break;
            case ENGLISH:
                w = "Happy " + nameEn;
                break;
            default:
                w = nameHi + " के ढेर सिनी शुभकामना";
        }
        String bless = ENGLISH.equals(lang) ? blessEn : blessHi;
        return bless == null || bless.isEmpty() ? w : w + " · " + bless;
    }

    /** "tomorrow" (1) and "day after tomorrow" (2); null for other gaps. */
    static String daysAway(String lang, long days) {
        if (days != 1 && days != 2) return null;
        switch (lang) {
            case HINDI:
                return days == 1 ? "कल" : "परसों";
            case ENGLISH:
                return days == 1 ? "Tomorrow" : "Day after";
            case MAITHILI:
                return days == 1 ? "काल्हि" : "परसू";
            default:
                return days == 1 ? "काल्हि" : "परसूं";
        }
    }
}

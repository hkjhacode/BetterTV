package tv.dumbtv.home;

/** When rain is next likely, from an hourly forecast; plain Java so it is tested on the host. */
final class Rain {
    /** A chance this high counts as "rain likely". */
    static final int LIKELY = 40;

    private Rain() {}

    /**
     * The rest of today's first likely hour, as "☂ 4 PM"; from 5 PM on, tomorrow's first likely hour
     * is added ("कल 10 AM"). {@code times} are "yyyy-MM-ddTHH:mm" hours, {@code chance} their
     * precipitation probability in percent (negative when unknown). Null when no rain is likely.
     */
    static String check(String[] times, int[] chance, String today, String tomorrow, int hourNow) {
        String rest = first(times, chance, today, hourNow);
        String next = hourNow >= 17 ? first(times, chance, tomorrow, 0) : null;
        if (rest == null && next == null) return null;
        StringBuilder b = new StringBuilder("☂︎ ");
        if (rest != null) b.append(rest);
        if (next != null) b.append(rest != null ? " · " : "").append("कल ").append(next);
        return b.toString();
    }

    private static String first(String[] times, int[] chance, String day, int fromHour) {
        for (int i = 0; i < times.length; i++) {
            if (!times[i].startsWith(day) || chance[i] < LIKELY) continue;
            int h = Integer.parseInt(times[i].substring(11, 13));
            if (h < fromHour) continue;
            return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM");
        }
        return null;
    }

    /** The US EPA band of an AQI number, as MSN Weather words it. */
    static String aqiWord(int usAqi) {
        if (usAqi <= 50) return "Good";
        if (usAqi <= 100) return "Moderate";
        if (usAqi <= 150) return "Unhealthy for sensitive groups";
        if (usAqi <= 200) return "Unhealthy";
        if (usAqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    /** Fraction of the moon lit tonight (0 new, 1 full) from the synodic month since a known new moon. */
    static double moonLit(long epochMillis) {
        double days = (epochMillis - 947182440000L) / 86_400_000.0; // new moon 2000-01-06 18:14 UTC
        double age = ((days % 29.530588853) + 29.530588853) % 29.530588853;
        return (1 - Math.cos(2 * Math.PI * age / 29.530588853)) / 2;
    }
}

package tv.bettertv.home;

/** Host-side checks for the festival calendar file and lookups, without a device. */
public final class FestivalsTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        String text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])), "UTF-8");
        String[][] rows = Festivals.parse(text);
        check(rows.length > 30, "a year of festivals");
        Festivals.use(rows);
        check("#FF7A1A".equals(Festivals.colour("2026-11-15")), "Chhath spans its days");
        check(Festivals.colour("2026-11-01") == null, "ordinary day");
        String[] next = Festivals.next("2027-03-20", "2027-03-27", Lang.HINDI);
        check(next != null && "2027-03-21".equals(next[0]), "next festival in the week");
        check(Festivals.parse("# comment\n2026-01-01\tx\n").length == 0, "short rows are skipped");
        System.out.println("FestivalsTest ok");
    }
}

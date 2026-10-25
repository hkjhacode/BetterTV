package tv.dumbtv.home;

/** Host-side checks for picking the most-watched live stream, without a device or network. */
public final class LiveTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    private static String lockup(String id, String watching) {
        return "{\"contentId\":\"" + id + "\",\"title\":{\"content\":\"x\"},\"metadataParts\":[{\"text\":"
                + "{\"content\":\"" + watching + " watching\"},\"accessibilityLabel\":\"" + watching + " watching\"}]}";
    }

    public static void main(String[] args) {
        check(Live.viewers("7.9K") == 7900, "K suffix");
        check(Live.viewers("1,234") == 1234, "commas");
        check(Live.viewers("2.1M") == 2100000, "M suffix");
        check(Live.viewers("Live") == -1, "not a count");

        String page = lockup("Hkxo-Wb03z8", "173") + lockup("Nq2wYlWFucg", "7.9K") + lockup("5_bOHoAwlIE", "2.3K");
        check("Nq2wYlWFucg".equals(Live.mostWatched(page)), "highest viewer count wins");
        check(Live.mostWatched("{\"contentId\":\"abcdefghijk\",\"content\":\"12 views\"}") == null, "nothing live");
        System.out.println("LiveTest ok");
    }
}

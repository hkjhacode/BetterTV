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
        String search = "x{\"channelRenderer\":{\"channelId\":\"UCRWFSbif-RFENbBrSiez1DA\",\"title\":{\"simpleText\":\"ABP NEWS\"},"
                + "\"thumbnail\":{\"thumbnails\":[{\"url\":\"//yt3.ggpht.com/abc=s88-c-k\"}]}}}"
                + "{\"channelRenderer\":{\"channelId\":\"UCx\",\"title\":{\"simpleText\":\"No Avatar\"}}}";
        java.util.List<String[]> found = Live.channels(search);
        check(found.size() == 2, "two channels");
        check("UCRWFSbif-RFENbBrSiez1DA".equals(found.get(0)[0]) && "ABP NEWS".equals(found.get(0)[1]), "id and name");
        check("https://yt3.ggpht.com/abc=s176-c-k".equals(found.get(0)[2]), "bigger avatar URL");
        check("".equals(found.get(1)[2]), "missing avatar");
        System.out.println("LiveTest ok");
    }
}

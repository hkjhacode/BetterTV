package tv.bettertv.home;

import java.util.List;

/** Host-side checks for the My Favourites lineup file, its drawing order and the remote's moves. */
public final class FavouritesTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String tsv = "# name\trow\tx\ttop\theight\tcolour\ttitle\tpackage\tlink\textra\ttouch\n"
                + "tony-stark\tback\t0.30\t160\t700\t#F2B33D\tIron Man\tin.startv.hotstar\thttps://www.hotstar.com/in/movies/iron-man/1660000038\t-\tarc\n"
                + "spider-man\tback\t0.62\t150\t700\t#E23B3B\tSpider-Man: Homecoming\tcom.netflix.ninja\thttps://www.netflix.com/title/80166369\t-\tspider\n"
                + "patrick-jane\tfront\t0.22\t300\t860\t#C8443A\tThe Mentalist\tcom.netflix.ninja\thttps://www.netflix.com/title/70155590\tred-john.png@120,260,260\textra\n"
                + "hinata\tfront\t0.70\t330\t820\t#F28C28\tHaikyuu!!\tcom.netflix.ninja\thttps://www.netflix.com/title/80090673\t-\tjump\n"
                + "broken\tfront\tnot-a-number\t1\t1\t#FFFFFF\tx\tx\tx\t-\tnone\n"
                + "short\tback\t0.5\n";
        List<Lineup.Who> all = Lineup.parse(tsv);
        check(all.size() == 4, "good lines read, bad and short ones skipped");
        Lineup.Who tony = all.get(0), spidey = all.get(1), jane = all.get(2), hinata = all.get(3);
        check("red-john.png".equals(jane.extra) && jane.extraX == 120f && jane.extraW == 260f, "extra file and place");
        check("-".equals(tony.extra), "no extra");

        List<Lineup.Who> none = Lineup.order(all, null);
        check(none.indexOf(tony) < none.indexOf(jane) && none.indexOf(spidey) < none.indexOf(hinata), "back row drawn first");
        List<Lineup.Who> backSel = Lineup.order(all, tony);
        check(backSel.indexOf(tony) == 1 && backSel.indexOf(tony) < backSel.indexOf(jane), "a selected back-row character stays behind the front row");
        List<Lineup.Who> frontSel = Lineup.order(all, jane);
        check(frontSel.get(frontSel.size() - 1) == jane, "a selected front-row character comes in front of everyone");

        check(Lineup.beside(all, tony, true) == spidey, "right along the back row");
        check(Lineup.beside(all, spidey, true) == null, "stops at the end of the row");
        check(Lineup.beside(all, hinata, false) == jane, "left along the front row");
        System.out.println("FavouritesTest ok");
    }
}

package tv.bettertv.home;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/** The My Favourites lineup file and the rules for its order and moves; no Android calls, so it tests on the host. */
final class Lineup {
    /** One character: a line of favourites.tsv, plus its pictures once loaded. */
    static final class Who {
        String name, row, title, pkg, link, extra, touch;
        float x, top, height, extraX, extraY, extraW;
        int colour;
        Bitmap cut, scene, extraArt;
    }

    private Lineup() {}

    /**
     * favourites.tsv, tab-separated: name, row (back/front), x (centre, fraction of the width),
     * top and height (px on a 1920x1080 canvas), colour (#RRGGBB), title, app package, link,
     * extra (file@x,y,width on the canvas, or "-"), touch. "#" lines are comments.
     */
    static List<Who> parse(String text) {
        List<Who> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String l = raw.replace("\r", "");
            if (l.startsWith("#") || l.trim().isEmpty()) continue;
            String[] f = l.split("\t", -1);
            if (f.length < 11) continue;
            try {
                Who w = new Who();
                w.name = f[0];
                w.row = f[1];
                w.x = Float.parseFloat(f[2]);
                w.top = Float.parseFloat(f[3]);
                w.height = Float.parseFloat(f[4]);
                if (!f[5].matches("#[0-9A-Fa-f]{6}")) continue;
                w.colour = 0xFF000000 | Integer.parseInt(f[5].substring(1), 16);
                w.title = f[6];
                w.pkg = f[7];
                w.link = f[8];
                w.extra = f[9];
                w.touch = f[10];
                int at = w.extra.indexOf('@');
                if (at > 0) {
                    String[] p = w.extra.substring(at + 1).split(",");
                    w.extraX = Float.parseFloat(p[0]);
                    w.extraY = Float.parseFloat(p[1]);
                    w.extraW = Float.parseFloat(p[2]);
                    w.extra = w.extra.substring(0, at);
                }
                out.add(w);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // a bad line is skipped; the rest still show
            }
        }
        return out;
    }

    /** Back row first, the selected back-row character last within it; then the front row, the selected one last. */
    static List<Who> order(List<Who> all, Who selected) {
        List<Who> out = new ArrayList<>();
        for (String row : new String[] {"back", "front"}) {
            for (Who w : all) if (w.row.equals(row) && w != selected) out.add(w);
            if (selected != null && selected.row.equals(row)) out.add(selected);
        }
        return out;
    }

    /** The next character in the same row to the right (or left), or null at the end. */
    static Who beside(List<Who> all, Who from, boolean right) {
        Who best = null;
        for (Who w : all) {
            if (!w.row.equals(from.row) || w == from) continue;
            boolean ahead = right ? w.x > from.x : w.x < from.x;
            if (ahead && (best == null || Math.abs(w.x - from.x) < Math.abs(best.x - from.x))) best = w;
        }
        return best;
    }
}

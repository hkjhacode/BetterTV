package tv.bettertv.home;

/** Host-side checks for turning a banner into a white logo, without a device. */
public final class LogoTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int w = 10, h = 6;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = 0xFFFFFFFF;          // white banner
        for (int y = 2; y < 4; y++) for (int x = 3; x < 7; x++) px[y * w + x] = 0xFFE50914; // red mark
        int[] box = Logo.whiten(px, w, h);
        check(box != null && box[0] == 3 && box[1] == 2 && box[2] == 7 && box[3] == 4, "bounds of the mark");
        check(px[2 * w + 3] == 0xFFFFFFFF, "mark becomes opaque white");
        check(px[0] >>> 24 == 0, "backdrop becomes transparent");

        int[] gradient = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) gradient[y * w + x] = 0xFF000000 | (x * 25) << 16 | (255 - x * 25);
        }
        check(Logo.whiten(gradient, w, h) == null, "a plain sideways gradient has no logo");
        check(Logo.ink(new int[] {0xFF000000, 0x00000000}) == 1f, "ink counts painted pixels");
        System.out.println("LogoTest ok");
    }
}

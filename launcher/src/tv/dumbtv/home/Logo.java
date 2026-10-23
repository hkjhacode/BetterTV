package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.io.File;

/**
 * The home row's floating white logos. A PNG in {@code logos/<package>.png} next to home.conf
 * wins; otherwise the app's banner is turned white on transparent: whatever stands out from the
 * banner's own background becomes the logo.
 */
final class Logo {
    static final String DIR = "logos";
    private static final int W = 320, H = 180;

    private Logo() {}

    /** Slow: call off the UI thread, after {@link Catalog#art}. */
    static void load(Config cfg, Item it) {
        File own = new File(new File(cfg.dir, DIR), it.id + ".png");
        Bitmap b = own.isFile() ? BitmapFactory.decodeFile(own.getPath()) : null;
        if (b == null) b = white(it.banner != null ? it.banner : it.icon);
        if (b == null) return;
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        it.logoInk = ink(px) / px.length;
        it.logo = b;
    }

    private static Bitmap white(Drawable art) {
        if (art == null || art.getConstantState() == null) return null;
        Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Drawable copy = art.getConstantState().newDrawable().mutate();
        copy.setBounds(0, 0, W, H);
        copy.draw(new Canvas(src));
        int[] px = new int[W * H];
        src.getPixels(px, 0, W, 0, 0, W, H);
        src.recycle();
        int[] box = whiten(px, W, H);
        if (box == null) return null;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, W, 0, 0, W, H);
        Bitmap trimmed = Bitmap.createBitmap(out, box[0], box[1], box[2] - box[0], box[3] - box[1]);
        if (trimmed != out) out.recycle();
        return trimmed;
    }

    /**
     * Turns {@code px} into white on transparent in place and returns the logo's bounds
     * {left, top, right, bottom}, or null when nothing stands out. The background of each row is
     * taken from its two edge pixels and blended across, so a sideways gradient stays background.
     */
    static int[] whiten(int[] px, int w, int h) {
        int left = w, top = h, right = 0, bottom = 0;
        for (int y = 0; y < h; y++) {
            int a = px[y * w], b = px[y * w + w - 1];
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                float t = w == 1 ? 0 : x / (float) (w - 1);
                int d = 0;
                for (int s = 0; s <= 24; s += 8) {
                    float bg = (a >>> s & 255) * (1 - t) + (b >>> s & 255) * t;
                    d = Math.max(d, Math.abs((p >>> s & 255) - Math.round(bg)));
                }
                // 40 or less is noise and anti-aliasing of the backdrop; full white from 110.
                int alpha = Math.max(0, Math.min(255, (d - 40) * 255 / 70));
                px[y * w + x] = alpha << 24 | 0xFFFFFF;
                if (alpha > 24) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x + 1);
                    bottom = Math.max(bottom, y + 1);
                }
            }
        }
        return right > left && bottom > top ? new int[] {left, top, right, bottom} : null;
    }

    /** The logo's painted area in pixels, alpha-weighted. */
    static float ink(int[] px) {
        float sum = 0;
        for (int p : px) sum += (p >>> 24) / 255f;
        return sum;
    }
}

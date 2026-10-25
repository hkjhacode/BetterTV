package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    /** Dialogs match the screen: frosted with the glassy look. */
    static int dialog(Config cfg) {
        return glassy(cfg) ? R.style.GlassDialog : DIALOG;
    }
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    /** Outfit (SIL OFL, assets/OFL.txt), bundled; set by {@link #fonts} before the first screen. */
    static Typeface outfitLight = LIGHT, outfitMedium = MEDIUM;

    private Ui() {}

    static void fonts(Context c) {
        if (outfitLight != LIGHT) return;
        outfitLight = Typeface.createFromAsset(c.getAssets(), "outfit-300.ttf");
        outfitMedium = Typeface.createFromAsset(c.getAssets(), "outfit-500.ttf");
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        return Color.luminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /**
     * Frosted glass without a blur (Android 9 has no cheap one): a light-to-clear sheen from the
     * top, a bright hairline edge, nothing that costs more than a gradient.
     */
    static GradientDrawable glass(float radius, int edge) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x3DFFFFFF, 0x1AFFFFFF, 0x12FFFFFF});
        g.setCornerRadius(radius);
        g.setStroke(Math.max(1, edge), 0x4DFFFFFF);
        return g;
    }

    /** Glass that is lit up: the focused row or card, tinted with the accent. */
    static GradientDrawable glassLit(float radius, int edge, int accent) {
        int tint = accent & 0x00FFFFFF;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {tint | 0x73000000, tint | 0x40000000, tint | 0x2E000000});
        g.setCornerRadius(radius);
        g.setStroke(Math.max(1, edge), 0x99FFFFFF);
        return g;
    }

    static boolean glassy(Config cfg) {
        return "on".equals(cfg.get(Config.GLASS));
    }

    /** The home wallpaper as shown now; Home sets it, other screens share it so it costs no memory. */
    static Bitmap wall;

    /** A screen's background: the wallpaper under a dark veil with the glassy look, else {@code plain}. */
    static Drawable backdrop(Context c, Config cfg, int plain) {
        if (!glassy(cfg) || wall == null) return new ColorDrawable(plain);
        return new LayerDrawable(new Drawable[] {new BitmapDrawable(c.getResources(), wall), new ColorDrawable(0xC20A0A0C)});
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[0], round(idle, radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xD9FFFFFF}));
    }
}

package tv.dumbtv.home;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/** One tile: an installed app (id = package name) or a TV input (id = input id). */
final class Item {
    final String id;
    final CharSequence label;
    final Intent intent;
    ActivityInfo info;
    boolean leanback;
    int state = -1;
    volatile boolean artRequested;
    volatile Drawable banner, icon;
    volatile int backgroundColor;
    /** The soft light behind the home row while this tile is focused; 0 until sampled. */
    volatile int glow;
    /** White-on-transparent logo for the home row, and the share of it that is painted. */
    volatile Bitmap logo;
    volatile float logoInk;
    volatile boolean colorRequested;

    Item(String id, CharSequence label, Intent intent) {
        this.id = id;
        this.label = label;
        this.intent = intent;
    }
}

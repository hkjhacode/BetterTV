package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The home screen: time and weather top left, bare icons top right, then a tagline, one row of
 * apps floating on the wallpaper with a soft glow in the focused app's colour, a row of live
 * news channels, and More apps.
 */
public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private Config cfg;
    private int applied = -1, appsGeneration;
    private boolean resumed, landed;
    private ImageView wall;
    private View shade, dimmer, glow, wifi;
    private ScrollView page;
    private static final Locale HINDI = new Locale("hi", "IN");
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEEE, d MMMM", HINDI);
    private static final SimpleDateFormat WEEKDAY = new SimpleDateFormat("EEEE", HINDI);
    private TextView clock, weather, tagline, greet, date;
    private HorizontalScrollView dockScroll;
    private LinearLayout dock, news;
    private List<Item> apps = new ArrayList<>();
    private Tile moving;
    private String wallKey;
    private ValueAnimator glowAnimation;
    private int glowColor;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            loadApps(data == null ? null : data.getSchemeSpecificPart());
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        Ui.fonts(this);
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        firstApps(catalog);
        renderNews();
    }

    /**
     * Waits for the app list and loads the art of the home row, so the first frame is complete
     * instead of popping banners in after a cold start.
     */
    private void firstApps(Future<List<Item>> catalog) {
        try {
            apps = catalog.get();
        } catch (ExecutionException e) {
            loadApps(null);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadApps(null);
            return;
        }
        for (Item it : Catalog.visible(cfg, Catalog.APPS, apps)) {
            Catalog.art(getPackageManager(), it);
            Logo.load(this, cfg, it, Catalog.glyph(it.id));
            it.artRequested = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.version() != applied) apply();
        else background();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
        Weather.current(cfg, net, ui, weather::setText);
        View focused = getCurrentFocus();
        if (focused instanceof Tile) focusTile((Tile) focused);
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterReceiver(time);
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        io.shutdownNow();
        net.shutdownNow();
        super.onDestroy();
    }

    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) stopMoving();
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile && Catalog.APPS.equals(((Tile) f).kind)) menu((Tile) f);
            else startActivity(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        dimmer = new View(this);
        dimmer.setBackgroundColor(0x99000000);
        dimmer.setAlpha(0f);
        root.addView(dimmer, new FrameLayout.LayoutParams(MATCH, MATCH));
        // The rows sit on dark: clear down to about half the height, then fading to black.
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x00000000, 0x0D000000, 0x8C000000, 0xD9000000}));
        root.addView(bottom, new FrameLayout.LayoutParams(MATCH, MATCH));
        // An oval of light that glides to sit behind the focused tile.
        glow = new View(this);
        GradientDrawable light = new GradientDrawable();
        light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        light.setGradientRadius(dp(270));
        light.setColors(new int[] {Color.TRANSPARENT, Color.TRANSPARENT});
        glow.setBackground(light);
        glow.setScaleX(1.7f);
        glow.setVisibility(View.INVISIBLE);
        root.addView(glow, new FrameLayout.LayoutParams(dp(540), dp(540)));

        // Two stops: the home screen itself, and below it the live news. The wallpaper darkens
        // as the page moves down.
        page = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return 0;
            }
        };
        page.setFocusable(false);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setOnScrollChangeListener((v, x, y, ox, oy) ->
                dimmer.setAlpha(Math.min(1f, y / (float) Math.max(1, v.getHeight() / 2))));
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        page.addView(sections, new FrameLayout.LayoutParams(MATCH, WRAP));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(dp(56), dp(30), dp(56), dp(10));
        int screen = getResources().getDisplayMetrics().heightPixels;
        sections.addView(column, new LinearLayout.LayoutParams(MATCH, screen - dp(34)));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.TOP);
        head.setClipChildren(false);
        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        greet = Ui.text(this, 17, Ui.MUTED, Ui.outfitLight);
        clock = Ui.text(this, 60, Ui.TEXT, Ui.outfitLight);
        clock.setPadding(0, dp(4), 0, dp(6));
        date = Ui.text(this, 16, Ui.MUTED, Ui.outfitLight);
        when.addView(greet);
        when.addView(clock);
        when.addView(date);
        head.addView(when, new LinearLayout.LayoutParams(0, WRAP, 1f));
        weather = pill(0, "", v -> startActivity(new Intent(this, Weather.class)));
        head.addView(weather);
        wifi = pill(R.drawable.ic_wifi, "Wi-Fi", v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS), null));
        wifi.setId(View.generateViewId());
        head.addView(wifi);
        head.addView(circle(R.drawable.ic_tune, "Options", v -> startActivity(new Intent(this, Options.class))));
        head.addView(circle(R.drawable.ic_settings, "Settings", v -> openSettings()));
        column.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        tagline = Ui.text(this, 34, 0xF2FFFFFF, Ui.outfitLight);
        tagline.setSingleLine();
        column.addView(tagline, new LinearLayout.LayoutParams(MATCH, WRAP));

        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(dp(4), dp(16), dp(4), dp(4));
        dockScroll = new HorizontalScrollView(this) {
            /** Keeps the focused tile in view with a little room, except near either end. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                if (getChildCount() == 0) return 0;
                int range = Math.max(0, getChildAt(0).getWidth() - getWidth());
                int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                return target - getScrollX();
            }
        };
        dockScroll.setFocusable(false);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.addView(dock, new FrameLayout.LayoutParams(WRAP, WRAP));
        column.addView(dockScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout below = new LinearLayout(this);
        below.setOrientation(LinearLayout.VERTICAL);
        below.setClipChildren(false);
        below.setClipToPadding(false);
        below.setPadding(dp(56), 0, dp(56), dp(40));
        sections.addView(below, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView live = Ui.text(this, 11, Ui.FAINT, Ui.outfitMedium);
        live.setText("LIVE NEWS");
        live.setLetterSpacing(0.16f);
        live.setPadding(dp(4), dp(10), 0, dp(8));
        below.addView(live);
        news = new LinearLayout(this);
        news.setClipChildren(false);
        news.setPadding(dp(14), dp(10), dp(14), dp(10));
        news.setClipToPadding(false);
        below.addView(news, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView more = Ui.text(this, 13, Ui.TEXT, Ui.outfitMedium);
        more.setText("More apps");
        more.setPadding(dp(18), dp(8), dp(18), dp(8));
        more.setFocusable(true);
        more.setClickable(true);
        more.setBackground(Ui.round(0x1FFFFFFF, dp(16)));
        more.setOnClickListener(v -> startActivity(new Intent(this, Apps.class)));
        more.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(200).start();
            v.setBackground(Ui.round(has ? 0xE6FFFFFF : 0x1FFFFFFF, dp(16)));
            more.setTextColor(has ? 0xFF000000 : Ui.TEXT);
            if (has) {
                light(0, null);
                scrollTo(true);
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        mlp.topMargin = dp(14);
        below.addView(more, mlp);
        return root;
    }

    /** A top-bar pill: icon and label on a faint glass chip; white with dark ink when focused. */
    private TextView pill(int icon, String label, View.OnClickListener click) {
        TextView v = Ui.text(this, 15, Ui.TEXT, Ui.outfitMedium);
        v.setText(label);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(icon == 0 ? 16 : 13), dp(9), dp(16), dp(9));
        if (icon != 0) {
            Drawable d = getDrawable(icon).mutate();
            d.setBounds(0, 0, dp(17), dp(17));
            v.setCompoundDrawablesRelative(d, null, null, null);
            v.setCompoundDrawablePadding(dp(8));
        }
        v.setOnClickListener(click);
        chip(v);
        return v;
    }

    private ImageView circle(int icon, String name, View.OnClickListener click) {
        ImageView v = new ImageView(this);
        v.setImageResource(icon);
        v.setContentDescription(name);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setOnClickListener(click);
        chip(v);
        v.getLayoutParams().width = v.getLayoutParams().height = dp(37);
        return v;
    }

    private void chip(View v) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(idleChip());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(10);
        v.setLayoutParams(lp);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(220).start();
            view.setBackground(has ? Ui.round(0xF2FFFFFF, dp(19)) : idleChip());
            int ink = has ? 0xFF000000 : Ui.TEXT;
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(ink);
                for (Drawable d : ((TextView) view).getCompoundDrawablesRelative()) {
                    if (d != null) d.setTint(ink);
                }
            } else {
                ((ImageView) view).setColorFilter(ink);
            }
            if (has) {
                tagline.setText("");
                light(0, null);
                scrollTo(false);
            }
        });
    }

    private GradientDrawable idleChip() {
        GradientDrawable g = Ui.round(0x1AFFFFFF, dp(19));
        g.setStroke(Math.max(1, dp(1)), 0x24FFFFFF);
        return g;
    }

    private void apply() {
        applied = cfg.version();
        renderDock();
        background();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                            fresh.glow = old.glow;
                            fresh.logo = old.logo;
                            fresh.logoInk = old.logoInk;
                        }
                    }
                }
                apps = list;
                renderDock();
            });
        });
    }

    private void renderDock() {
        if (moving != null) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && Catalog.APPS.equals(((Tile) focused).kind)
                ? ((Tile) focused).item.id : null;
        dock.removeAllViews();
        String labels = cfg.get(Config.LABELS);
        int accent = cfg.accent(), restore = -1, index = 0;
        List<Item> shown = Catalog.visible(cfg, Catalog.APPS, apps);
        // Equal cells across the width, as many as there are apps; it only scrolls past ten or so.
        int avail = getResources().getDisplayMetrics().widthPixels - dp(120);
        int cell = Math.max(dp(84), Math.min(dp(170), avail / Math.max(1, shown.size())));
        int w = cell - dp(14), h = Math.round(w * 0.6f);
        for (Item it : shown) {
            Tile t = new Tile(this, it, Catalog.APPS, Catalog.label(cfg, it), w, h, labels, accent, false);
            t.setOnClickListener(v -> launch(new Intent(it.intent), it.id));
            t.setNextFocusUpId(wifi.getId());
            t.setOnFocusChangeListener((v, has) -> {
                if (has) focusTile((Tile) v);
            });
            t.setOnLongClickListener(v -> {
                menu((Tile) v);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(14);
            dock.addView(t, lp);
            if (it.id.equals(keep)) restore = index;
            index++;
            if (!it.artRequested) loadArt(it);
        }
        if (restore >= 0) dock.getChildAt(restore).requestFocus();
        else if (keep != null || getCurrentFocus() == null || !landed) focusFirst();
    }

    private void renderNews() {
        for (Channels.Channel c : Channels.ALL) {
            Item it = new Item(c.name, c.name, null);
            it.banner = getDrawable(c.art).mutate();
            it.glow = c.color;
            Tile t = new Tile(this, it, Catalog.NEWS, c.name, dp(118), dp(66), "never", cfg.accent(), true);
            t.setOnClickListener(v -> Channels.open(this, c, net, ui));
            t.setOnFocusChangeListener((v, has) -> {
                if (has) focusTile((Tile) v);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(16);
            news.addView(t, lp);
        }
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            Logo.load(this, cfg, it, Catalog.glyph(it.id));
            ui.post(() -> {
                for (int i = 0; i < dock.getChildCount(); i++) dock.getChildAt(i).invalidate();
                View f = getCurrentFocus();
                if (f instanceof Tile && ((Tile) f).item == it) focusTile((Tile) f);
            });
        });
    }

    /** The tagline and the glow follow the focused tile. */
    private void scrollTo(boolean below) {
        page.smoothScrollTo(0, below ? page.getChildAt(0).getHeight() - page.getHeight() : 0);
    }

    private void focusTile(Tile t) {
        Item it = t.item;
        scrollTo(Catalog.NEWS.equals(t.kind));
        CharSequence line = Catalog.NEWS.equals(t.kind) ? it.label : tagline(it);
        if (!line.toString().equals(tagline.getText().toString())) {
            tagline.setAlpha(0f);
            tagline.setText(line);
            tagline.animate().alpha(1f).setDuration(220).start();
        }
        if (it.glow == 0 && (it.banner != null || it.icon != null)) {
            io.execute(() -> {
                it.glow = FocusColor.vivid(it.banner != null ? it.banner : it.icon);
                ui.post(() -> {
                    if (getCurrentFocus() == t) light(it.glow, t);
                });
            });
        }
        light(it.glow, t);
    }

    /** One line per app from home.conf {@code tagline.<package>}; several, split by |, rotate per visit. */
    private CharSequence tagline(Item it) {
        String lines = cfg.get("tagline." + it.id, null);
        if (lines == null) return "";
        String[] each = lines.split("\\|");
        return each[(int) (cfg.visits() % each.length)].trim();
    }

    /** Moves the soft light behind the home row under {@code over} and eases it to {@code color}. */
    private void light(int color, View over) {
        GradientDrawable g = (GradientDrawable) glow.getBackground();
        if (over != null) {
            over.post(() -> {
                int[] a = new int[2], b = new int[2];
                over.getLocationOnScreen(a);
                ((View) glow.getParent()).getLocationOnScreen(b);
                float x = a[0] - b[0] + over.getWidth() / 2f - glow.getWidth() / 2f;
                float y = a[1] - b[1] + over.getHeight() / 2f - glow.getHeight() / 2f;
                if (glow.getVisibility() != View.VISIBLE) {
                    glow.setTranslationX(x);
                    glow.setTranslationY(y);
                    glow.setVisibility(View.VISIBLE);
                } else {
                    glow.animate().translationX(x).translationY(y).setDuration(260).start();
                }
            });
        }
        glow.animate().alpha(color == 0 ? 0.45f : 1f).setDuration(420).start();
        if (color == 0) return;
        int target = (color & 0x00FFFFFF) | 0x66000000;
        if (glowAnimation != null) glowAnimation.cancel();
        glowAnimation = ValueAnimator.ofArgb(glowColor, target);
        glowAnimation.setDuration(420);
        glowAnimation.addUpdateListener(v -> {
            glowColor = (Integer) v.getAnimatedValue();
            g.setColors(new int[] {glowColor, (glowColor & 0x00FFFFFF) | (glowColor >>> 24) / 4 << 24,
                    glowColor & 0x00FFFFFF});
        });
        glowAnimation.start();
    }

    private void focusFirst() {
        dockScroll.scrollTo(0, 0);
        page.scrollTo(0, 0);
        View first = dock.getChildAt(0);
        landed = first != null;
        if (first == null) first = news.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        Date now = new Date();
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", now));
        Calendar c = Calendar.getInstance();
        String today = ISO.format(now);
        String festival = Festivals.on(cfg, today);
        greet.setText(festival != null ? festival + " के ढेर सिनी शुभकामना" : greeting(c.get(Calendar.HOUR_OF_DAY)));
        c.add(Calendar.DAY_OF_YEAR, 7);
        String[] next = Festivals.next(today, ISO.format(c.getTime()));
        String line = DAY.format(now);
        if (next != null) {
            try {
                // Said as a day, never a second date beside today's: काल्हि (tomorrow), परसूं, or the weekday.
                Date on = ISO.parse(next[0]);
                long days = Math.round((on.getTime() - ISO.parse(today).getTime()) / 86_400_000.0);
                String when = days == 1 ? "काल्हि" : days == 2 ? "परसूं" : WEEKDAY.format(on);
                line += "   ·   " + when + " " + next[1];
            } catch (ParseException ignored) {
                // the built-in list is well formed
            }
        }
        date.setText(line);
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
    }

    /** In Angika; home.conf greet.morning, greet.noon, greet.evening, greet.night replace a line. */
    private String greeting(int hour) {
        if (hour >= 4 && hour < 12) return cfg.get("greet.morning", "भोर के प्रणाम");
        if (hour >= 12 && hour < 16) return cfg.get("greet.noon", "दुपहरिया के प्रणाम");
        if (hour >= 16 && hour < 20) return cfg.get("greet.evening", "साँझ के प्रणाम");
        return cfg.get("greet.night", "रात के प्रणाम");
    }

    private void background() {
        String mode = cfg.get(Config.BACKGROUND);
        File file = "image".equals(mode) || "adaptive-tint".equals(mode) ? cfg.wallpaper() : null;
        wall.setBackgroundColor(cfg.background());
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            return;
        }
        io.execute(() -> {
            Bitmap b = decode(file, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            ui.post(() -> {
                if (key.equals(wallKey)) wall.setImageBitmap(b);
            });
        });
    }

    /** Full 32-bit colour, so dark gradients in the wallpaper do not band. */
    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void launch(Intent i, String pkg) {
        if (Guard.AMAZON.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, t.item))
                .setItems(new String[] {"Move", "Rename", "Remove from home row", "App info"}, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (t.getParent() == null) break;
                            moving = t;
                            t.setMoving(true);
                            break;
                        case 1:
                            rename(t);
                            break;
                        case 2:
                            Catalog.setShown(cfg, Catalog.APPS, t.item, false);
                            renderDock();
                            Toast.makeText(this, "Still under More apps.", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", t.item.id, null)), null);
                    }
                })
                .show();
    }

    private void rename(Tile t) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(Catalog.label(cfg, t.item));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        String key = "name." + t.item.id;
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Rename")
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> {
                    String name = field.getText().toString().trim();
                    cfg.set(key, name.equals(t.item.label.toString()) ? null : name);
                    apply();
                })
                .setNeutralButton("Original", (dialog, w) -> {
                    cfg.set(key, null);
                    apply();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void shift(int by) {
        int from = dock.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= dock.getChildCount()) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        dock.removeViewAt(from);
        dock.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) ids.add(((Tile) dock.getChildAt(i)).item.id);
        Catalog.saveOrder(cfg, Catalog.APPS, ids);
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

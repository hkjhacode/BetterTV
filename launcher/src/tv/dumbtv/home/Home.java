package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.view.animation.DecelerateInterpolator;
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
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
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * The home screen: time and weather top left, bare icons top right, then a tagline, one row of
 * apps floating on the wallpaper with a soft glow in the focused app's colour, a row of live
 * news channels, and More apps.
 */
public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator(2f);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private Config cfg;
    private int applied = -1, appsGeneration;
    private boolean resumed, landed;
    private ImageView wall;
    private View shade, wash, dimmer, glow, wifi, more;
    private FrameLayout shelf;
    /** The opening / welcome-back curtain; {@link #away} is set while another app is in front. */
    private FrameLayout curtain;
    private ImageView curtainLogo;
    private TextView helloText, helloHand;
    private boolean away, ownScreen, welcoming;
    private View content;
    private String washFor;
    private LinearLayout head;
    /** Where Up, Down and Back return to: the last tile used in each row, the last top-bar item. */
    private View lastDock, lastNews, lastChip;
    private int targetScroll;
    private ScrollView page;
    private static final Locale HINDI = new Locale("hi", "IN");
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private static final SimpleDateFormat NOW = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEEE, d MMMM", HINDI);
    private static final SimpleDateFormat WEEKDAY = new SimpleDateFormat("EEEE", HINDI);
    private static final SimpleDateFormat DAY_EN = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);
    private static final SimpleDateFormat WEEKDAY_EN = new SimpleDateFormat("EEEE", Locale.ENGLISH);
    private TextView clock, weather, tagline, greet, date, panchang, wallTitle, wallLine;
    private Map<String, String> panchangDay;
    /** The panchang day (yyyy-MM-dd, turning at 6 AM) that {@link #panchangDay} is for. */
    private String panchangFor;
    /** Lines above the home row until home.conf sets tagline.<package>; | separates lines that rotate per visit. */
    private static final java.util.Map<String, String> TAGLINES = new java.util.HashMap<>();
    /** The small line under a wallpaper's title; home.conf wallpaper.line.<file name> replaces one. */
    private static final java.util.Map<String, String> WALL_LINES = new java.util.HashMap<>();

    static {
        // Family-friendly lines, a different one each time you come home; nothing from adult shows.
        TAGLINES.put("com.netflix.ninja", "Are you still watching?|Just one more episode.|Tudum.");
        TAGLINES.put("com.amazon.firetv.youtube", "Broadcast yourself.|Just one more video.|Like, share, subscribe.");
        TAGLINES.put("in.startv.hotstar", "Hakuna matata.|To infinity and beyond!|Avengers, assemble."
                + "|I am Iron Man.");
        TAGLINES.put("org.smarttube.stable", "No ads. Just videos.|Skip nothing. There's nothing to skip.");
        TAGLINES.put("com.indiatv.livetv.androidtv", "Aaj ki taaza khabar.|Khabar har pal.|Breaking news, live.");
        TAGLINES.put("com.amazon.firebat", "Dekh raha hai Binod.|Main hoon na.|Lights, camera, popcorn.");
        TAGLINES.put("com.amazon.venezia", "Something new to try.|There's an app for that.");
        WALL_LINES.put("the-batman", "I am vengeance.");
        WALL_LINES.put("the-batman-batmobile", "Why do we fall? So we can learn to pick ourselves up.");
        WALL_LINES.put("iron-man", "Sometimes you gotta run before you can walk.");
        WALL_LINES.put("iron-man-suit", "I am Iron Man.");
        WALL_LINES.put("captain-america", "I can do this all day.");
        WALL_LINES.put("captain-america-shield", "I can do this all day.");
        WALL_LINES.put("thor", "Bring me Thanos!");
        WALL_LINES.put("thor-dark-world", "I'd rather be a good man than a great king.");
        WALL_LINES.put("hulk", "That's my secret. I'm always angry.");
        WALL_LINES.put("hulkbuster", "Avengers, assemble.");
        WALL_LINES.put("ultron", "There are no strings on me.");
        WALL_LINES.put("black-panther", "Wakanda forever.");
        WALL_LINES.put("guardians-of-the-galaxy", "We are Groot.");
        WALL_LINES.put("patrick-jane", "There's no such thing as psychics.");
        WALL_LINES.put("red-john", "Tyger Tyger, burning bright.");
        WALL_LINES.put("haikyuu-jump", "Fly high.");
        WALL_LINES.put("haikyuu-court", "The view from the top.");
        WALL_LINES.put("vivekananda", "Arise, awake, and stop not till the goal is reached.");
        WALL_LINES.put("kalam", "Dream, dream, dream.");
    }
    private HorizontalScrollView dockScroll;
    private LinearLayout dock, news;
    private List<Item> apps = new ArrayList<>();
    private Tile moving;
    private String wallKey;
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
        Crash.install(this);
        Festivals.load(this);
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
        if (away) {
            away = false;
            welcome();
        }
        // put the Home button back if Fire OS dropped it (force-stop, app update)
        Guard.sync(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.version() != applied) apply();
        else background();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
        Weather.current(cfg, net, ui, text -> {
            weather.setText(text);
            weather.setVisibility(View.VISIBLE);
        });
        loadPanchang();
        View focused = getCurrentFocus();
        if (focused instanceof Tile) focusTile((Tile) focused);
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
        playWelcome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        // anything that covers the home screen except our own screens counts as leaving
        if (!ownScreen) away = true;
        ownScreen = false;
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
            else if (f instanceof Tile && Catalog.NEWS.equals(((Tile) f).kind)) channelMenu((Tile) f);
            else own(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this) {
            @Override
            public View focusSearch(View focused, int direction) {
                View next = navigate(focused, direction);
                return next != null ? next : super.focusSearch(focused, direction);
            }
        };
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        wash = new View(this);
        wash.setAlpha(0f);
        root.addView(wash, new FrameLayout.LayoutParams(MATCH, MATCH));
        dimmer = new View(this);
        dimmer.setBackgroundColor(0x99000000);
        dimmer.setAlpha(0f);
        root.addView(dimmer, new FrameLayout.LayoutParams(MATCH, MATCH));
        // The rows sit on dark: clear down to about half the height, then fading to black.
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x00000000, 0x0D000000, 0x8C000000, 0xD9000000}));
        root.addView(bottom, new FrameLayout.LayoutParams(MATCH, MATCH));
        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xB3000000, 0x66000000, 0x00000000}));
        root.addView(top, new FrameLayout.LayoutParams(MATCH, dp(230)));
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
        LinearLayout poster = new LinearLayout(this);
        poster.setOrientation(LinearLayout.VERTICAL);
        poster.setGravity(Gravity.END);
        poster.setPadding(0, dp(168), dp(56), 0);
        wallTitle = Ui.text(this, 64, 0x1FFFFFFF, Ui.outfitMedium);
        wallTitle.setLetterSpacing(0.12f);
        wallTitle.setSingleLine();
        wallTitle.setGravity(Gravity.END);
        wallLine = Ui.text(this, 18, 0x8CFFFFFF, Ui.outfitLight);
        wallLine.setGravity(Gravity.END);
        wallLine.setPadding(0, dp(6), dp(6), 0);
        poster.addView(wallTitle);
        poster.addView(wallLine);
        root.addView(poster, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP | Gravity.END));
        // The curtain for opening and coming back: black, the app's logo, and a line with a waving hand.
        curtain = new FrameLayout(this);
        curtain.setBackgroundColor(0xFF000000);
        curtain.setVisibility(View.GONE);
        curtainLogo = new ImageView(this);
        curtain.addView(curtainLogo, new FrameLayout.LayoutParams(dp(200), dp(120), Gravity.CENTER));
        LinearLayout words = new LinearLayout(this);
        words.setGravity(Gravity.CENTER);
        helloHand = Ui.text(this, 34, Ui.TEXT, Ui.outfitMedium);
        helloHand.setPadding(0, 0, dp(14), 0);
        helloText = Ui.text(this, 34, Ui.TEXT, Ui.outfitLight);
        words.addView(helloHand);
        words.addView(helloText);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER);
        wlp.topMargin = dp(120);
        curtain.addView(words, wlp);
        root.addView(curtain, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Two stops: the home screen itself, and below it the live news. The wallpaper darkens
        // as the page moves down.
        page = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return 0;
            }

            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                return !vertical(e) && super.executeKeyEvent(e);
            }
        };
        page.setFocusable(false);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            dimmer.setAlpha(Math.min(1f, y / (float) Math.max(1, v.getHeight() / 2)));
            // the wallpaper's title stays put while the rows scroll up: let it go before they meet
            poster.setAlpha(Math.max(0f, 1f - y / (float) Math.max(1, v.getHeight() / 4)));
        });
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));
        content = page;
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

        head = new LinearLayout(this);
        head.setGravity(Gravity.TOP);
        head.setClipChildren(false);
        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        greet = Ui.text(this, 17, Ui.MUTED, Ui.outfitLight);
        clock = Ui.text(this, 60, Ui.TEXT, Ui.outfitLight);
        clock.setPadding(0, dp(4), 0, dp(6));
        date = Ui.text(this, 16, Ui.MUTED, Ui.outfitLight);
        for (TextView t : new TextView[] {greet, clock, date}) t.setShadowLayer(dp(10), 0, dp(1), 0xCC000000);
        when.addView(greet);
        when.addView(clock);
        when.addView(date);
        head.addView(when, new LinearLayout.LayoutParams(0, WRAP, 1f));
        weather = pill(0, "", v -> own(new Intent(this, Weather.class)));
        weather.setVisibility(View.GONE);
        head.addView(weather);
        panchang = pill(0, "पञ्चाङ्ग", v -> showPanchang());
        panchang.setVisibility(View.GONE);
        head.addView(panchang);
        wifi = pill(R.drawable.ic_wifi, "Wi-Fi", v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS), null));
        wifi.setId(View.generateViewId());
        head.addView(wifi);
        head.addView(circle(R.drawable.ic_tune, "Options", v -> own(new Intent(this, Options.class))));
        head.addView(circle(R.drawable.ic_settings, "Settings", v -> openSettings()));
        column.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        tagline = Ui.text(this, 34, 0xF2FFFFFF, Ui.outfitLight);
        tagline.setSingleLine();
        column.addView(tagline, new LinearLayout.LayoutParams(MATCH, WRAP));

        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        // room below for the focused tile's name: the tile grows 18% around its middle, which pushes
        // the name about 10dp past the row, and the scroll view cuts off whatever leaves its bounds
        dock.setPadding(dp(4), dp(16), dp(4), dp(16));
        dockScroll = new HorizontalScrollView(this) {
            /** Keeps the focused tile in view with a little room, except near either end. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                if (getChildCount() == 0) return 0;
                int range = Math.max(0, getChildAt(0).getWidth() - getWidth());
                int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                return target - getScrollX();
            }

            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                return !sideways(e) && super.executeKeyEvent(e);
            }
        };
        dockScroll.setFocusable(false);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.addView(dock, new FrameLayout.LayoutParams(WRAP, WRAP));
        // Glassy look: a frosted shelf the app row floats on (shown in apply())
        shelf = new FrameLayout(this);
        shelf.setClipChildren(false);
        shelf.setPadding(0, dp(6), 0, dp(10));
        shelf.addView(dockScroll, new FrameLayout.LayoutParams(MATCH, WRAP));
        column.addView(shelf, new LinearLayout.LayoutParams(MATCH, WRAP));

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
        HorizontalScrollView newsScroll = new HorizontalScrollView(this) {
            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                return !sideways(e) && super.executeKeyEvent(e);
            }
        };
        newsScroll.setFocusable(false);
        newsScroll.setHorizontalScrollBarEnabled(false);
        newsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        newsScroll.setClipChildren(false);
        newsScroll.setClipToPadding(false);
        newsScroll.addView(news, new FrameLayout.LayoutParams(WRAP, WRAP));
        below.addView(newsScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView more = Ui.text(this, 13, Ui.TEXT, Ui.outfitMedium);
        this.more = more;
        more.setText("More apps");
        more.setPadding(dp(18), dp(8), dp(18), dp(8));
        more.setFocusable(true);
        more.setClickable(true);
        more.setBackground(glassy() ? Ui.glass(dp(16), dp(1)) : Ui.round(0x1FFFFFFF, dp(16)));
        more.setOnClickListener(v -> own(new Intent(this, Apps.class)));
        more.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(200).start();
            v.setBackground(has ? Ui.round(0xE6FFFFFF, dp(16)) : glassy() ? Ui.glass(dp(16), dp(1)) : Ui.round(0x1FFFFFFF, dp(16)));
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
        curtain.bringToFront();
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
                flatten();
                lastChip = view;
                tagline.setText("");
                light(0, null);
                scrollTo(false);
            }
        });
    }

    /**
     * The remote moves in straight lines: Left and Right stay inside a row and stop at its ends;
     * Up and Down go between the top bar, the home row, the live row and More apps, landing on the
     * item last used there. Anything else falls back to Android's own search.
     */
    private View navigate(View from, int dir) {
        boolean side = dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT;
        ViewGroup row = from.getParent() instanceof ViewGroup ? (ViewGroup) from.getParent() : null;
        if (side && (row == head || row == dock || row == news)) {
            int i = row.indexOfChild(from) + (dir == View.FOCUS_LEFT ? -1 : 1);
            return i >= 0 && i < row.getChildCount() ? row.getChildAt(i) : from;
        }
        if (from == more) {
            return dir == View.FOCUS_UP ? in(news, lastNews) : from;
        }
        if (row == head) {
            return dir == View.FOCUS_DOWN ? in(dock, lastDock) : from;
        }
        if (row == dock) {
            if (dir == View.FOCUS_UP) return lastChip != null ? lastChip : wifi;
            if (dir == View.FOCUS_DOWN) return in(news, lastNews);
        }
        if (row == news) {
            if (dir == View.FOCUS_UP) return in(dock, lastDock);
            if (dir == View.FOCUS_DOWN) return more;
        }
        return null;
    }

    private static boolean vertical(KeyEvent e) {
        int k = e.getKeyCode();
        return k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_PAGE_UP || k == KeyEvent.KEYCODE_PAGE_DOWN || k == KeyEvent.KEYCODE_SPACE;
    }

    private static boolean sideways(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /** {@code last} if it is still in {@code row}, else the row's first item, else nothing. */
    private static View in(ViewGroup row, View last) {
        if (last != null && last.getParent() == row) return last;
        return row.getChildCount() > 0 ? row.getChildAt(0) : null;
    }

    private GradientDrawable idleChip() {
        if (glassy()) return Ui.glass(dp(19), dp(1));
        GradientDrawable g = Ui.round(0x1AFFFFFF, dp(19));
        g.setStroke(Math.max(1, dp(1)), 0x24FFFFFF);
        return g;
    }

    private boolean glassy() {
        return Ui.glassy(cfg);
    }

    private void apply() {
        applied = cfg.version();
        shelf.setBackground(glassy() ? Ui.glass(dp(28), dp(1)) : null);
        shelf.setPadding(0, dp(glassy() ? 10 : 6), 0, dp(glassy() ? 8 : 0));
        for (int i = 0; i < head.getChildCount(); i++) {
            View chip = head.getChildAt(i);
            if (chip.isFocusable() && !chip.isFocused()) chip.setBackground(idleChip());
        }
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

    /** The live row, then a + card that adds a YouTube channel. */
    private void renderNews() {
        if (moving != null && Catalog.NEWS.equals(moving.kind)) stopMoving();
        View focused = getCurrentFocus();
        Object keep = focused instanceof Tile && Catalog.NEWS.equals(((Tile) focused).kind) ? focused.getTag() : null;
        String keepId = keep instanceof Channels.Channel ? ((Channels.Channel) keep).id : null;
        news.removeAllViews();
        View restore = null;
        for (Channels.Channel c : Channels.all(cfg)) {
            String name = Channels.label(cfg, c);
            Item it = new Item(c.id, name, null);
            File face = c.youtube != null ? Channels.avatar(cfg, c.youtube) : null;
            if (c.art != 0) it.banner = getDrawable(c.art).mutate();
            else if (face != null && face.isFile()) it.banner = new BitmapDrawable(getResources(), face.getPath());
            Tile t = newsTile(it, name);
            t.setTag(c);
            t.setOnClickListener(v -> Channels.open(this, c, net, ui));
            t.setOnLongClickListener(v -> {
                channelMenu((Tile) v);
                return true;
            });
            if (c.id.equals(keepId)) restore = t;
        }
        Tile add = newsTile(new Item("add", "+  YouTube channel", null), "+  YouTube channel");
        add.setTag("add");
        add.setOnClickListener(v -> addChannel());
        if ("add".equals(keep)) restore = add;
        if (restore != null) restore.requestFocus();
    }

    private Tile newsTile(Item it, String name) {
        Tile t = new Tile(this, it, Catalog.NEWS, name, dp(118), dp(66), "never", cfg.accent(), true);
        t.setOnFocusChangeListener((v, has) -> {
            if (has) focusTile((Tile) v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.rightMargin = dp(16);
        news.addView(t, lp);
        return t;
    }

    private void channelMenu(Tile t) {
        if (!(t.getTag() instanceof Channels.Channel)) return;
        Channels.Channel c = (Channels.Channel) t.getTag();
        new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle(Channels.label(cfg, c))
                .setItems(new String[] {"Move", "Rename", "Remove from this row"}, (d, which) -> {
                    if (which == 0) {
                        moving = t;
                        t.setMoving(true);
                    } else if (which == 1) {
                        ask("Rename", Channels.label(cfg, c), name -> {
                            Channels.rename(cfg, c, name);
                            renderNews();
                        });
                    } else {
                        Channels.remove(cfg, c);
                        renderNews();
                    }
                })
                .show();
    }

    /** Search YouTube for a channel by name, pick one, and it joins the live row. */
    private void addChannel() {
        ask("Add a YouTube channel", "", query -> {
            Toast.makeText(this, "Searching YouTube…", Toast.LENGTH_SHORT).show();
            net.execute(() -> {
                List<String[]> found;
                try {
                    found = Live.channels(Live.get("https://www.youtube.com/results?sp=EgIQAg%253D%253D&search_query="
                            + URLEncoder.encode(query, "UTF-8")));
                } catch (IOException e) {
                    found = new ArrayList<>();
                }
                List<String[]> results = found;
                ui.post(() -> pickChannel(results));
            });
        });
    }

    private void pickChannel(List<String[]> results) {
        if (results.isEmpty()) {
            Toast.makeText(this, "No channel found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[Math.min(10, results.size())];
        for (int i = 0; i < names.length; i++) names[i] = results.get(i)[1];
        new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle("Pick the channel")
                .setItems(names, (d, which) -> {
                    String[] ch = results.get(which);
                    net.execute(() -> {
                        Channels.fetchAvatar(cfg, ch[0], ch[2]);
                        ui.post(() -> {
                            Channels.add(cfg, ch[0], ch[1]);
                            renderNews();
                        });
                    });
                })
                .show();
    }

    /** A one-line text prompt with the on-screen keyboard; empty answers are ignored. */
    private void ask(String title, String start, Consumer<String> done) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(start);
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        AlertDialog d = new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (dialog, w) -> {
                    String text = field.getText().toString().trim();
                    if (!text.isEmpty()) done.accept(text);
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
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
    private void flatten() {
        for (int i = 0; i < dock.getChildCount(); i++) {
            View v = dock.getChildAt(i);
            if (v.isFocused()) continue;
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).translationY(0).setDuration(220).start();
        }
    }

    private void scrollTo(boolean below) {
        targetScroll = below ? Math.max(0, page.getChildAt(0).getHeight() - page.getHeight()) : 0;
        page.smoothScrollTo(0, targetScroll);
    }

    private void focusTile(Tile t) {
        if (Catalog.NEWS.equals(t.kind)) flatten();
        if (Catalog.NEWS.equals(t.kind)) lastNews = t;
        else lastDock = t;
        Item it = t.item;
        scrollTo(Catalog.NEWS.equals(t.kind));
        CharSequence line = Catalog.NEWS.equals(t.kind) ? it.label : tagline(it);
        if (!line.toString().equals(tagline.getText().toString())) {
            tagline.setAlpha(0f);
            tagline.setTranslationY(dp(10));
            tagline.setText(line);
            tagline.animate().alpha(1f).translationY(0).setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
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
        // after layout, so the first focus at start-up is centred too
        if (Catalog.APPS.equals(t.kind) && "carousel".equals(cfg.get(Config.ROW))) t.post(() -> carousel(t));
    }

    /**
     * Experiment (Options › Home row style › Carousel): the focused logo is biggest and in front;
     * the others shrink, dim and turn away a little more with each step from it, like a cover flow.
     */
    private void carousel(Tile focused) {
        int at = dock.indexOfChild(focused);
        // slide the whole row so the selected app sits in the middle of the screen
        int[] d0 = new int[2];
        dock.getLocationOnScreen(d0);
        float left = d0[0] - dock.getTranslationX();
        float shift = getResources().getDisplayMetrics().widthPixels / 2f
                - (left + focused.getLeft() + focused.getWidth() / 2f);
        if (!"on".equals(cfg.get(Config.CENTER))) shift = 0;
        dock.animate().translationX(shift).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        for (int i = 0; i < dock.getChildCount(); i++) {
            View v = dock.getChildAt(i);
            if (v == focused) continue;
            int d = i - at, n = Math.abs(d);
            float scale = Math.max(0.62f, 1f - 0.11f * n);
            v.animate().scaleX(scale).scaleY(scale).alpha(Math.max(0.35f, 1f - 0.18f * n))
                    .translationY(0)
                    .setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
        // it comes out front: bigger than the flat row's focus, lifted a little, drawn over the rest
        focused.setZ(2f);
        focused.animate().scaleX(1.42f).scaleY(1.42f).translationY(-dp(10)).alpha(1f)
                .setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
    }

    /** One line per app from home.conf {@code tagline.<package>}; several, split by |, rotate per visit. */
    private CharSequence tagline(Item it) {
        String lines = cfg.get("tagline." + it.id, TAGLINES.get(it.id));
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
                if (over.getParent() == dock && "carousel".equals(cfg.get(Config.ROW))
                        && "on".equals(cfg.get(Config.CENTER))) {
                    // the row is sliding the selected app to the centre
                    x = getResources().getDisplayMetrics().widthPixels / 2f - b[0] - glow.getWidth() / 2f;
                }
                // where the tile will sit once the page has scrolled, not where it is mid-scroll
                float y = a[1] + page.getScrollY() - targetScroll - b[1] + over.getHeight() / 2f - glow.getHeight() / 2f;
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
        if (target == glowColor) return;
        // set the gradient once and fade the view: rebuilding a screen-sized radial shader every
        // frame was the main cause of dropped frames on this stick
        glowColor = target;
        g.setColors(new int[] {target, (target & 0x00FFFFFF) | 0x1A000000, target & 0x00FFFFFF});
        glow.setAlpha(0.55f);
        glow.animate().alpha(1f).setDuration(300).start();
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
        String lang = cfg.get(Config.LANGUAGE);
        String wish = Festivals.wish(cfg, lang, today);
        greet.setText(wish != null ? wish : greeting(lang, c.get(Calendar.HOUR_OF_DAY)));
        c.add(Calendar.DAY_OF_YEAR, 7);
        String[] next = Festivals.next(today, ISO.format(c.getTime()), lang);
        boolean english = Lang.ENGLISH.equals(lang);
        String line = (english ? DAY_EN : DAY).format(now);
        String today2 = panchangFestival();
        if (today2 != null) {
            line += "   ·   " + today2;
        } else if (next != null) {
            try {
                // Said as a day, never a second date beside today's: tomorrow, day after, or the weekday.
                Date on = ISO.parse(next[0]);
                long days = Math.round((on.getTime() - ISO.parse(today).getTime()) / 86_400_000.0);
                String when = Lang.daysAway(lang, days);
                if (when == null) when = (english ? WEEKDAY_EN : WEEKDAY).format(on);
                line += "   ·   " + when + " " + next[1];
            } catch (ParseException ignored) {
                // the built-in list is well formed
            }
        }
        date.setText(line);
        // the tithi changes during the day: follow it every minute
        if (panchangDay != null) panchang.setText(Panchang.tithiLine(panchangDay, NOW.format(now)));
        colours(today);
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
    }

    /** Today's panchang, fetched at most every three hours while the home screen shows. */
    private void loadPanchang() {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < 6) c.add(Calendar.DAY_OF_YEAR, -1);
        String day = ISO.format(c.getTime());
        if (day.equals(panchangFor)) return;
        net.execute(() -> {
            File cache = new File(cfg.dir, "panchang.txt");
            Map<String, String> p = Panchang.read(cache, day);
            if (p == null) {
                try {
                    p = Panchang.fetch(day);
                    Panchang.write(cache, day, p);
                } catch (IOException ignored) {
                    // no network: the pill stays hidden and the next visit tries again
                    return;
                }
            }
            Map<String, String> got = p;
            if (!got.containsKey("drik.end") && !got.containsKey("तिथि")) return;
            ui.post(() -> {
                panchangDay = got;
                panchangFor = day;
                panchang.setVisibility(View.VISIBLE);
                tick();
            });
        });
    }

    /** Mithila's festival for today, if any, for the date line; none between midnight and 6 AM, when the panchang is still yesterday's. */
    private String panchangFestival() {
        return panchangDay == null || !ISO.format(new Date()).equals(panchangFor) ? null : Panchang.festival(panchangDay);
    }

    private void showPanchang() {
        if (panchangDay == null) return;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : panchangDay.entrySet()) {
            // one line each, so the whole day fits on the screen with its OK button
            if (!e.getKey().startsWith("drik.")) b.append(e.getKey()).append(":  ").append(e.getValue()).append("\n");
        }
        b.append("\nmithilapanchang.com · drikpanchang.com");
        new AlertDialog.Builder(this, Ui.dialog(cfg))
                .setTitle("आजुक पञ्चाङ्ग")
                .setMessage(b.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Options › Colours: on a festival day (or through a series like Navratri or Chhath) the screen
     * takes a warm wash in that festival's colour; "Always colourful" keeps a saffron-to-violet wash
     * on ordinary days too; "Calm" never colours it.
     */
    private void colours(String today) {
        String mode = cfg.get(Config.COLOURS);
        String c = "calm".equals(mode) ? null : Festivals.colour(today);
        if (c == null && "always".equals(mode)) c = "#FF8A3D,#8E5CFF";
        String key = mode + c;
        if (key.equals(washFor)) return;
        washFor = key;
        if (c == null) {
            wash.animate().alpha(0f).setDuration(600).start();
            greet.setTextColor(Ui.MUTED);
            return;
        }
        String[] parts = c.split(",");
        int a = Color.parseColor(parts[0]), b = parts.length > 1 ? Color.parseColor(parts[1]) : a;
        wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {
            (a & 0xFFFFFF) | 0x59000000, (b & 0xFFFFFF) | 0x26000000, 0x00000000, (a & 0xFFFFFF) | 0x33000000}));
        wash.animate().alpha(1f).setDuration(800).start();
        // the greeting and the clock pick the colour up too
        greet.setTextColor((a & 0xFFFFFF) | 0xE6000000);
    }

    /** Options › Language; home.conf greet.morning, greet.noon, greet.evening, greet.night replace a line. */
    private String greeting(String lang, int hour) {
        String key = hour >= 4 && hour < 12 ? "morning" : hour >= 12 && hour < 16 ? "noon" : hour >= 16 && hour < 20 ? "evening" : "night";
        return cfg.get("greet." + key, Lang.greeting(lang, hour));
    }

    private void background() {
        String mode = cfg.get(Config.BACKGROUND);
        File file = "image".equals(mode) || "adaptive-tint".equals(mode) ? cfg.wallpaper() : null;
        wall.setBackgroundColor(cfg.background());
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        caption(file);
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            return;
        }
        io.execute(() -> {
            // half size in each direction is enough under the shade and scrim: a quarter of the memory
            Bitmap b = decode(file, getResources().getDisplayMetrics().widthPixels / 2,
                    getResources().getDisplayMetrics().heightPixels / 2);
            ui.post(() -> {
                if (!key.equals(wallKey)) return;
                // crossfade from the old picture, never through black
                android.graphics.drawable.Drawable old = wall.getDrawable();
                android.graphics.drawable.BitmapDrawable next = new android.graphics.drawable.BitmapDrawable(getResources(), b);
                Ui.wall = b;
                if (old == null || old instanceof android.graphics.drawable.TransitionDrawable) {
                    wall.setImageDrawable(next);
                } else {
                    android.graphics.drawable.TransitionDrawable fade = new android.graphics.drawable.TransitionDrawable(
                            new android.graphics.drawable.Drawable[] {old, next});
                    fade.setCrossFadeEnabled(true);
                    wall.setImageDrawable(fade);
                    fade.startTransition(500);
                    wall.postDelayed(() -> {
                        if (wall.getDrawable() == fade) wall.setImageDrawable(next);
                    }, 600);
                }
            });
        });
    }

    /** The wallpaper's name as poster type ("the-batman.png" is THE BATMAN) and its line, if known. */
    private void caption(File file) {
        if (file == null || "off".equals(cfg.get("wallpaper.text", "on"))) {
            wallTitle.setText("");
            wallLine.setText("");
            return;
        }
        String name = file.getName().replaceFirst("\\.[^.]+$", "");
        String title = name.replace('-', ' ').toUpperCase(Locale.ROOT);
        // one line that always fits: shrink the type until the whole title is on screen
        float room = getResources().getDisplayMetrics().widthPixels - dp(130);
        wallTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        while (wallTitle.getPaint().measureText(title) > room && wallTitle.getTextSize() > dp(24)) {
            wallTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, wallTitle.getTextSize() * 0.92f);
        }
        wallTitle.setText(title);
        wallLine.setText(cfg.get("wallpaper.line." + name, WALL_LINES.containsKey(name) ? WALL_LINES.get(name) : ""));
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
        Intent go = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (pkg == null || "quick".equals(cfg.get(Config.MOTION))) {
            start(go);
            return;
        }
        bye(pkg);
        // the app opens behind the curtain once the logo has rushed forward
        curtain.postDelayed(() -> start(go), 520);
    }

    /** Opens one of BetterTV's own screens: no curtain on the way back. */
    private void own(Intent i) {
        ownScreen = true;
        startActivity(i);
    }

    private void start(Intent go) {
        try {
            startActivity(go, ActivityOptions.makeCustomAnimation(this, R.anim.screen_in, R.anim.screen_out).toBundle());
        } catch (ActivityNotFoundException | SecurityException e) {
            curtain.setVisibility(View.GONE);
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Like Netflix opening: the screen goes dark and the app's logo rushes toward you, with "Bye"
     * and a waving hand. All GPU property animations on views that already exist.
     */
    private void bye(String pkg) {
        Item it = null;
        for (Item a : apps) if (a.id.equals(pkg)) it = a;
        curtainLogo.setImageBitmap(it == null ? null : it.logo);
        curtainLogo.setScaleX(0.7f);
        curtainLogo.setScaleY(0.7f);
        curtainLogo.setAlpha(1f);
        curtain.setAlpha(0f);
        curtain.setVisibility(View.VISIBLE);
        say("Bye");
        curtain.animate().alpha(1f).setDuration(160).start();
        curtainLogo.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(EASE_OUT).withEndAction(() ->
                curtainLogo.animate().scaleX(5f).scaleY(5f).alpha(0f).setDuration(320)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(2f)).start()).start();
        if (content != null) content.animate().scaleX(1.08f).scaleY(1.08f).setDuration(480).start();
    }

    /**
     * Coming back: black at once (the Home button's cover is black too), then, once the window is
     * really on screen, "Welcome back" with a waving hand and the home screen zooming in to rest.
     */
    private void welcome() {
        if ("quick".equals(cfg.get(Config.MOTION))) {
            curtain.setVisibility(View.GONE);
            return;
        }
        curtainLogo.setImageDrawable(null);
        helloText.setText("");
        helloHand.setText("");
        curtain.animate().cancel();
        curtain.setAlpha(1f);
        curtain.setVisibility(View.VISIBLE);
        if (content != null) {
            content.setScaleX(1.08f);
            content.setScaleY(1.08f);
        }
        welcoming = true;
        // onEnterAnimationComplete starts it; this covers a return that has no enter animation
        curtain.postDelayed(this::playWelcome, 900);
    }

    private void playWelcome() {
        if (!welcoming) return;
        welcoming = false;
        say("Welcome back");
        if (content != null) {
            content.animate().scaleX(1f).scaleY(1f).setStartDelay(700).setDuration(520).setInterpolator(EASE_OUT).start();
        }
        curtain.animate().alpha(0f).setStartDelay(700).setDuration(420)
                .withEndAction(() -> curtain.setVisibility(View.GONE)).start();
    }

    private void say(String words) {
        helloText.setText(words);
        helloHand.setText("\uD83D\uDC4B");
        helloText.setAlpha(0f);
        helloHand.setAlpha(0f);
        helloText.animate().alpha(1f).setStartDelay(80).setDuration(220).start();
        helloHand.animate().alpha(1f).setStartDelay(80).setDuration(220).start();
        helloHand.post(() -> {
            helloHand.setPivotX(helloHand.getWidth() * 0.7f);
            helloHand.setPivotY(helloHand.getHeight() * 0.9f);
        });
        ValueAnimator wave = ValueAnimator.ofFloat(0f, 1f);
        wave.setDuration(1100);
        wave.addUpdateListener(a -> {
            float k = (Float) a.getAnimatedValue();
            helloHand.setRotation((float) Math.sin(k * Math.PI * 6) * 20f * (1 - k));
        });
        wave.start();
    }

    private void menu(Tile t) {
        new AlertDialog.Builder(this, Ui.dialog(cfg))
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
        AlertDialog d = new AlertDialog.Builder(this, Ui.dialog(cfg))
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
        ViewGroup row = (ViewGroup) moving.getParent();
        // the + card stays last
        int last = row.getChildCount() - (row == news ? 2 : 1);
        int from = row.indexOfChild(moving), to = from + by;
        if (to < 0 || to > last) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        row.removeViewAt(from);
        row.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        List<String> ids = new ArrayList<>();
        if (Catalog.NEWS.equals(t.kind)) {
            for (int i = 0; i < news.getChildCount(); i++) {
                Object c = news.getChildAt(i).getTag();
                if (c instanceof Channels.Channel) ids.add(((Channels.Channel) c).id);
            }
            Channels.saveOrder(cfg, ids);
        } else {
            for (int i = 0; i < dock.getChildCount(); i++) ids.add(((Tile) dock.getChildAt(i)).item.id);
            Catalog.saveOrder(cfg, Catalog.APPS, ids);
        }
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

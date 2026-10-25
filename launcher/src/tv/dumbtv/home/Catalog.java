package tv.dumbtv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where tiles come from, and which of them the user wants on the home screen. */
final class Catalog {
    static final String APPS = "apps", INPUTS = "inputs", NEWS = "news";
    /** The home row: only these apps, in this order. Every other app is under More apps. */
    static final String DOCK = "apps.dock";
    private static final String DEFAULT_DOCK = "com.netflix.ninja,com.amazon.firetv.youtube,in.startv.hotstar,"
            + "org.smarttube.stable,com.indiatv.livetv.androidtv,com.amazon.firebat,com.amazon.venezia";

    private Catalog() {}

    /** The bundled white glyph for the usual apps, or 0. */
    static int glyph(String pkg) {
        switch (pkg) {
            case "com.netflix.ninja": return R.drawable.logo_netflix;
            case "com.amazon.firetv.youtube": return R.drawable.logo_youtube;
            case "org.smarttube.stable": return R.drawable.logo_smarttube;
            case "in.startv.hotstar": return R.drawable.logo_star;
            case "com.amazon.venezia": return R.drawable.logo_apps;
            case "com.amazon.firebat": return R.drawable.logo_prime;
            default: return 0;
        }
    }

    /** Every launchable app, TV apps first; drawables are left for {@link #art}. */
    static List<Item> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Item> found = new LinkedHashMap<>();
        collect(pm, found, Intent.CATEGORY_LEANBACK_LAUNCHER, true, c.getPackageName());
        collect(pm, found, Intent.CATEGORY_LAUNCHER, false, c.getPackageName());
        List<Item> out = new ArrayList<>(found.values());
        Collator collator = Collator.getInstance();
        Collections.sort(out, (a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return out;
    }

    private static void collect(PackageManager pm, Map<String, Item> found, String category,
            boolean leanback, String self) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(self) || found.containsKey(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(ai.packageName, ai.name);
            Item it = new Item(ai.packageName, ri.loadLabel(pm), launch);
            it.info = ai;
            it.leanback = leanback;
            found.put(ai.packageName, it);
        }
    }

    /** Loads a banner, or an icon when the app ships no banner. Slow: call off the UI thread. */
    static void art(PackageManager pm, Item it) {
        if (it.info == null) return;
        it.banner = it.info.loadBanner(pm);
        if (it.banner == null) it.icon = it.info.loadIcon(pm);
    }

    /**
     * HDMI, AV and other passthrough inputs; HDMI first in port order. The intent is the TV's own
     * app for the input; {@link Input#intent} decides whether a tile opens that or the built-in
     * screen.
     */
    static List<Item> inputs(Context c, TvInputManager tv) {
        List<Item> out = new ArrayList<>();
        if (tv == null) return out;
        List<Item> other = new ArrayList<>();
        for (TvInputInfo in : tv.getTvInputList()) {
            if (!in.isPassthroughInput() || in.isHidden(c)) continue;
            CharSequence label = in.loadCustomLabel(c);
            if (TextUtils.isEmpty(label)) label = in.loadLabel(c);
            Intent view = new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(in.getId()));
            Item it = new Item(in.getId(), label, view);
            it.state = tv.getInputState(in.getId());
            (in.getType() == TvInputInfo.TYPE_HDMI ? out : other).add(it);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(out, (a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        Collections.sort(other, (a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        out.addAll(other);
        return out;
    }

    static CharSequence label(Config cfg, Item it) {
        return cfg.get("name." + it.id, it.label.toString());
    }

    // shortcut: an empty home row reads as "never set" and brings the default back; store a
    // marker if someone really wants no apps on it.
    static List<String> dock(Config cfg) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.get(DOCK, DEFAULT_DOCK).split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    static boolean shown(Config cfg, String kind, Item it) {
        if (APPS.equals(kind)) return dock(cfg).contains(it.id);
        return !cfg.list(kind + ".hide").contains(it.id);
    }

    static void setShown(Config cfg, String kind, Item it, boolean on) {
        if (APPS.equals(kind)) {
            List<String> dock = dock(cfg);
            dock.remove(it.id);
            if (on) dock.add(it.id);
            cfg.setList(DOCK, dock);
            return;
        }
        List<String> hide = cfg.list(kind + ".hide");
        hide.remove(it.id);
        if (!on) hide.add(it.id);
        cfg.setList(kind + ".hide", hide);
    }

    private static List<String> order(Config cfg, String kind) {
        return APPS.equals(kind) ? dock(cfg) : cfg.list(kind + ".order");
    }

    /** The shown items, in the user's order; anything never moved keeps catalog order. */
    static List<Item> visible(Config cfg, String kind, List<Item> all) {
        List<String> order = order(cfg, kind);
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (shown(cfg, kind, it)) out.add(it);
        }
        Collections.sort(out, (a, b) -> Integer.compare(rank(order, a.id), rank(order, b.id)));
        return out;
    }

    private static int rank(List<String> order, String id) {
        int i = order.indexOf(id);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** Saves {@code ids} as the new order, keeping the old positions of hidden items after them. */
    static void saveOrder(Config cfg, String kind, List<String> ids) {
        List<String> order = new ArrayList<>(ids);
        for (String id : order(cfg, kind)) {
            if (!order.contains(id)) order.add(id);
        }
        cfg.setList(APPS.equals(kind) ? DOCK : kind + ".order", order);
    }
}

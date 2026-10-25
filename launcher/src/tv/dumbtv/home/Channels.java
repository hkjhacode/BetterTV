package tv.dumbtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The live row. Veto channels open straight into Veto's player through its own
 * vetoworld.com/content/live link (streams recorded from Veto's Live TV page on this TV).
 * YouTube channels, which you add, rename, move and remove from the home screen, open their
 * most-watched live stream in SmartTube, or YouTube when SmartTube is missing.
 */
final class Channels {
    static final String VETO = "com.indiatv.livetv.androidtv";
    private static final String[] YOUTUBE = {"org.smarttube.stable", "com.amazon.firetv.youtube"};
    /** home.conf: YouTube channels as id|name, the row's order, and Veto channels taken off it. */
    private static final String YT = "live.yt", ORDER = "live.order", HIDE = "live.hide";
    private static final String DEFAULT_YT = "UCt4t-jeY85JegMlZ-E5UWtA|Aaj Tak,UCYPvAwZP8pZhSMW8qs7cVCw|India Today";
    static final String AVATARS = "yt";
    /** Your five news channels lead the row until you move something. */
    private static final String DEFAULT_ORDER = "https://ndtv-veto.akamaized.net/vglive-sk-834057/main.m3u8,"
            + "yt:UCt4t-jeY85JegMlZ-E5UWtA,"
            + "https://veto.akamaized.net/v1/vglive-sk-274906/main.m3u8,"
            + "https://ndtv-veto.akamaized.net/vglive-sk-722277/main.m3u8,"
            + "yt:UCYPvAwZP8pZhSMW8qs7cVCw,"
            + "https://veto.akamaized.net/v1/vglive-sk-479089/main.m3u8,"
            + "https://nn-veto.akamaized.net/national/master_nnna_veto.m3u8,"
            + "https://ndtvprofit-veto.akamaized.net/ndtvprofit/master_1.m3u8";

    static final class Channel {
        final String id, name, veto, youtube;
        final int art;

        Channel(String name, int art, String veto, String youtube) {
            this.id = veto != null ? veto : "yt:" + youtube;
            this.name = name;
            this.art = art;
            this.veto = veto;
            this.youtube = youtube;
        }
    }

    private static final Channel[] VETO_LIVE = VetoLive.ALL;

    private Channels() {}

    /** Every channel on the row, in the user's order, without the ones taken off it. */
    static List<Channel> all(Config cfg) {
        List<Channel> out = new ArrayList<>();
        List<String> hidden = cfg.list(HIDE);
        for (Channel c : VETO_LIVE) {
            if (!hidden.contains(c.id)) out.add(c);
        }
        for (String[] yt : youtube(cfg)) out.add(new Channel(yt[1], bundled(yt[0]), null, yt[0]));
        List<String> order = new ArrayList<>();
        for (String id : cfg.get(ORDER, DEFAULT_ORDER).split(",")) order.add(id.trim());
        Collections.sort(out, (a, b) -> Integer.compare(rank(order, a.id), rank(order, b.id)));
        return out;
    }

    private static int rank(List<String> order, String id) {
        int i = order.indexOf(id);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static List<String[]> youtube(Config cfg) {
        List<String[]> out = new ArrayList<>();
        for (String e : cfg.get(YT, DEFAULT_YT).split(",")) {
            String[] p = e.split("\\|", 2);
            if (p.length == 2 && !p[0].trim().isEmpty()) out.add(new String[] {p[0].trim(), p[1].trim()});
        }
        return out;
    }

    private static int bundled(String ytId) {
        if ("UCt4t-jeY85JegMlZ-E5UWtA".equals(ytId)) return R.drawable.ch_aaj_tak;
        if ("UCYPvAwZP8pZhSMW8qs7cVCw".equals(ytId)) return R.drawable.ch_india_today;
        return 0;
    }

    static String label(Config cfg, Channel c) {
        return cfg.get("name." + c.id, c.name);
    }

    static void rename(Config cfg, Channel c, String name) {
        cfg.set("name." + c.id, name == null || name.equals(c.name) ? null : name);
    }

    static void remove(Config cfg, Channel c) {
        if (c.youtube != null) {
            List<String> keep = new ArrayList<>();
            for (String[] yt : youtube(cfg)) {
                if (!yt[0].equals(c.youtube)) keep.add(yt[0] + "|" + yt[1]);
            }
            // shortcut: removing every YouTube channel brings the two defaults back; add one to avoid that.
            cfg.setList(YT, keep);
        } else {
            List<String> hide = cfg.list(HIDE);
            if (!hide.contains(c.id)) hide.add(c.id);
            cfg.setList(HIDE, hide);
        }
    }

    static void add(Config cfg, String id, String name) {
        List<String> all = new ArrayList<>();
        for (String[] yt : youtube(cfg)) {
            if (!yt[0].equals(id)) all.add(yt[0] + "|" + yt[1]);
        }
        // , and | separate entries in home.conf
        all.add(id + "|" + name.replace(",", " ").replace("|", " "));
        cfg.setList(YT, all);
    }

    static void saveOrder(Config cfg, List<String> ids) {
        cfg.setList(ORDER, ids);
    }

    /** The card for a channel you added: its round YouTube avatar on a dark 16:9 card. */
    static File avatar(Config cfg, String ytId) {
        return new File(new File(cfg.dir, AVATARS), ytId + ".png");
    }

    /** Downloads and stores {@link #avatar}. Slow: call off the UI thread. */
    static void fetchAvatar(Config cfg, String ytId, String url) {
        if (TextUtils.isEmpty(url)) return;
        try (InputStream in = new URL(url).openStream()) {
            Bitmap face = BitmapFactory.decodeStream(in);
            if (face == null) return;
            Bitmap card = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(card);
            c.drawColor(0xFF26272B);
            Path round = new Path();
            round.addCircle(160, 90, 66, Path.Direction.CW);
            c.clipPath(round);
            c.drawBitmap(face, null, new Rect(94, 24, 226, 156), new Paint(Paint.FILTER_BITMAP_FLAG));
            File f = avatar(cfg, ytId);
            if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(f)) {
                card.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (IOException ignored) {
            // the card falls back to the channel's name
        }
    }

    static void open(Activity a, Channel c, Executor io, Handler ui) {
        if (c.veto != null) {
            start(a, Uri.parse("https://vetoworld.com/content/live?url=" + c.veto), VETO);
            return;
        }
        Toast.makeText(a, "Finding " + c.name + " live…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            String id = null;
            try {
                id = Live.mostWatched(Live.get("https://www.youtube.com/channel/" + c.youtube + "/streams"));
            } catch (IOException ignored) {
                // offline or blocked: the channel page below still lists its live streams
            }
            Uri uri = Uri.parse(id != null
                    ? "https://www.youtube.com/watch?v=" + id
                    : "https://www.youtube.com/channel/" + c.youtube);
            ui.post(() -> start(a, uri, YOUTUBE));
        });
    }

    private static void start(Activity a, Uri uri, String... packages) {
        for (String pkg : packages) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignored) {
                // try the next app
            }
        }
        Toast.makeText(a, "The app for this channel is not installed", Toast.LENGTH_SHORT).show();
    }

    static List<String> ids(List<Channel> list) {
        List<String> out = new ArrayList<>();
        for (Channel c : list) out.add(c.id);
        return out;
    }
}

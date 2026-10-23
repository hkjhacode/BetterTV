package tv.dumbtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * The live news row. Veto channels open straight into Veto's player through its own
 * vetoworld.com/content/live link (stream addresses taken from Veto's log on this TV). YouTube
 * channels open their most-watched live stream in SmartTube, or YouTube when it is missing.
 */
final class Channels {
    static final String VETO = "com.indiatv.livetv.androidtv";
    private static final String[] YOUTUBE = {"org.smarttube.stable", "com.amazon.firetv.youtube"};

    static final class Channel {
        final String name, veto, youtube;
        final int art, color;

        Channel(String name, int art, int color, String veto, String youtube) {
            this.name = name;
            this.art = art;
            this.color = color;
            this.veto = veto;
            this.youtube = youtube;
        }
    }

    static final Channel[] ALL = {
        new Channel("NDTV India", R.drawable.ch_ndtv_india, 0xFFE0242B,
                "https://ndtv-veto.akamaized.net/vglive-sk-834057/main.m3u8", null),
        new Channel("India TV", R.drawable.ch_india_tv, 0xFF1C6FE0,
                "https://veto.akamaized.net/v1/vglive-sk-274906/main.m3u8", null),
        new Channel("India TV Speed News", R.drawable.ch_speed_news, 0xFF2456D6,
                "https://veto.akamaized.net/v1/vglive-sk-479089/main.m3u8", null),
        new Channel("Aaj Tak", R.drawable.ch_aaj_tak, 0xFFE3262B, null, "UCt4t-jeY85JegMlZ-E5UWtA"),
        new Channel("India Today", R.drawable.ch_india_today, 0xFFD3202A, null, "UCYPvAwZP8pZhSMW8qs7cVCw"),
    };

    private Channels() {}

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
}

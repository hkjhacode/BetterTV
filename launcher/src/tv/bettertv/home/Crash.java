package tv.bettertv.home;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps a crash report on the TV: crash.log next to home.conf, newest at the end, cut to its
 * last 64 KB. Options › Crash reports shows it; adb pull fetches it. Nothing is sent anywhere.
 */
final class Crash {
    static final String FILE = "crash.log";
    private static final long MAX = 64 * 1024;
    private static boolean installed;

    private Crash() {}

    /** One line in crash.log for a problem that did not crash the app, such as Bing refusing. */
    static synchronized void note(Config cfg, String what) {
        File log = new File(cfg.dir, FILE);
        if (log.length() > MAX && !log.delete()) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(log, true))) {
            w.println("--- " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + what);
        } catch (IOException ignored) {
            // the note is a nicety
        }
    }

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        File log = new File(Config.get(c).dir, FILE);
        String version = "?";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // unknown version is still worth logging
        }
        String app = version;
        Thread.UncaughtExceptionHandler next = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                if (log.length() > MAX && !log.delete()) return;
                try (PrintWriter w = new PrintWriter(new FileWriter(log, true))) {
                    w.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())
                            + "  BetterTV " + app + "  " + Build.MODEL + "  Android " + Build.VERSION.RELEASE
                            + "  thread " + thread.getName());
                    e.printStackTrace(w);
                }
            } catch (IOException | RuntimeException ignored) {
                // never let the reporter hide the real crash
            }
            if (next != null) next.uncaughtException(thread, e);
        });
    }
}

package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** More apps: every app that is not in the home row, TV apps first. Back returns home. */
public final class Apps extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private Grid grid;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Ui.fonts(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(cfg.background());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setPadding(dp(56), dp(40), dp(56), dp(40));
        scroll.addView(page);

        TextView title = Ui.text(this, 28, Ui.TEXT, Ui.outfitLight);
        title.setText("More apps");
        title.setPadding(0, 0, 0, dp(20));
        page.addView(title);
        TextView hint = Ui.text(this, 13, Ui.FAINT, Ui.outfitLight);
        hint.setText("Hold OK or press ☰ on an app to add it to the home row");
        hint.setPadding(0, 0, 0, dp(16));
        page.addView(hint);

        grid = new Grid(this);
        page.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        io.execute(() -> {
            List<Item> all = Catalog.apps(this);
            ui.post(() -> render(all));
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void render(List<Item> all) {
        List<String> dock = Catalog.dock(cfg);
        List<Item> rest = new ArrayList<>();
        for (Item it : all) {
            if (!dock.contains(it.id)) rest.add(it);
        }
        Collections.sort(rest, (a, b) -> Boolean.compare(!a.leanback, !b.leanback));
        grid.removeAllViews();
        for (Item it : rest) {
            Tile t = new Tile(this, it, Catalog.APPS, Catalog.label(cfg, it), dp(160), dp(90),
                    cfg.get(Config.LABELS), cfg.accent(), false);
            t.setOnClickListener(v -> open(it));
            t.setOnLongClickListener(v -> {
                add(it, all);
                return true;
            });
            t.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_MENU || e.getAction() != KeyEvent.ACTION_UP) return false;
                add(it, all);
                return true;
            });
            grid.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            io.execute(() -> {
                Catalog.art(getPackageManager(), it);
                ui.post(t::invalidate);
            });
        }
        View first = grid.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    private void add(Item it, List<Item> all) {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, it))
                .setItems(new String[] {"Add to home row"}, (d, w) -> {
                    Catalog.setShown(cfg, Catalog.APPS, it, true);
                    render(all);
                })
                .show();
    }

    private void open(Item it) {
        try {
            startActivity(new Intent(it.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

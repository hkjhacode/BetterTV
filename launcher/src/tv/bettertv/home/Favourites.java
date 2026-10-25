package tv.bettertv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import tv.bettertv.home.Lineup.Who;

/**
 * My Favourites (the guide's section 6): characters standing in two rows in black and white.
 * The selected one turns to colour, grows a little and steps forward (front row only; the back
 * row stays behind it), a soft light in their colour sits behind the group, the background fades
 * into a darkened black-and-white scene from their title, their extra appears (Red John's smiley
 * for Patrick Jane), and their own touch plays once. Left and Right move along a row, Up and Down
 * switch rows, OK opens the title.
 *
 * Everything comes from files/favourites/ on the TV, made on the computer: favourites.tsv (one
 * line per character, positions on a 1920x1080 canvas), cutouts/NAME.png, scenes/NAME.jpg and
 * extras/. One view, one onDraw; pictures are decoded once at the size they are drawn.
 */
final class Favourites extends View {
    private static final float CANVAS_W = 1920f, CANVAS_H = 1080f, GROW = 0.06f;
    private static final ColorMatrixColorFilter GRAY;
    private static final ColorMatrixColorFilter SCENE;

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0f);
        GRAY = new ColorMatrixColorFilter(m);
        ColorMatrix s = new ColorMatrix();
        s.setSaturation(0f);
        s.postConcat(new ColorMatrix(new float[] {
            0.42f, 0, 0, 0, 0,
            0, 0.42f, 0, 0, 0,
            0, 0, 0.42f, 0, 0,
            0, 0, 0, 1, 0,
        }));
        SCENE = new ColorMatrixColorFilter(s);
    }

    final List<Who> all = new ArrayList<>();
    private Who selected, previous;
    /** 0 to 1: how far the selected character has turned to colour and grown. */
    private float on;
    /** 0 to 1 while the selected character's touch plays. */
    private float touch;
    private float sceneFade = 0f;
    private ValueAnimator onAnim, touchAnim;
    private final Paint gray = new Paint(Paint.FILTER_BITMAP_FLAG), colour = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scenePaint = new Paint(Paint.FILTER_BITMAP_FLAG), glow = new Paint(), fade = new Paint();
    private final Paint extraPaint = new Paint(Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Random random = new Random();
    private long seed;
    private Consumer<Who> opener;
    private Runnable leaveUp, leaveDown;

    Favourites(Context c) {
        super(c);
        setFocusable(true);
        gray.setColorFilter(GRAY);
        scenePaint.setColorFilter(SCENE);
        extraPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        title.setColor(Ui.TEXT);
        title.setTypeface(Ui.outfitMedium);
        label.setColor(Ui.FAINT);
        label.setTypeface(Ui.outfitMedium);
        label.setLetterSpacing(0.16f);
        word.setTypeface(Ui.outfitLight);
        word.setColor(Ui.TEXT);
    }

    void onOpen(Consumer<Who> opener) {
        this.opener = opener;
    }

    /** Where Up from the back row and Down from the front row go. */
    void onLeave(Runnable up, Runnable down) {
        leaveUp = up;
        leaveDown = down;
    }

    /** Loads the lineup from {@code dir} (files/favourites). Slow: call off the UI thread. */
    static List<Who> load(File dir, int screenW, int screenH) {
        try {
            String text = new String(Files.readAllBytes(new File(dir, "favourites.tsv").toPath()), StandardCharsets.UTF_8);
            List<Who> list = Lineup.parse(text);
            float k = screenH / CANVAS_H;
            for (Who w : list) {
                w.cut = decode(new File(dir, "cutouts/" + w.name + ".png"), Math.round(w.height * k), Bitmap.Config.ARGB_8888);
                // scenes sit darkened under the lineup: half size and 16-bit colour are plenty
                w.scene = decode(new File(dir, "scenes/" + w.name + ".jpg"), screenH / 2, Bitmap.Config.RGB_565);
                if (!"-".equals(w.extra) && !w.extra.isEmpty()) {
                    w.extraArt = decode(new File(dir, "extras/" + w.extra), Math.round(w.extraW * k * 2), Bitmap.Config.ARGB_8888);
                }
            }
            List<Who> shown = new ArrayList<>();
            for (Who w : list) if (w.cut != null) shown.add(w);
            return shown;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** The picture scaled down (powers of two) to about {@code height} pixels tall, or null. */
    private static Bitmap decode(File f, int height, Bitmap.Config config) {
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outHeight / (o.inSampleSize * 2) >= height) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = config;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    void show(List<Who> list) {
        all.clear();
        all.addAll(list);
        selected = null;
        invalidate();
    }

    boolean isEmpty() {
        return all.isEmpty();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previousRect) {
        super.onFocusChanged(focused, direction, previousRect);
        if (all.isEmpty()) return;
        if (focused) {
            // coming down from above lands on the back row, as the guide's lineup is read top first
            Who first = previous != null ? previous : first(direction == View.FOCUS_UP ? "front" : "back");
            select(first);
        } else {
            previous = selected;
            select(null);
        }
    }

    private Who first(String row) {
        Who best = null;
        for (Who w : all) {
            if (w.row.equals(row) && (best == null || w.x < best.x)) best = w;
        }
        return best != null ? best : all.get(0);
    }

    private void select(Who w) {
        if (w == selected) return;
        selected = w;
        seed = random.nextLong();
        if (onAnim != null) onAnim.cancel();
        on = 0f;
        onAnim = ValueAnimator.ofFloat(0f, 1f);
        onAnim.setDuration(260);
        onAnim.setInterpolator(new DecelerateInterpolator(2f));
        onAnim.addUpdateListener(a -> {
            on = (Float) a.getAnimatedValue();
            sceneFade = w == null ? 1f - on : Math.max(sceneFade, on);
            invalidate();
        });
        onAnim.start();
        if (touchAnim != null) touchAnim.cancel();
        touch = 0f;
        if (w == null) return;
        // one touch per selection, never looping
        touchAnim = ValueAnimator.ofFloat(0f, 1f);
        touchAnim.setDuration(w.touch.startsWith("type") ? 1400 : w.touch.equals("jump") ? 520 : 1000);
        touchAnim.setStartDelay(180);
        touchAnim.addUpdateListener(a -> {
            touch = (Float) a.getAnimatedValue();
            invalidate();
        });
        touchAnim.start();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (selected == null) return super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                Who next = beside(selected, code == KeyEvent.KEYCODE_DPAD_RIGHT);
                if (next != null) select(next);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                boolean up = code == KeyEvent.KEYCODE_DPAD_UP;
                String to = up ? "back" : "front";
                if (selected.row.equals(to)) {
                    Runnable leave = up ? leaveUp : leaveDown;
                    if (leave != null) leave.run();
                    return true;
                }
                Who near = nearest(to, selected.x);
                if (near != null) select(near);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (opener != null) opener.accept(selected);
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    private Who beside(Who from, boolean right) {
        return Lineup.beside(all, from, right);
    }

    private Who nearest(String row, float x) {
        Who best = null;
        for (Who w : all) {
            if (w.row.equals(row) && (best == null || Math.abs(w.x - x) < Math.abs(best.x - x))) best = w;
        }
        return best;
    }

    @Override
    protected void onDraw(Canvas c) {
        float k = getHeight() / CANVAS_H;
        float left = (getWidth() - CANVAS_W * k) / 2f;
        Who s = selected != null ? selected : previous;
        // the title's scene, darkened and black and white, fading in over the wallpaper
        if (s != null && s.scene != null && sceneFade > 0f) {
            scenePaint.setAlpha(Math.round(255 * sceneFade));
            box.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(s.scene, null, box, scenePaint);
        }
        // a soft light in the selected character's colour behind the group
        if (selected != null && on > 0f) {
            float cx = left + selected.x * CANVAS_W * k, cy = (selected.top + selected.height * 0.35f) * k;
            float r = 520 * k;
            int a = Math.round(0x8C * on);
            glow.setShader(new RadialGradient(cx, cy, r, (selected.colour & 0xFFFFFF) | (a << 24),
                    selected.colour & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, glow);
        }
        // Red John and the like: only with their owner, at their place, with Darken
        if (selected != null && selected.extraArt != null && on > 0f) {
            extraPaint.setAlpha(Math.round(255 * on));
            float w = selected.extraW * k, h = w * selected.extraArt.getHeight() / selected.extraArt.getWidth();
            box.set(left + selected.extraX * k, selected.extraY * k, left + selected.extraX * k + w, selected.extraY * k + h);
            c.drawBitmap(selected.extraArt, null, box, extraPaint);
        }
        for (Who w : Lineup.order(all, selected)) drawWho(c, w, k, left);
        // the legs run into black, so the lineup blends into the page
        float from = getHeight() * 0.74f;
        fade.setShader(new LinearGradient(0, from, 0, getHeight(), 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, from, getWidth(), getHeight(), fade);
        // labels: the section, and the selected title with its app's logo
        label.setTextSize(11 * getResources().getDisplayMetrics().density);
        c.drawText("MY FAVOURITES", Ui.dp(getContext(), 56), Ui.dp(getContext(), 42), label);
        if (selected != null) {
            title.setTextSize(19 * getResources().getDisplayMetrics().density);
            title.setAlpha(Math.round(255 * on));
            float tw = title.measureText(selected.title);
            Drawable logo = appLogo(selected.pkg);
            float lh = title.getTextSize() * 0.95f, lw = logo == null ? 0 : lh * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
            float gap = logo == null ? 0 : Ui.dp(getContext(), 12);
            float x = (getWidth() - tw - gap - lw) / 2f, y = Ui.dp(getContext(), 92);
            c.drawText(selected.title, x, y, title);
            if (logo != null) {
                int lx = Math.round(x + tw + gap), ly = Math.round(y - lh * 0.86f);
                logo.setBounds(lx, ly, lx + Math.round(lw), ly + Math.round(lh));
                logo.setAlpha(Math.round(255 * on));
                logo.draw(c);
            }
        }
    }

    private Drawable appLogo(String pkg) {
        int glyph = Catalog.glyph(pkg);
        return glyph == 0 ? null : getContext().getDrawable(glyph);
    }

    private void drawWho(Canvas c, Who w, float k, float left) {
        boolean sel = w == selected;
        float grow = sel ? 1f + GROW * on : 1f;
        float h = w.height * k * grow, wd = h * w.cut.getWidth() / w.cut.getHeight();
        float cx = left + w.x * CANVAS_W * k, feet = (w.top + w.height) * k;
        float lift = 0f, shake = 0f;
        if (sel && touch > 0f && touch < 1f) {
            if ("jump".equals(w.touch)) lift = (float) Math.sin(Math.PI * touch) * 46 * k;
            if ("spider".equals(w.touch) && touch < 0.3f) lift = -(float) Math.sin(Math.PI * touch / 0.3f) * 10 * k;
            if ("shudder".equals(w.touch)) shake = (float) Math.sin(touch * Math.PI * 14) * (1f - touch) * 14 * k;
        }
        box.set(cx - wd / 2 + shake, feet - h - lift, cx + wd / 2 + shake, feet - lift);
        if (!sel) {
            c.drawBitmap(w.cut, null, box, gray);
            return;
        }
        // black and white underneath, colour fading in on top
        c.drawBitmap(w.cut, null, box, gray);
        colour.setAlpha(Math.round(255 * on));
        c.drawBitmap(w.cut, null, box, colour);
        if (touch > 0f && touch < 1f) drawTouch(c, w, k);
    }

    /** The character's own moment, once per selection. */
    private void drawTouch(Canvas c, Who w, float k) {
        float t = touch, fadeOut = 1f - t;
        float cx = box.centerX(), headY = box.top + box.height() * 0.12f;
        switch (w.touch) {
            case "spider": {
                // spider-sense: short lines around the head, a little different every time
                Random r = new Random(seed);
                line.setColor(0xFFFFFFFF);
                line.setAlpha(Math.round(230 * fadeOut));
                line.setStrokeWidth(4 * k);
                int n = 7 + r.nextInt(4);
                float base = box.width() * 0.32f;
                for (int i = 0; i < n; i++) {
                    double a = -Math.PI * (0.1 + 0.8 * (i + r.nextFloat() * 0.6) / n);
                    float r0 = base * (1f + 0.15f * t), r1 = r0 + (26 + r.nextInt(22)) * k;
                    c.drawLine(cx + (float) Math.cos(a) * r0, headY + (float) Math.sin(a) * r0,
                            cx + (float) Math.cos(a) * r1, headY + (float) Math.sin(a) * r1, line);
                }
                break;
            }
            case "arc": {
                // one flash of the arc reactor on the chest
                float y = box.top + box.height() * 0.4f, pulse = (float) Math.sin(Math.PI * t);
                glow.setShader(new RadialGradient(cx, y, 90 * k, 0xCC9FE8FF, 0x009FE8FF, Shader.TileMode.CLAMP));
                glow.setAlpha(Math.round(255 * pulse));
                c.drawCircle(cx, y, 90 * k, glow);
                glow.setAlpha(255);
                break;
            }
            case "glint": {
                // a light sweep across the figure, once
                int save = c.saveLayer(box, null);
                c.drawBitmap(w.cut, null, box, colour);
                Paint p = new Paint();
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                float x = box.left - box.width() + 2.2f * box.width() * t;
                p.setShader(new LinearGradient(x, box.top, x + box.width() * 0.5f, box.bottom,
                        new int[] {0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRect(box, p);
                c.restoreToCount(save);
                break;
            }
            case "portal": {
                // a ring of golden sparks opening beside him
                float rx = box.right - box.width() * 0.08f, ry = box.top + box.height() * 0.42f;
                float rad = (40 + 70 * t) * k;
                Random r = new Random(seed);
                line.setColor(0xFFFFB347);
                line.setStrokeWidth(5 * k);
                line.setAlpha(Math.round(255 * (float) Math.sin(Math.PI * t)));
                for (int i = 0; i < 24; i++) {
                    double a = 2 * Math.PI * i / 24 + t * 3;
                    float r0 = rad * (0.92f + 0.12f * r.nextFloat());
                    float x0 = rx + (float) Math.cos(a) * r0, y0 = ry + (float) Math.sin(a) * r0;
                    c.drawPoint(x0, y0, line);
                }
                break;
            }
            default:
                if (w.touch.startsWith("type:")) {
                    // a word typed in beside the character
                    String text = w.touch.substring(5);
                    int shown = Math.min(text.length(), Math.round(text.length() * Math.min(1f, t * 1.6f)));
                    word.setTextSize(30 * k * 2);
                    word.setAlpha(t > 0.85f ? Math.round(255 * (1f - t) / 0.15f) : 255);
                    c.drawText(text, 0, shown, box.left - word.measureText(text) * 0.15f, box.top + box.height() * 0.08f, word);
                }
        }
    }
}

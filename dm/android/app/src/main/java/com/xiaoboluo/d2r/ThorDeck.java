package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

// [Thor] The Diablo deck on the AYN Thor's bottom screen (1240x1080): life bar + resource globe read from the game's HUD
// (D3SkillIcons.bars), the six skill buttons with the game's own skill icons (D3SkillIcons, copied from the HUD), a large potion,
// and the console menus as big buttons. Everything is drawn here (no assets); input goes into the pad channel like D3PadView's
// buttons (DmActivity.nativePadKey, PAD_DEVICE), so it works next to a physical controller.
// Controller layout of the Switch version (game Options -> Controller, measured on the Thor): Y/B/A/X/ZL/ZR = skills 1..6,
// R = potion, L = lock target, + = character menu (tabs Inventory / Skills / Paragon / Party / Quests, L/R switch, wraps, always
// opens on Inventory), - = game menu, d-pad down = map, right = town portal.
final class ThorDeck extends View {
    static final float W = 1240, H = 1080;   // design units = the Thor's bottom screen
    static final int ACCENT_DEFAULT = 0xffb3261e, GOLD = 0xffc9a46a, PARCH = 0xffe9d8b4, BRONZE = 0xff5e3b24;
    static final long SEQ_MS = 650;   // the character menu needs this long before L / R switch its tab

    static final int ICON_NONE = 0, ICON_BAG = 1, ICON_SKILLS = 2, ICON_QUEST = 3, ICON_MAP = 4, ICON_PORTAL = 5, ICON_TARGET = 6, ICON_GEAR = 7, ICON_PAUSE = 8;
    static final class Ctl {
        final String label, hint; final int[] keys; final boolean round, hold; final int icon; final float x, y, w, h;
        int pointer = -1;
        Ctl(String label, String hint, int[] keys, boolean round, boolean hold, int icon, float x, float y, float w, float h) {
            this.label = label; this.hint = hint; this.keys = keys; this.round = round; this.hold = hold; this.icon = icon; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    static final int SETTINGS = -100;

    private final DmActivity act;
    private final Ctl[] ctls;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
        bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path(); private final RectF rect = new RectF();
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    private boolean running;
    private final Runnable tick = new Runnable() { public void run() { if (!running) return; invalidate(); ui.postDelayed(this, 250); } };

    ThorDeck(Context c, DmActivity act) {
        super(c);
        this.act = act;
        boolean de = "de".equals(Locale.getDefault().getLanguage());
        line.setStyle(Paint.Style.STROKE); text.setTextAlign(Paint.Align.CENTER);
        float sr = 92, sy0 = 372, sy1 = 580;   // skills: two rows of three, in the order of the game's slots 1..6
        float bw = 280, bh = 160, by0 = 708, by1 = 892;
        ctls = new Ctl[] {
            new Ctl("1", "Y", k(D3PadView.K_Y), true, true, ICON_NONE, 175, sy0, sr, sr),
            new Ctl("2", "B", k(D3PadView.K_B), true, true, ICON_NONE, 395, sy0, sr, sr),
            new Ctl("3", "A", k(D3PadView.K_A), true, true, ICON_NONE, 615, sy0, sr, sr),
            new Ctl("4", "X", k(D3PadView.K_X), true, true, ICON_NONE, 175, sy1, sr, sr),
            new Ctl("5", "ZL", k(D3PadView.K_ZL), true, true, ICON_NONE, 395, sy1, sr, sr),
            new Ctl("6", "ZR", k(D3PadView.K_ZR), true, true, ICON_NONE, 615, sy1, sr, sr),
            new Ctl(de ? "HEILTRANK" : "POTION", "R", k(D3PadView.K_R), true, true, ICON_NONE, 945, 470, 150, 150),
            new Ctl(de ? "Inventar" : "Inventory", "+", k(D3PadView.K_PLUS), false, false, ICON_BAG, 30, by0, bw, bh),
            new Ctl(de ? "Fertigkeiten" : "Skills", "+ R", k(D3PadView.K_PLUS, D3PadView.K_R), false, false, ICON_SKILLS, 330, by0, bw, bh),
            new Ctl("Quests", "+ L", k(D3PadView.K_PLUS, D3PadView.K_L), false, false, ICON_QUEST, 630, by0, bw, bh),
            new Ctl(de ? "Karte" : "Map", "▼", k(D3PadView.K_DOWN), false, false, ICON_MAP, 930, by0, bw, bh),
            new Ctl(de ? "Stadtportal" : "Town Portal", "▶", k(D3PadView.K_RIGHT), false, false, ICON_PORTAL, 30, by1, bw, bh),
            new Ctl(de ? "Ziel fixieren" : "Lock Target", "L", k(D3PadView.K_L), false, true, ICON_TARGET, 330, by1, bw, bh),
            new Ctl(de ? "Einstellungen" : "Settings", "⚙", k(SETTINGS), false, false, ICON_GEAR, 630, by1, bw, bh),
            new Ctl(de ? "Spielmenü" : "Game Menu", "−", k(D3PadView.K_MINUS), false, false, ICON_PAUSE, 930, by1, bw, bh),
        };
    }
    private static int[] k(int... keys) { return keys; }

    void start() { if (running) return; running = true; ui.post(tick); }
    void stop() { running = false; ui.removeCallbacks(tick); for (Ctl c : ctls) release(c); }

    // ---------------- input ----------------
    private static void key(int k, boolean down) { DmActivity.nativePadKey(D3PadView.PAD_DEVICE, k, down); }
    private void pulse(int k) { key(k, true); ui.postDelayed(() -> key(k, false), D3PadView.PULSE_MS); }
    private void press(Ctl c) {
        if (c.keys[0] == SETTINGS) { act.runOnUiThread(act::showD3Settings); return; }
        if (c.hold) { key(c.keys[0], true); return; }
        for (int i = 0; i < c.keys.length; i++) { final int kk = c.keys[i]; ui.postDelayed(() -> pulse(kk), i * SEQ_MS); }
    }
    private void release(Ctl c) { if (c.pointer >= 0 && c.hold) key(c.keys[0], false); c.pointer = -1; }

    private float s() { return Math.min(getWidth() / W, getHeight() / H); }
    private Ctl hit(float px, float py) {
        float s = s(), x = px / s, y = py / s;
        for (Ctl c : ctls) {
            if (c.round) { float dx = x - c.x, dy = y - c.y, r = c.w * 1.12f; if (dx * dx + dy * dy <= r * r) return c; }
            else if (x >= c.x - 8 && x <= c.x + c.w + 8 && y >= c.y - 8 && y <= c.y + c.h + 8) return c;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked(), i = e.getActionIndex(), id = e.getPointerId(i);
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            Ctl c = hit(e.getX(i), e.getY(i));
            if (c != null && c.pointer < 0) { c.pointer = id; press(c); performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); invalidate(); }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
            for (Ctl c : ctls) if (c.pointer == id) release(c);
            invalidate();
        } else if (a == MotionEvent.ACTION_CANCEL) {
            for (Ctl c : ctls) release(c);
            invalidate();
        }
        return true;
    }

    // ---------------- drawing ----------------
    private D3SkillIcons icons() { D3PadView p = act.d3Pad(); return p == null ? null : p.icons(); }
    private static int alpha(int c, float a) { return (Math.round(a * 255) << 24) | (c & 0xffffff); }
    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xff) * (1 - t) + ((b >> 16) & 0xff) * t), g = Math.round(((a >> 8) & 0xff) * (1 - t) + ((b >> 8) & 0xff) * t),
            bl = Math.round((a & 0xff) * (1 - t) + (b & 0xff) * t);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    @Override protected void onDraw(Canvas c) {
        float s = s();
        c.save(); c.translate((getWidth() - W * s) / 2, (getHeight() - H * s) / 2); c.scale(s, s);
        D3SkillIcons ic = icons();
        int res = ic != null && ic.resourceColor() != 0 ? ic.resourceColor() : 0;
        int accent = res != 0 ? mix(res, 0xffffffff, 0.15f) : ACCENT_DEFAULT;
        background(c, accent);
        header(c);
        status(c, ic, res);
        for (Ctl k : ctls) { if (k.round) skill(c, k, ic, accent); else button(c, k, accent); }
        c.restore();
    }

    private void background(Canvas c, int accent) {
        fill.setShader(new LinearGradient(0, 0, 0, H, 0xff1b0e0b, 0xff060303, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, fill);
        fill.setShader(new RadialGradient(W / 2, H + 120, 760, alpha(mix(accent, 0xff000000, 0.35f), 0.38f), 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, fill);
        fill.setShader(new RadialGradient(395, 476, 420, 0x2a8a5a30, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 250, W, 690, fill);
        fill.setShader(null);
        // the skill ring: an ornamental circle with rune ticks behind the six skills (D3's own skill wheel in spirit)
        line.setStrokeWidth(3); line.setColor(0x553a2418); c.drawCircle(395, 476, 330, line);
        line.setStrokeWidth(1.5f); line.setColor(0x40c9a46a); c.drawCircle(395, 476, 300, line); c.drawCircle(395, 476, 350, line);
        for (int i = 0; i < 48; i++) {
            double t = Math.toRadians(i * 7.5); float r0 = i % 4 == 0 ? 300 : 316, r1 = i % 4 == 0 ? 350 : 334;
            c.drawLine(395 + r0 * (float) Math.cos(t), 476 + r0 * (float) Math.sin(t), 395 + r1 * (float) Math.cos(t), 476 + r1 * (float) Math.sin(t), line);
        }
        // frame: bronze double border with corner studs
        line.setStrokeWidth(4); line.setColor(BRONZE); c.drawRect(10, 10, W - 10, H - 10, line);
        line.setStrokeWidth(1.5f); line.setColor(0x80c9a46a); c.drawRect(20, 20, W - 20, H - 20, line);
        fill.setColor(GOLD); for (float[] p : new float[][] {{20, 20}, {W - 20, 20}, {20, H - 20}, {W - 20, H - 20}}) c.drawCircle(p[0], p[1], 7, fill);
        line.setStrokeWidth(2); line.setColor(0x705e3b24); c.drawLine(40, 676, W - 40, 676, line);
    }

    private void header(Canvas c) {
        text.setTypeface(serif); text.setLetterSpacing(0.3f); text.setTextSize(44); text.setColor(GOLD);
        text.setShadowLayer(12, 0, 0, 0xc0000000); c.drawText("THOR EDITION", W / 2, 74, text);
        float half = text.measureText("THOR EDITION") / 2 + 30;
        text.setLetterSpacing(0.12f); text.setTextSize(20); text.setColor(0xffd04030); c.drawText("for Diablo III", W / 2, 104, text);
        text.clearShadowLayer(); text.setLetterSpacing(0);
        line.setStrokeWidth(2); line.setColor(0x90c9a46a); c.drawLine(110, 60, W / 2 - half - 24, 60, line); c.drawLine(W / 2 + half + 24, 60, W - 110, 60, line);
        fill.setColor(GOLD);
        for (float dx : new float[] {-half - 12, half + 12}) { path.reset(); path.moveTo(W / 2 + dx - 12, 60); path.lineTo(W / 2 + dx, 52); path.lineTo(W / 2 + dx + 12, 60); path.lineTo(W / 2 + dx, 68); path.close(); c.drawPath(path, fill); }
    }

    // Life bar (red, like the HUD) and the resource globe (in the class's resource colour); "—" until the HUD was seen.
    private void status(Canvas c, D3SkillIcons ic, int res) {
        float life = ic == null ? -1 : ic.life(), rsc = ic == null ? -1 : ic.resource();
        float ox = 120, oy = 196, r = 66;
        fill.setColor(0xff0a0606); c.drawCircle(ox, oy, r, fill);
        if (rsc >= 0) {
            int rc = res != 0 ? res : 0xff2a3cc0;
            c.save(); path.reset(); path.addCircle(ox, oy, r - 4, Path.Direction.CW); c.clipPath(path);
            float top = oy + r - 2 * r * Math.min(1, rsc);
            fill.setShader(new LinearGradient(0, top, 0, oy + r, mix(rc, 0xffffffff, 0.25f), mix(rc, 0xff000000, 0.45f), Shader.TileMode.CLAMP));
            c.drawRect(ox - r, top, ox + r, oy + r, fill); fill.setShader(null); c.restore();
        }
        fill.setShader(new RadialGradient(ox - 22, oy - 26, 36, 0x50ffffff, 0, Shader.TileMode.CLAMP)); c.drawCircle(ox, oy, r, fill); fill.setShader(null);
        line.setStrokeWidth(6); line.setColor(BRONZE); c.drawCircle(ox, oy, r + 3, line);
        line.setStrokeWidth(2); line.setColor(GOLD); c.drawCircle(ox, oy, r + 7, line);
        text.setTypeface(serif); text.setTextSize(28); text.setColor(PARCH);
        c.drawText(rsc < 0 ? "—" : Math.round(rsc * 100) + "%", ox, oy + 10, text);

        float x0 = 220, x1 = W - 60, y0 = 176, y1 = 222;
        rect.set(x0, y0, x1, y1); fill.setColor(0xff0c0707); c.drawRoundRect(rect, 10, 10, fill);
        if (life >= 0) {
            rect.set(x0 + 4, y0 + 4, x0 + 4 + (x1 - x0 - 8) * Math.min(1, life), y1 - 4);
            fill.setShader(new LinearGradient(0, y0, 0, y1, 0xffe0443a, 0xff7a120c, Shader.TileMode.CLAMP)); c.drawRoundRect(rect, 7, 7, fill); fill.setShader(null);
        }
        rect.set(x0, y0, x1, y1); line.setStrokeWidth(4); line.setColor(BRONZE); c.drawRoundRect(rect, 10, 10, line);
        line.setStrokeWidth(1.5f); line.setColor(0x90c9a46a); for (int i = 1; i < 10; i++) { float x = x0 + (x1 - x0) * i / 10f; c.drawLine(x, y0 - 6, x, y0, line); }
        boolean de = "de".equals(Locale.getDefault().getLanguage());
        text.setTextAlign(Paint.Align.LEFT); text.setTextSize(24); text.setColor(GOLD); text.setLetterSpacing(0.15f);
        c.drawText(de ? "LEBEN" : "LIFE", x0, 160, text);
        text.setTextAlign(Paint.Align.RIGHT); text.setColor(PARCH); c.drawText(life < 0 ? "—" : Math.round(life * 100) + " %", x1, 160, text);
        text.setTextAlign(Paint.Align.CENTER); text.setLetterSpacing(0);
    }

    private void skill(Canvas c, Ctl k, D3SkillIcons ic, int accent) {
        boolean on = k.pointer >= 0, potion = k.keys[0] == D3PadView.K_R;
        float x = k.x, y = k.y, r = k.w;
        fill.setShader(new RadialGradient(x, y, r * 1.35f, alpha(potion ? 0xffb02020 : accent, on ? 0.55f : 0.22f), 0, Shader.TileMode.CLAMP)); c.drawCircle(x, y, r * 1.35f, fill); fill.setShader(null);
        Bitmap b = ic == null ? null : ic.icon(k.keys[0]);
        fill.setShader(new LinearGradient(0, y - r, 0, y + r, 0xff2c2220, 0xff0e0909, Shader.TileMode.CLAMP)); c.drawCircle(x, y, r, fill); fill.setShader(null);
        if (b != null) {
            c.save(); path.reset(); path.addCircle(x, y, r - 8, Path.Direction.CW); c.clipPath(path);
            rect.set(x - r + 8, y - r + 8, x + r - 8, y + r - 8); bmp.setAlpha(on ? 255 : 235); c.drawBitmap(b, null, rect, bmp);
            if (on) { fill.setColor(0x40ffffff); c.drawCircle(x, y, r, fill); }
            c.restore();
        } else if (potion) {
            flask(c, x, y - 12, r * 0.55f);
        } else {
            text.setTypeface(serif); text.setTextSize(r * 0.7f); text.setColor(alpha(PARCH, 0.55f)); c.drawText(k.hint, x, y + r * 0.25f, text);
        }
        line.setStrokeWidth(8); line.setColor(on ? GOLD : BRONZE); c.drawCircle(x, y, r, line);
        line.setStrokeWidth(2); line.setColor(alpha(GOLD, on ? 1f : 0.6f)); c.drawCircle(x, y, r + 7, line);
        if (potion) {   // its name under it, the controller button as a badge
            text.setTypeface(serif); text.setTextSize(30); text.setLetterSpacing(0.12f); text.setColor(PARCH); c.drawText(k.label, x, y + r + 58, text); text.setLetterSpacing(0);
        }
        badge(c, x + r * 0.72f, y + r * 0.72f, potion ? k.hint : k.hint);
        if (!potion) { text.setTypeface(serif); text.setTextSize(24); text.setColor(alpha(GOLD, 0.8f)); c.drawText(k.label, x - r * 0.78f, y - r * 0.70f, text); }
    }
    private void badge(Canvas c, float x, float y, String t) {
        float r = t.length() > 1 ? 26 : 22;
        fill.setColor(0xff120b09); c.drawCircle(x, y, r, fill); line.setStrokeWidth(3); line.setColor(GOLD); c.drawCircle(x, y, r, line);
        text.setTypeface(Typeface.DEFAULT_BOLD); text.setTextSize(t.length() > 1 ? 20 : 24); text.setColor(PARCH); c.drawText(t, x, y + 8, text);
    }
    private void flask(Canvas c, float x, float y, float r) {
        fill.setShader(new LinearGradient(0, y - r, 0, y + r, 0xffe04a3a, 0xff6a0c08, Shader.TileMode.CLAMP)); c.drawCircle(x, y + r * 0.25f, r, fill); fill.setShader(null);
        fill.setColor(0xff8a6a50); c.drawRect(x - r * 0.28f, y - r * 1.25f, x + r * 0.28f, y - r * 0.55f, fill);
        fill.setColor(0xff5a3a26); c.drawRect(x - r * 0.36f, y - r * 1.4f, x + r * 0.36f, y - r * 1.2f, fill);
        fill.setColor(0x70ffffff); c.drawCircle(x - r * 0.35f, y - r * 0.05f, r * 0.18f, fill);
    }

    private void button(Canvas c, Ctl k, int accent) {
        boolean on = k.pointer >= 0;
        rect.set(k.x, k.y, k.x + k.w, k.y + k.h);
        fill.setShader(new LinearGradient(0, k.y, 0, k.y + k.h, on ? 0xff4a2a20 : 0xff2a201d, on ? 0xff200c08 : 0xff100b0a, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, 14, 14, fill); fill.setShader(null);
        line.setStrokeWidth(4); line.setColor(on ? GOLD : BRONZE); c.drawRoundRect(rect, 14, 14, line);
        rect.inset(7, 7); line.setStrokeWidth(1.5f); line.setColor(alpha(GOLD, on ? 0.9f : 0.35f)); c.drawRoundRect(rect, 9, 9, line);
        float cx = k.x + k.w / 2, iy = k.y + 62;
        glyph(c, k.icon, cx, iy, 34, on ? GOLD : mix(GOLD, accent, 0.25f));
        text.setTypeface(serif); text.setTextSize(30); text.setColor(PARCH); c.drawText(k.label, cx, k.y + k.h - 30, text);
        text.setTypeface(Typeface.DEFAULT_BOLD); text.setTextSize(18); text.setColor(alpha(GOLD, 0.65f)); text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(k.hint, k.x + k.w - 16, k.y + 28, text); text.setTextAlign(Paint.Align.CENTER);
    }

    // Small line icons, drawn (no image assets).
    private void glyph(Canvas c, int kind, float x, float y, float r, int col) {
        line.setColor(col); line.setStrokeWidth(4); line.setStrokeCap(Paint.Cap.ROUND); fill.setColor(col);
        switch (kind) {
            case ICON_BAG:
                rect.set(x - r, y - r * 0.45f, x + r, y + r * 0.8f); c.drawRoundRect(rect, 8, 8, line);
                c.drawLine(x - r, y + r * 0.1f, x + r, y + r * 0.1f, line); rect.set(x - r * 0.45f, y - r * 0.95f, x + r * 0.45f, y - r * 0.15f); c.drawArc(rect, 180, 180, false, line);
                c.drawCircle(x, y + r * 0.1f, 5, fill); break;
            case ICON_SKILLS:
                for (int i = 0; i < 4; i++) { double t = Math.toRadians(90 * i - 90); c.drawCircle(x + r * 0.62f * (float) Math.cos(t), y + r * 0.62f * (float) Math.sin(t), r * 0.28f, line); }
                break;
            case ICON_QUEST:
                rect.set(x - r * 0.8f, y - r, x + r * 0.8f, y + r); c.drawRoundRect(rect, 6, 6, line);
                c.drawLine(x, y - r * 0.55f, x, y + r * 0.2f, line); c.drawCircle(x, y + r * 0.58f, 4, fill); break;
            case ICON_MAP:
                path.reset(); path.moveTo(x - r, y - r * 0.7f); path.lineTo(x - r * 0.33f, y - r); path.lineTo(x + r * 0.33f, y - r * 0.7f); path.lineTo(x + r, y - r);
                path.lineTo(x + r, y + r * 0.7f); path.lineTo(x + r * 0.33f, y + r); path.lineTo(x - r * 0.33f, y + r * 0.7f); path.lineTo(x - r, y + r); path.close(); c.drawPath(path, line);
                c.drawLine(x - r * 0.33f, y - r, x - r * 0.33f, y + r * 0.7f, line); c.drawLine(x + r * 0.33f, y - r * 0.7f, x + r * 0.33f, y + r, line); break;
            case ICON_PORTAL:
                fill.setShader(new RadialGradient(x, y, r, 0xff9fd0ff, 0x002050ff, Shader.TileMode.CLAMP)); rect.set(x - r * 0.62f, y - r, x + r * 0.62f, y + r); c.drawOval(rect, fill); fill.setShader(null);
                line.setColor(0xff7ab8ff); c.drawOval(rect, line); break;
            case ICON_TARGET:
                c.drawCircle(x, y, r * 0.75f, line); c.drawCircle(x, y, 5, fill);
                c.drawLine(x, y - r, x, y - r * 0.45f, line); c.drawLine(x, y + r * 0.45f, x, y + r, line); c.drawLine(x - r, y, x - r * 0.45f, y, line); c.drawLine(x + r * 0.45f, y, x + r, y, line); break;
            case ICON_GEAR:
                c.drawCircle(x, y, r * 0.55f, line); c.drawCircle(x, y, r * 0.2f, line);
                for (int i = 0; i < 8; i++) { double t = Math.toRadians(45 * i); c.drawLine(x + r * 0.62f * (float) Math.cos(t), y + r * 0.62f * (float) Math.sin(t), x + r * 0.92f * (float) Math.cos(t), y + r * 0.92f * (float) Math.sin(t), line); }
                break;
            case ICON_PAUSE:
                rect.set(x - r * 0.6f, y - r * 0.8f, x - r * 0.15f, y + r * 0.8f); c.drawRoundRect(rect, 4, 4, fill);
                rect.set(x + r * 0.15f, y - r * 0.8f, x + r * 0.6f, y + r * 0.8f); c.drawRoundRect(rect, 4, 4, fill); break;
            default: break;
        }
        line.setStrokeCap(Paint.Cap.BUTT);
    }
}

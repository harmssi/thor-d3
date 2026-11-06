package com.xiaoboluo.d2r;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

// [Thor] The bottom screen of the AYN Thor (or any second display): ThorDeck as a Presentation on the first display that is not the
// game's. FLAG_NOT_FOCUSABLE keeps the controller with the game. Same pattern as the other Thor ports (ra2-thor ThorScreens).
final class ThorScreens implements DisplayManager.DisplayListener {
    private final DmActivity act;
    private final DisplayManager displays;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Presentation presentation;
    private ThorDeck deck;

    ThorScreens(DmActivity act) { this.act = act; displays = (DisplayManager) act.getSystemService(Context.DISPLAY_SERVICE); }

    private Display secondary() {
        int own = Display.DEFAULT_DISPLAY;
        try { own = act.getDisplay().getDisplayId(); } catch (Throwable ignored) { }
        for (Display d : displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) if (d.getDisplayId() != own) return d;
        for (Display d : displays.getDisplays()) if (d.getDisplayId() != own) return d;
        return null;
    }

    void start() { displays.registerDisplayListener(this, handler); attach(); }
    void stop() { displays.unregisterDisplayListener(this); detach(); }
    void pause() { if (deck != null) deck.stop(); }
    void resume() { if (deck != null) deck.start(); }

    private void detach() {
        if (deck != null) deck.stop();
        if (presentation != null) presentation.dismiss();
        presentation = null; deck = null; D3PadView.thorDeck = false;
    }

    private void attach() {
        Display target = secondary();
        if (presentation != null && (target == null || presentation.getDisplay().getDisplayId() != target.getDisplayId())) detach();
        if (target == null || presentation != null) { refreshPad(); return; }
        presentation = new Presentation(act, target);
        deck = new ThorDeck(presentation.getContext(), act);
        presentation.setContentView(deck);
        presentation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            presentation.show(); deck.start(); D3PadView.thorDeck = true;
            Log.i("dm", "[thor] deck on display " + target.getDisplayId());
        } catch (WindowManager.InvalidDisplayException e) {
            Log.w("dm", "[thor] presentation failed", e); presentation = null; deck = null;
        }
        refreshPad();
    }
    private void refreshPad() { D3PadView p = act.d3Pad(); if (p != null) { p.iconGate(); p.invalidate(); } }

    @Override public void onDisplayAdded(int id) { attach(); }
    @Override public void onDisplayRemoved(int id) { attach(); }
    @Override public void onDisplayChanged(int id) { }
}

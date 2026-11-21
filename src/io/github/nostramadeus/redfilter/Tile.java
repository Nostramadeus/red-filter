package io.github.nostramadeus.redfilter;

import android.service.quicksettings.TileService;

/** Quick-settings tile: tap = filter on/off, long-press = open the sliders. */
public class Tile extends TileService {
    @Override public void onStartListening() {
        Heal.check(this);
        show();
    }

    @Override public void onClick() {
        Prefs.setOn(this, !Prefs.on(this));
        if (!FilterService.refresh()) { Heal.check(this); Note.show(this); }
        show();
    }

    private void show() {
        android.service.quicksettings.Tile t = getQsTile();
        if (t == null) return;
        t.setState(Prefs.on(this)
                ? android.service.quicksettings.Tile.STATE_ACTIVE
                : android.service.quicksettings.Tile.STATE_INACTIVE);
        t.setLabel("Red Filter");
        t.updateTile();
    }
}

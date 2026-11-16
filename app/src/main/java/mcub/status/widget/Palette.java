package mcub.status.widget;

import android.content.Context;
import android.content.res.Configuration;

/** Resolves Material You dynamic colors from the system palette (API 31+). */
final class Palette {
    final int surface;
    final int onSurface;
    final int muted;

    private Palette(int surface, int onSurface, int muted) {
        this.surface = surface;
        this.onSurface = onSurface;
        this.muted = muted;
    }

    static Palette resolve(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = night == Configuration.UI_MODE_NIGHT_YES;
        int surface = systemColor(context,
                dark ? "system_neutral1_800" : "system_neutral1_50");
        int onSurface = systemColor(context,
                dark ? "system_neutral1_50" : "system_neutral1_900");
        int muted = systemColor(context,
                dark ? "system_neutral1_300" : "system_neutral1_600");
        return new Palette(surface, onSurface, muted);
    }

    private static int systemColor(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "color", "android");
        return id != 0 ? context.getColor(id) : 0xFF1C1B1F;
    }
}

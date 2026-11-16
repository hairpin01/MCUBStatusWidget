package mcub.status.widget;

import android.content.Context;
import android.content.res.Configuration;

/** Resolves Material You dynamic colors from the system palette (API 31+). */
final class Palette {
    final int onSurface;
    final int muted;

    private Palette(int onSurface, int muted) {
        this.onSurface = onSurface;
        this.muted = muted;
    }

    static Palette resolve(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = night == Configuration.UI_MODE_NIGHT_YES;
        int onSurface = systemColor(context,
                dark ? "system_neutral1_50" : "system_neutral1_900");
        int muted = systemColor(context,
                dark ? "system_neutral1_300" : "system_neutral1_600");
        return new Palette(onSurface, muted);
    }

    private static int systemColor(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "color", "android");
        return id != 0 ? context.getColor(id) : 0xFF1C1B1F;
    }
}

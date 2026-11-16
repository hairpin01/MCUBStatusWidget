package mcub.status.widget;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.RemoteViews;
import android.content.SharedPreferences;

public class StatusWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "mcub.status.widget.ACTION_REFRESH";
    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        schedulePoll(context);
    }
    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelPoll(context);
    }
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        schedulePoll(context);
        for (int id : appWidgetIds) render(context, manager, id, null, null, null, null);
        startFetch(context, appWidgetIds);
    }

    /** Static entry point for PollReceiver so any class can trigger a refresh. */
    static void dispatchPoll(Context context) {
        int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, StatusWidgetProvider.class));
        if (ids.length > 0) startFetch(context, ids);
    }
    private static void startFetch(Context context, int[] appWidgetIds) {
        int[] ids = appWidgetIds != null ? appWidgetIds : AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, StatusWidgetProvider.class));
        if (ids.length == 0) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            ApiClient.Result result = ApiClient.fetch(app);
            String title = ApiClient.render(ApiClient.titleTemplate(app), result.placeholders);
            String status = result.reachable
                    ? ApiClient.render(ApiClient.statusTemplate(app), result.placeholders)
                    : app.getString(R.string.status_offline);
            String footer = result.reachable
                    ? ApiClient.render(ApiClient.footerTemplate(app), result.placeholders)
                    : (result.error == null ? "API unreachable" : result.error);
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            for (int id : ids) {
                render(app, mgr, id, result, title, status, footer);
            }
        }, "mcub-widget-fetch").start();
    }
    private static void render(Context context, AppWidgetManager manager, int id,
                               ApiClient.Result result, String title, String status, String footer) {
        Palette p = Palette.resolve(context);
        boolean ring = ApiClient.LAYOUT_RING.equals(ApiClient.layout(context));
        RemoteViews views = new RemoteViews(context.getPackageName(),
                ring ? R.layout.widget_ring : R.layout.widget_status);
        int dotColor;
        if (result == null) {
            dotColor = p.muted;
        } else if (!result.reachable) {
            dotColor = Color.rgb(0xE8, 0x63, 0x5E);
        } else {
            dotColor = Color.rgb(0x6D, 0xD5, 0x8A);
        }
        if (title == null) title = "MCUB";
        if (status == null) status = context.getString(R.string.status_loading);
        if (footer == null) footer = "";
        views.setTextViewText(R.id.title_text, title);
        views.setTextViewText(R.id.status_text, status);
        views.setTextViewText(R.id.detail_text, footer);

        // Colors: user HEX overrides, otherwise Material You palette
        SharedPreferences sp = ApiClient.prefs(context);
        Integer cTitle = ApiClient.parseColor(sp.getString("color_title", ""));
        Integer cStatus = ApiClient.parseColor(sp.getString("color_status", ""));
        Integer cFooter = ApiClient.parseColor(sp.getString("color_footer", ""));
        views.setTextColor(R.id.title_text, cTitle != null ? cTitle : p.onSurface);
        views.setTextColor(R.id.status_text, cStatus != null ? cStatus : p.onSurface);
        views.setTextColor(R.id.detail_text, cFooter != null ? cFooter : p.muted);
        // Alignment
        String align = ApiClient.alignment(context);
        int gravity;
        if (ApiClient.ALIGN_CENTER.equals(align)) gravity = Gravity.CENTER;
        else if (ApiClient.ALIGN_RIGHT.equals(align)) gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        else gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        views.setInt(R.id.status_text, "setGravity", gravity);
        views.setInt(R.id.detail_text, "setGravity", gravity);
        views.setInt(R.id.title_text, "setGravity",
                ApiClient.ALIGN_CENTER.equals(align) ? Gravity.CENTER
                : ApiClient.ALIGN_RIGHT.equals(align) ? Gravity.END
                : Gravity.START);
        // Status indicator: dot (bar design) or thick ring (frame design)
        int thickness = ApiClient.thicknessDp(context);
        if (ring) {
            int drawableId;
            switch (thickness) {
                case 2: case 3: drawableId = R.drawable.ring_stroke_2; break;
                case 4: case 5: drawableId = R.drawable.ring_stroke_4; break;
                case 6: case 7: drawableId = R.drawable.ring_stroke_6; break;
                default: drawableId = R.drawable.ring_stroke_8; break;
            }
            views.setInt(R.id.ring_view, "setColorFilter", dotColor);
            views.setInt(R.id.ring_view, "setImageResource", drawableId);
        } else {
            // dot diameter follows the same thickness setting
            views.setInt(R.id.status_icon, "setColorFilter", dotColor);
            views.setViewLayoutWidth(R.id.status_icon, thickness,
                    android.util.TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.status_icon, thickness,
                    android.util.TypedValue.COMPLEX_UNIT_DIP);
        }
        Intent refresh = new Intent(context, StatusWidgetProvider.class);
        refresh.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id});
        PendingIntent pi = PendingIntent.getBroadcast(context, id, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        manager.updateAppWidget(id, views);
    }
    private void schedulePoll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pollPendingIntent(context);
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis(),
                ApiClient.pollIntervalMs(context), pi);
    }
    private void cancelPoll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pollPendingIntent(context));
    }
    static PendingIntent pollPendingIntent(Context context) {
        Intent i = new Intent(context, PollReceiver.class);
        return PendingIntent.getBroadcast(context, 4242, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
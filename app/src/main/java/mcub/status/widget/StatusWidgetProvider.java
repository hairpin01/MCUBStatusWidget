package mcub.status.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

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
        for (int id : appWidgetIds) render(context, manager, id, null, null);
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
            String rendered = ApiClient.render(ApiClient.template(app), result.placeholders);
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            for (int id : ids) {
                render(app, mgr, id, result, rendered);
            }
        }, "mcub-widget-fetch").start();
    }

    private static void render(Context context, AppWidgetManager manager, int id,
                               ApiClient.Result result, String rendered) {
        Palette p = Palette.resolve(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_status);
        String title;
        int dotColor;
        if (result == null) {
            title = context.getString(R.string.status_loading);
            rendered = "";
            dotColor = p.muted;
        } else if (!result.reachable) {
            title = context.getString(R.string.status_offline);
            rendered = result.error == null ? "API unreachable" : result.error;
            dotColor = Color.rgb(0xE8, 0x63, 0x5E);
        } else {
            title = context.getString(R.string.status_online);
            dotColor = Color.rgb(0x6D, 0xD5, 0x8A);
        }
        views.setTextViewText(R.id.status_text, title);
        views.setTextViewText(R.id.detail_text, rendered == null ? "" : rendered);
        views.setTextColor(R.id.status_text, p.onSurface);
        views.setTextColor(R.id.detail_text, p.onSurface);
        views.setTextColor(R.id.title_text, p.onSurface);
        views.setInt(R.id.status_icon, "setColorFilter", dotColor);
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
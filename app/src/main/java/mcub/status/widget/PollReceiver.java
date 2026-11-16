package mcub.status.widget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Periodic poll triggered by AlarmManager. */
public class PollReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, StatusWidgetProvider.class));
        if (ids.length == 0) return;
        StatusWidgetProvider.dispatchPoll(context, ids);
    }
}

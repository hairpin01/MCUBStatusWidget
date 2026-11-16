package mcub.status.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends android.app.Activity {
    private static final String PREFS = "mcub_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        EditText urlInput = findViewById(R.id.input_url);
        EditText tokenInput = findViewById(R.id.input_token);
        urlInput.setText(prefs.getString("api_url", ApiClient.DEFAULT_URL));
        tokenInput.setText(prefs.getString("api_token", ""));
        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString("api_url", urlInput.getText().toString().trim())
                    .putString("api_token", tokenInput.getText().toString().trim())
                    .apply();
            refreshWidgets();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void refreshWidgets() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, StatusWidgetProvider.class));
        if (ids.length > 0) {
            Intent update = new Intent(this, StatusWidgetProvider.class);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(update);
        }
    }
}

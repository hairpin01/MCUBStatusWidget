package mcub.status.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;

public class SettingsActivity extends android.app.Activity {
    private static final String PREFS = "mcub_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        EditText urlInput = findViewById(R.id.input_url);
        EditText tokenInput = findViewById(R.id.input_token);
        EditText templateInput = findViewById(R.id.input_template);
        EditText modulesInput = findViewById(R.id.input_modules);
        EditText pollInput = findViewById(R.id.input_poll);

        urlInput.setText(prefs.getString("api_url", ApiClient.DEFAULT_URL));
        tokenInput.setText(prefs.getString("api_token", ""));
        templateInput.setText(prefs.getString("template", ApiClient.DEFAULT_TEMPLATE));
        modulesInput.setText(prefs.getString("modules_json", ApiClient.DEFAULT_MODULES));
        pollInput.setText(String.valueOf(prefs.getLong("poll_minutes", 1L)));

        Button test = findViewById(R.id.btn_test);
        test.setOnClickListener(v -> runTest(urlInput, tokenInput, templateInput, modulesInput));

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> {
            String modules = modulesInput.getText().toString();
            try {
                new JSONArray(modules);
            } catch (Exception e) {
                Toast.makeText(this, "Modules JSON invalid: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
            long minutes;
            try {
                minutes = Long.parseLong(pollInput.getText().toString().trim());
                if (minutes < 1) minutes = 1;
            } catch (Exception e) {
                minutes = 1;
            }
            prefs.edit()
                    .putString("api_url", urlInput.getText().toString().trim())
                    .putString("api_token", tokenInput.getText().toString().trim())
                    .putString("template", templateInput.getText().toString())
                    .putString("modules_json", modules)
                    .putLong("poll_minutes", minutes)
                    .apply();
            refreshWidgets();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });

        Button defaults = findViewById(R.id.btn_defaults);
        defaults.setOnClickListener(v -> {
            templateInput.setText(ApiClient.DEFAULT_TEMPLATE);
            modulesInput.setText(ApiClient.DEFAULT_MODULES);
            pollInput.setText("1");
            Toast.makeText(this, "Defaults restored (not saved yet)", Toast.LENGTH_SHORT).show();
        });
    }

    private void runTest(EditText urlInput, EditText tokenInput,
                         EditText templateInput, EditText modulesInput) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // temporarily apply current inputs for the test
        prefs.edit()
                .putString("api_url", urlInput.getText().toString().trim())
                .putString("api_token", tokenInput.getText().toString().trim())
                .putString("template", templateInput.getText().toString())
                .putString("modules_json", modulesInput.getText().toString())
                .apply();
        try {
            new JSONArray(modulesInput.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "Modules JSON invalid: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> {
            ApiClient.Result r = ApiClient.fetch(app);
            String rendered = ApiClient.render(templateInput.getText().toString(), r.placeholders);
            final String msg = (r.reachable ? "OK (" + r.latencyMs + "ms)\n" : "FAIL: " + r.error + "\n")
                    + rendered;
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        }, "mcub-test").start();
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

package mcub.status.widget;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
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
        EditText titleInput = findViewById(R.id.input_title_template);
        EditText statusInput = findViewById(R.id.input_status_template);
        EditText footerInput = findViewById(R.id.input_template);
        EditText modulesInput = findViewById(R.id.input_modules);
        EditText pollInput = findViewById(R.id.input_poll);
        EditText thicknessInput = findViewById(R.id.input_thickness);
        EditText colorTitle = findViewById(R.id.input_color_title);
        EditText colorStatus = findViewById(R.id.input_color_status);
        EditText colorFooter = findViewById(R.id.input_color_footer);
        RadioGroup align = findViewById(R.id.align_group);
        RadioGroup design = findViewById(R.id.design_group);
        urlInput.setText(prefs.getString("api_url", ApiClient.DEFAULT_URL));
        tokenInput.setText(prefs.getString("api_token", ""));
        titleInput.setText(prefs.getString("template_title", ApiClient.DEFAULT_TITLE_TEMPLATE));
        statusInput.setText(prefs.getString("template_status", ApiClient.DEFAULT_STATUS_TEMPLATE));
        footerInput.setText(prefs.getString("template", ApiClient.DEFAULT_TEMPLATE));
        modulesInput.setText(prefs.getString("modules_json", ApiClient.DEFAULT_MODULES));
        pollInput.setText(String.valueOf(prefs.getLong("poll_minutes", 1L)));
        thicknessInput.setText(String.valueOf(prefs.getInt("thickness", ApiClient.DEFAULT_THICKNESS_DP)));
        colorTitle.setText(prefs.getString("color_title", ""));
        colorStatus.setText(prefs.getString("color_status", ""));
        colorFooter.setText(prefs.getString("color_footer", ""));
        String curAlign = prefs.getString("align", ApiClient.ALIGN_LEFT);
        if (ApiClient.ALIGN_CENTER.equals(curAlign)) align.check(R.id.align_center);
        else if (ApiClient.ALIGN_RIGHT.equals(curAlign)) align.check(R.id.align_right);
        else align.check(R.id.align_left);
        String curDesign = prefs.getString("layout", ApiClient.LAYOUT_BAR);
        if (ApiClient.LAYOUT_RING.equals(curDesign)) design.check(R.id.design_ring);
        else design.check(R.id.design_bar);
        EditText[] colorInputs = {colorTitle, colorStatus, colorFooter};
        Button test = findViewById(R.id.btn_test);
        test.setOnClickListener(v -> runTest(titleInput, statusInput, footerInput));
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
            for (EditText ci : colorInputs) {
                String s = ci.getText().toString().trim();
                if (!s.isEmpty() && ApiClient.parseColor(s) == null) {
                    Toast.makeText(this, "Invalid HEX color: " + s
                            + " (use e.g. #6DD58A)", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            long minutes;
            try {
                minutes = Long.parseLong(pollInput.getText().toString().trim());
                if (minutes < 1) minutes = 1;
            } catch (Exception e) {
                minutes = 1;
            }
            int thickness;
            try {
                thickness = Integer.parseInt(thicknessInput.getText().toString().trim());
            } catch (Exception e) {
                thickness = ApiClient.DEFAULT_THICKNESS_DP;
            }
            if (thickness < 2 || thickness > 12) {
                Toast.makeText(this, "Indicator thickness must be 2–12 dp",
                        Toast.LENGTH_LONG).show();
                return;
            }
            int alignId = align.getCheckedRadioButtonId();
            String alignVal = alignId == R.id.align_center ? ApiClient.ALIGN_CENTER
                    : alignId == R.id.align_right ? ApiClient.ALIGN_RIGHT
                    : ApiClient.ALIGN_LEFT;
            String designVal = design.getCheckedRadioButtonId() == R.id.design_ring
                    ? ApiClient.LAYOUT_RING : ApiClient.LAYOUT_BAR;
            prefs.edit()
                    .putString("api_url", urlInput.getText().toString().trim())
                    .putString("api_token", tokenInput.getText().toString().trim())
                    .putString("template_title", titleInput.getText().toString())
                    .putString("template_status", statusInput.getText().toString())
                    .putString("template", footerInput.getText().toString())
                    .putString("modules_json", modules)
                    .putLong("poll_minutes", minutes)
                    .putInt("thickness", thickness)
                    .putString("color_title", colorInputs[0].getText().toString().trim())
                    .putString("color_status", colorInputs[1].getText().toString().trim())
                    .putString("color_footer", colorInputs[2].getText().toString().trim())
                    .putString("align", alignVal)
                    .putString("layout", designVal)
                    .apply();
            refreshWidgets();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        Button defaults = findViewById(R.id.btn_defaults);
        defaults.setOnClickListener(v -> {
            titleInput.setText(ApiClient.DEFAULT_TITLE_TEMPLATE);
            statusInput.setText(ApiClient.DEFAULT_STATUS_TEMPLATE);
            footerInput.setText(ApiClient.DEFAULT_TEMPLATE);
            modulesInput.setText(ApiClient.DEFAULT_MODULES);
            pollInput.setText("1");
            thicknessInput.setText(String.valueOf(ApiClient.DEFAULT_THICKNESS_DP));
            colorTitle.setText("");
            colorStatus.setText("");
            colorFooter.setText("");
            align.check(R.id.align_left);
            design.check(R.id.design_bar);
            Toast.makeText(this, "Defaults restored (not saved yet)", Toast.LENGTH_SHORT).show();
        });
    }
    private void runTest(EditText titleInput, EditText statusInput, EditText footerInput) {
        try {
            new JSONArray(
                    ((EditText) findViewById(R.id.input_modules)).getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "Modules JSON invalid: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        final String title = titleInput.getText().toString();
        final String status = statusInput.getText().toString();
        final String footer = footerInput.getText().toString();
        new Thread(() -> {
            ApiClient.Result r = ApiClient.fetch(app);
            String msg = (r.reachable ? "OK (" + r.latencyMs + "ms)\n" : "FAIL: " + r.error + "\n")
                    + ApiClient.render(title, r.placeholders) + "\n"
                    + ApiClient.render(status, r.placeholders) + "\n"
                    + ApiClient.render(footer, r.placeholders);
            final String m = msg;
            runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
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
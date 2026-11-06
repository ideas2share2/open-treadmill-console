package org.opentread.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/**
 * Minimal local Home. Opens the console once per Home intent, never sends motor commands. A
 * crash/Back returns to these recovery buttons instead of an automatic relaunch loop.
 */
public final class MainActivity extends Activity {
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setBackgroundColor(0xff0d0f0e);
    root.setPadding(40, 40, 40, 40);
    TextView title = new TextView(this);
    title.setText("open");
    title.setTextSize(64);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD_ITALIC);
    title.setTextColor(0xffc8ff30);
    root.addView(title);
    Button open = new Button(this);
    open.setText("Open Console");
    root.addView(open, new LinearLayout.LayoutParams(420, 100));
    open.setOnClickListener(v -> openConsole());
    Button settings = new Button(this);
    settings.setText("Tablet settings");
    root.addView(settings, new LinearLayout.LayoutParams(420, 90));
    settings.setOnClickListener(
        v -> {
          try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
          } catch (Exception ignored) {
          }
        });
    setContentView(root);
    if (state == null) openConsole();
  }

  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if (i != null && i.hasCategory(Intent.CATEGORY_HOME)) openConsole();
  }

  void openConsole() {
    Intent i = getPackageManager().getLaunchIntentForPackage("org.opentread.console");
    if (i == null) {
      Toast.makeText(
              this, "Console is unavailable. Use Tablet settings to restore it.", Toast.LENGTH_LONG)
          .show();
      return;
    }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    try {
      startActivity(i);
    } catch (Exception e) {
      Toast.makeText(
              this, "Console could not open. Tablet settings remain available.", Toast.LENGTH_LONG)
          .show();
    }
  }

  public void onBackPressed() {
    /* Remain on the recovery Home; never start motion. */
  }
}

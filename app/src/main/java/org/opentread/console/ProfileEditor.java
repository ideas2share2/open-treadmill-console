package org.opentread.console;

import android.app.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Native form; no web page, sensor subscription or controller command. */
public final class ProfileEditor {
  static EditText field(
      Activity a, LinearLayout form, String label, String value, boolean decimal) {
    TextView caption = new TextView(a);
    caption.setText(label);
    caption.setTextSize(16);
    form.addView(caption);
    EditText edit = new EditText(a);
    edit.setSingleLine(true);
    edit.setTextSize(18);
    edit.setInputType(
        InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
    edit.setText(value);
    edit.setHint("Optional");
    form.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    return edit;
  }

  static String number(double n) {
    return n == 0 ? "" : String.format(Locale.US, "%.1f", n).replaceAll("\\.0$", "");
  }

  static double optional(EditText e) {
    String s = e.getText().toString().trim();
    return s.isEmpty() ? 0 : Double.parseDouble(s);
  }

  public static AlertDialog show(
      Activity a,
      Ui ui,
      ProfileSettings settings,
      String id,
      BooleanSupplier ready,
      Runnable saved) {
    ProfileData p = settings.get(id);
    LinearLayout form = new LinearLayout(a);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(24, 12, 24, 12);
    EditText name = field(a, form, "Name", p.name, false);
    name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
    EditText age = field(a, form, "Age (years)", p.age == 0 ? "" : Integer.toString(p.age), false);
    TextView sexLabel = new TextView(a);
    sexLabel.setText("Sex (optional)");
    sexLabel.setTextSize(16);
    form.addView(sexLabel);
    Spinner sex = new Spinner(a);
    String[] sexes = {"Unspecified", "Female", "Male"};
    sex.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, sexes));
    sex.setSelection(p.sex.equals("Female") ? 1 : p.sex.equals("Male") ? 2 : 0);
    form.addView(sex);
    EditText weight = field(a, form, "Weight (lb)", number(p.weightLb), true);
    double displayHeight = Math.round(p.heightIn * 10) / 10.0;
    EditText feet =
        field(
            a,
            form,
            "Height (feet)",
            p.heightIn == 0 ? "" : Integer.toString((int) displayHeight / 12),
            false);
    EditText inches =
        field(
            a,
            form,
            "Additional inches (0–11.9)",
            p.heightIn == 0 ? "" : number(displayHeight % 12),
            true);
    TextView help = new TextView(a);
    help.setText(
        "Estimated active calories use weight, speed and incline. Age, sex and height are saved for"
            + " your profile; they do not change this formula. Adult estimate only.\n\n"
            + "Changes apply to your next workout. Names keep their existing history and color.");
    help.setTextSize(14);
    form.addView(help);
    TextView error = new TextView(a);
    error.setTextColor(Ui.RED);
    form.addView(error);
    ScrollView scroll = new ScrollView(a);
    scroll.addView(form);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle("Edit " + p.name)
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        ignored -> {
          dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui.accent);
          dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ui.accent);
          dialog
              .getWindow()
              .setLayout(
                  ui.u(710),
                  Math.min(
                      ui.u(780), a.getResources().getDisplayMetrics().heightPixels - ui.u(50)));
          dialog
              .getWindow()
              .setSoftInputMode(
                  WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                      | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    if (!ready.getAsBoolean()) return;
                    try {
                      String ageText = age.getText().toString().trim();
                      int years = ageText.isEmpty() ? 0 : Integer.parseInt(ageText);
                      double ft = optional(feet), inch = optional(inches);
                      if (ft != Math.rint(ft))
                        throw new IllegalArgumentException("Use a whole number for feet");
                      if (inch < 0 || inch >= 12)
                        throw new IllegalArgumentException(
                            "Additional inches must be less than 12");
                      ProfileData updated =
                          new ProfileData(
                              id,
                              name.getText().toString(),
                              years,
                              sex.getSelectedItemPosition() == 0
                                  ? ""
                                  : sexes[sex.getSelectedItemPosition()],
                              optional(weight),
                              ft * 12 + inch);
                      settings.save(updated);
                      dialog.dismiss();
                      saved.run();
                    } catch (NumberFormatException badNumber) {
                      error.setText("Check the numbers. Use a whole number for age and feet.");
                    } catch (IllegalArgumentException invalid) {
                      error.setText(invalid.getMessage());
                    }
                  });
        });
    dialog.show();
    return dialog;
  }
}

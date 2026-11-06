package org.opentread.console;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.time.format.*;
import java.util.Locale;

/** Native, cached personal records screen. No controller references or hardware commands. */
public final class RecordsPanel extends LinearLayout {
  public interface Actions {
    void overview();

    void session(WorkoutRecord record);
  }

  final Ui ui;
  final Actions actions;
  final TextView title, sub;
  final TextView[] values = new TextView[6], notes = new TextView[6];
  final LinearLayout badges;
  PersonalBests model;

  public RecordsPanel(Context c, Ui ui, Actions actions) {
    super(c);
    this.ui = ui;
    this.actions = actions;
    setOrientation(VERTICAL);
    ui.pad(this, 32, 20);
    LinearLayout head = ui.row();
    addView(head, new LayoutParams(-1, ui.u(64)));
    title = ui.text("Your personal bests", 44, ui.accent, true);
    head.addView(title, new LayoutParams(0, -1, 1));
    head.addView(
        ui.button("‹  Overview", 21, 0, Ui.TEXT, () -> actions.overview()),
        new LayoutParams(ui.u(200), ui.u(48)));
    sub = ui.text("Your saved Console workouts · all time", 20, Ui.MUTED, false);
    addView(sub, new LayoutParams(-1, ui.u(34)));
    String[] labels = {
      "Longest workout",
      "Longest distance",
      "Fastest 10+ min workout",
      "Current streak",
      "Longest streak",
      "All-time activity"
    };
    for (int row = 0; row < 2; row++) {
      LinearLayout band = ui.row();
      LayoutParams bp = new LayoutParams(-1, ui.u(160));
      bp.topMargin = ui.u(10);
      addView(band, bp);
      for (int col = 0; col < 3; col++) {
        int n = row * 3 + col;
        LinearLayout card = ui.col();
        ui.pad(card, 22, 12);
        card.setBackground(ui.shape(Ui.PANEL, Ui.LINE, ui.u(12)));
        LayoutParams cp = new LayoutParams(0, -1, 1);
        if (col > 0) cp.leftMargin = ui.u(18);
        band.addView(card, cp);
        card.addView(ui.text(labels[n], 21, Ui.TEXT, false), new LayoutParams(-1, ui.u(30)));
        values[n] = ui.text("—", 49, ui.accent, true);
        card.addView(values[n], new LayoutParams(-1, ui.u(60)));
        notes[n] = ui.text("", 17, Ui.MUTED, false);
        notes[n].setMaxLines(2);
        card.addView(notes[n], new LayoutParams(-1, 0, 1));
        if (n < 3) {
          card.setOnClickListener(
              v -> {
                if (model == null) return;
                WorkoutRecord r =
                    n == 0 ? model.longestTime : n == 1 ? model.longestDistance : model.fastest;
                if (r != null) actions.session(r);
              });
        }
      }
    }
    TextView earned = ui.text("Small wins add up.", 25, Ui.TEXT, true);
    LayoutParams ep = new LayoutParams(-1, ui.u(45));
    ep.topMargin = ui.u(12);
    addView(earned, ep);
    HorizontalScrollView scroll = new HorizontalScrollView(c);
    scroll.setHorizontalScrollBarEnabled(false);
    addView(scroll, new LayoutParams(-1, ui.u(64)));
    badges = ui.row();
    scroll.addView(badges, new android.widget.FrameLayout.LayoutParams(-2, -1));
    TextView rules =
        ui.text(
            "Streaks: one saved workout of at least 10 moving minutes per day · device local time.\n"
                + "Days use the workout’s start date. Tap a record for details.\n"
                + "Records begin with your saved Console history.",
            17,
            Ui.MUTED,
            false);
    LayoutParams rp = new LayoutParams(-1, 0, 1);
    rp.topMargin = ui.u(10);
    addView(rules, rp);
  }

  public void data(PersonalBests b, String name) {
    model = b;
    ui.set(title, name + "’s personal bests");
    ui.set(sub, "Your saved Console workouts · all time");
    fill(
        0,
        b.longestTime == null ? "—" : WorkoutMath.duration(b.longestTime.movingMs),
        date(b.longestTime));
    fill(
        1,
        b.longestDistance == null
            ? "—"
            : String.format(Locale.US, "%.2f mi", b.longestDistance.meters / WorkoutMath.MILE),
        date(b.longestDistance));
    fill(
        2,
        b.fastest == null ? "—" : WorkoutMath.pace(b.fastest.meters, b.fastest.movingMs) + " /mi",
        b.fastest == null
            ? "Save a 10-minute workout to begin."
            : String.format(
                Locale.US, "%.1f mph average · %s", b.fastest.avgMph(), date(b.fastest)));
    fill(3, b.currentStreak + " days", b.streakNote());
    fill(
        4,
        b.bestStreak + " days",
        b.bestStart == null
            ? "Your first 10-minute workout starts it."
            : b.bestStart.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
                + " – "
                + b.bestEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)));
    fill(
        5,
        b.sessions + " workouts",
        String.format(
            Locale.US,
            "%.2f total mi · %s moving",
            b.totalMeters / WorkoutMath.MILE,
            WorkoutMath.duration(b.totalMoving)));
    badges.removeAllViews();
    if (b.badges.isEmpty()) {
      badges.addView(
          ui.text(
              "Your first 10-minute workout has a small surprise waiting.", 19, Ui.MUTED, false));
      return;
    }
    for (String s : b.badges) {
      TextView badge = ui.text(s, 18, ui.accent, true);
      badge.setPadding(ui.u(18), ui.u(10), ui.u(18), ui.u(10));
      badge.setBackground(ui.shape(Ui.PANEL, ui.accent, ui.u(12)));
      LayoutParams p = new LayoutParams(-2, ui.u(56));
      p.rightMargin = ui.u(14);
      badges.addView(badge, p);
    }
  }

  void fill(int i, String v, String note) {
    ui.set(values[i], v);
    ui.set(notes[i], note);
  }

  String date(WorkoutRecord r) {
    return r == null
        ? "Save your first workout to begin."
        : WorkoutMath.date(r.startMs).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US));
  }
}

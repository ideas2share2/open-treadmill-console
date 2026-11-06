package org.opentread.console;

import android.content.*;
import android.graphics.*;
import android.view.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Lightweight native charts: bounded plot buffers; no animations or redraw timer. */
public final class Charts {
  public static class MiniTrack extends View {
    final Ui ui;
    final Paint paint = new Paint(3);
    final Path outline = new Path(), progress = new Path();
    final PathMeasure measure = new PathMeasure();
    final RectF bounds = new RectF();
    final float[] dot = new float[2];
    double meters = -1;
    int announcedLap = -1;

    public MiniTrack(Context c, Ui u) {
      super(c);
      ui = u;
    }

    public void data(double distance) {
      double next = TrackProgress.safe(distance);
      if (Math.abs(next - meters) < .1) return;
      meters = next;
      int lap = TrackProgress.laps(meters) + 1;
      if (lap != announcedLap) {
        announcedLap = lap;
        setContentDescription("Quarter-mile track, lap " + lap);
      }
      invalidate();
    }

    protected void onDraw(Canvas c) {
      float pad = ui.u(4), radius = Math.max(1, (getHeight() - 2 * pad) / 2);
      bounds.set(pad, pad, getWidth() - pad, getHeight() - pad);
      outline.reset();
      outline.addRoundRect(bounds, radius, radius, Path.Direction.CW);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(ui.u(2));
      paint.setColor(Ui.LINE);
      c.drawPath(outline, paint);
      measure.setPath(outline, true);
      progress.reset();
      float at = measure.getLength() * TrackProgress.fraction(meters);
      measure.getSegment(0, at, progress, true);
      paint.setColor(ui.accent);
      c.drawPath(progress, paint);
      measure.getPosTan(at, dot, null);
      paint.setStyle(Paint.Style.FILL);
      c.drawCircle(dot[0], dot[1], ui.u(3), paint);
    }
  }

  public static class Bars extends View {
    final Paint p = new Paint(3);
    final Ui ui;
    List<WorkoutRecord> rows = Collections.emptyList();
    LocalDate from = LocalDate.now(WorkoutMath.ZONE), to = from.plusDays(7);
    boolean small;
    double[][] sums = new double[7][4];
    int bins = 7;

    public Bars(Context c, Ui u, boolean s) {
      super(c);
      ui = u;
      small = s;
      p.setTypeface(u.regular);
    }

    public void data(List<WorkoutRecord> r, LocalDate a, LocalDate b) {
      rows = r;
      from = a;
      to = b;
      long days = java.time.temporal.ChronoUnit.DAYS.between(a, b);
      bins = days <= 7 ? 7 : Math.min(31, (int) days);
      sums = new double[bins][4];
      for (WorkoutRecord w : r) {
        long n = java.time.temporal.ChronoUnit.DAYS.between(a, WorkoutMath.date(w.startMs));
        if (n >= 0 && n < bins) {
          int person = ProfileData.index(w.profile);
          if (person >= 0) sums[(int) n][person] += w.meters / WorkoutMath.MILE;
        }
      }
      invalidate();
    }

    protected void onDraw(Canvas c) {
      float w = getWidth(),
          h = getHeight(),
          left = ui.u(small ? 34 : 64),
          top = ui.u(18),
          bottom = h - ui.u(small ? 44 : 55),
          right = w - ui.u(10);
      double max = .5;
      for (double[] v : sums) for (double value : v) max = Math.max(max, value);
      max = Math.ceil(max * 2) / 2;
      float fs = ui.u(small ? 12 : 18);
      p.setTextSize(fs);
      p.setStrokeWidth(1);
      p.setTextAlign(Paint.Align.RIGHT);
      for (int i = 0; i <= 4; i++) {
        float y = bottom - (bottom - top) * i / 4;
        p.setColor(Ui.LINE);
        c.drawLine(left, y, right, y, p);
        p.setColor(Ui.MUTED);
        c.drawText(String.format(Locale.US, "%.1f", max * i / 4), left - ui.u(8), y + fs * .3f, p);
      }
      float span = (right - left) / bins;
      for (int n = 0; n < bins; n++) {
        float x = left + span * n + span * .16f;
        for (int j = 0; j < 4; j++) {
          p.setColor(ProfileTheme.accent(ProfileData.IDS[j]));
          float bw = span * .17f;
          float bh = (float) (sums[n][j] / max * (bottom - top));
          c.drawRect(
              x + j * (bw + span * .02f), bottom - bh, x + j * (bw + span * .02f) + bw, bottom, p);
        }
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(
            WorkoutMath.date(System.currentTimeMillis()).equals(from.plusDays(n))
                ? ui.accent
                : Ui.TEXT);
        p.setTextSize(fs);
        if (bins <= 7) {
          c.drawText(
              from.plusDays(n).format(DateTimeFormatter.ofPattern("EEE", Locale.US)),
              left + span * (n + .5f),
              bottom + ui.u(19),
              p);
          c.drawText(
              from.plusDays(n).format(DateTimeFormatter.ofPattern("MMM d", Locale.US)),
              left + span * (n + .5f),
              bottom + ui.u(36),
              p);
        } else if (n % Math.max(1, bins / 8) == 0)
          c.drawText(Integer.toString(n + 1), left + span * (n + .5f), bottom + ui.u(24), p);
      }
    }
  }

  public static class Trace extends View {
    final Ui ui;
    final Paint p = new Paint(3);
    WorkoutRecord record;
    String kind = "Pace";
    boolean ribbon;
    final RibbonPlot ribbonPlot = new RibbonPlot();
    final Path ribbonLine = new Path(), ribbonFill = new Path();
    int lastCount = -1;
    long lastTime = -1;

    public Trace(Context c, Ui u, boolean r) {
      super(c);
      ui = u;
      ribbon = r;
      p.setTypeface(u.regular);
    }

    public void data(WorkoutRecord r, String k) {
      int n = r == null ? 0 : r.points.size();
      long t = r == null ? 0 : r.movingMs;
      if (record != r || lastCount != n || !kind.equals(k)) {
        record = r;
        kind = k;
        lastCount = n;
        lastTime = t;
        if (ribbon) ribbonPlot.update(r);
        invalidate();
      }
    }

    void label(Canvas c, String s, float x, float y, int color, int size) {
      p.setColor(color);
      p.setTextSize(ui.u(size));
      p.setTextAlign(Paint.Align.LEFT);
      p.setStyle(Paint.Style.FILL);
      c.drawText(s, x, y, p);
    }

    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      if (record == null
          || record.points.isEmpty()
          || (record.points.size() < 2 && !kind.equals("Heart rate"))) {
        label(
            c,
            kind.equals("Heart rate")
                ? "No heart-rate readings were recorded."
                : ribbon
                    ? "Speed and incline will appear during your workout."
                    : "Pace and incline will appear during your workout.",
            ui.u(12),
            h / 2,
            Ui.MUTED,
            ribbon ? 12 : 18);
        return;
      }
      if (kind.equals("Splits")) {
        List<WorkoutMath.Split> splits = WorkoutMath.splits(record.points);
        int count = Math.min(splits.size(), 8);
        for (int i = 0; i < count; i++) {
          WorkoutMath.Split s = splits.get(i);
          label(
              c,
              String.format(
                  Locale.US,
                  "%s %d    %.2f mi                 %s /mi",
                  s.meters < WorkoutMath.MILE - 1 ? "Partial" : "Mile",
                  s.number,
                  s.meters / WorkoutMath.MILE,
                  WorkoutMath.pace(s.meters, s.movingMs)),
              ui.u(12),
              ui.u(26 + i * 30),
              Ui.TEXT,
              17);
        }
        if (splits.size() > 8)
          label(c, "First 8 splits shown", ui.u(12), h - ui.u(8), Ui.MUTED, 12);
        return;
      }
      if (ribbon) {
        float row = (h - ui.u(16)) / 2;
        ribbonRow(c, ribbonPlot.speed, "Speed (mph)", false, 0, row - ui.u(2), w);
        ribbonRow(c, ribbonPlot.incline, "Incline (%)", true, row + ui.u(2), h - ui.u(16), w);
        label(c, "0:00", ui.u(110), h - ui.u(1), Ui.MUTED, 11);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(WorkoutMath.duration(ribbonPlot.endMs), w - ui.u(36), h - ui.u(1), p);
      } else plot(c, kind, 0, h - ui.u(20), w);
    }

    void ribbonRow(
        Canvas c,
        RibbonPlot.Series series,
        String title,
        boolean grade,
        float y0,
        float y1,
        float width) {
      float left = ui.u(110), right = width - ui.u(36);
      float top = y0 + ui.u(3), bottom = y1 - ui.u(3);
      if (right <= left || bottom <= top) return;
      int color = grade ? 0xffa8aaa3 : ui.accent;
      label(c, title, ui.u(6), y0 + ui.u(13), Ui.MUTED, 11);
      label(
          c,
          Double.isFinite(series.current)
              ? String.format(Locale.US, grade ? "%.1f%%" : "%.1f mph", series.current)
              : "—",
          ui.u(6),
          y0 + ui.u(31),
          color,
          14);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1);
      p.setColor(Ui.LINE);
      for (int i = 0; i <= 2; i++) {
        float y = top + (bottom - top) * i / 2;
        c.drawLine(left, y, right, y, p);
      }
      if (series.min < 0) {
        float zero = bottom - (float) series.fraction(0) * (bottom - top);
        p.setColor(Ui.MUTED);
        c.drawLine(left, zero, right, zero, p);
      }
      label(
          c,
          String.format(Locale.US, "%.0f", series.max),
          right + ui.u(6),
          top + ui.u(9),
          Ui.MUTED,
          10);
      label(c, String.format(Locale.US, "%.0f", series.min), right + ui.u(6), bottom, Ui.MUTED, 10);
      ribbonLine.reset();
      ribbonFill.reset();
      int segment = -1;
      float lastX = 0, lastY = 0;
      boolean pen = false;
      for (int i = 0; i < series.count; i++) {
        float x = left + (right - left) * (float) ((double) series.time[i] / ribbonPlot.endMs);
        float y = bottom - (float) series.fraction(series.value[i]) * (bottom - top);
        if (!pen || series.segment[i] != segment) {
          if (pen) {
            ribbonFill.lineTo(lastX, bottom);
            ribbonFill.close();
          }
          ribbonLine.moveTo(x, y);
          ribbonFill.moveTo(x, bottom);
          ribbonFill.lineTo(x, y);
          pen = true;
        } else {
          ribbonLine.lineTo(x, y);
          ribbonFill.lineTo(x, y);
        }
        lastX = x;
        lastY = y;
        segment = series.segment[i];
      }
      if (pen) {
        ribbonFill.lineTo(lastX, bottom);
        ribbonFill.close();
      }
      p.setStyle(Paint.Style.FILL);
      p.setColor((color & 0x00ffffff) | 0x24000000);
      c.drawPath(ribbonFill, p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(ui.u(2));
      p.setColor(color);
      c.drawPath(ribbonLine, p);
      p.setStyle(Paint.Style.FILL);
      if (pen) c.drawCircle(lastX, lastY, ui.u(2), p);
    }

    void plot(Canvas c, String k, float y0, float y1, float width) {
      boolean grade = k.equals("Incline"), pulse = k.equals("Heart rate");
      float left = ui.u(ribbon ? 90 : 68),
          right = width - ui.u(14),
          top = y0 + ui.u(ribbon ? 8 : 28),
          bottom = y1 - ui.u(ribbon ? 14 : 22);
      double min = grade ? -3 : Double.MAX_VALUE, max = grade ? 15 : 0;
      for (WorkoutRecord.Point pt : record.points)
        if (!grade && (pulse ? HeartReading.valid(pt.hr) : pt.speedRaw > 5)) {
          double pace = pulse ? pt.hr : 60 / WorkoutMath.mph(pt.speedRaw);
          min = Math.min(min, pace);
          max = Math.max(max, pace);
        }
      if (!grade) {
        if (min == Double.MAX_VALUE) {
          if (pulse) {
            label(
                c,
                "No heart-rate readings were recorded.",
                ui.u(12),
                (top + bottom) / 2,
                Ui.MUTED,
                18);
            return;
          }
          min = 0;
          max = 60;
        } else {
          min = Math.floor(min);
          max = Math.ceil(max);
          if (max - min < (pulse ? 20 : 2)) {
            min = Math.max(0, min - (pulse ? 10 : 1));
            max = min + (pulse ? 20 : 2);
          }
        }
      }
      if (ribbon) {
        label(c, grade ? "Incline (%)" : "Pace (/mi)", ui.u(6), (top + bottom) / 2, Ui.MUTED, 12);
      }
      p.setStrokeWidth(1);
      for (int i = 0; i <= 2; i++) {
        float y = top + (bottom - top) * i / 2;
        p.setColor(Ui.LINE);
        c.drawLine(left, y, right, y, p);
        if (!ribbon) {
          double v = grade || pulse ? max - (max - min) * i / 2 : min + (max - min) * i / 2;
          label(
              c,
              grade
                  ? String.format(Locale.US, "%.0f%%", v)
                  : pulse ? String.format(Locale.US, "%.0f bpm", v) : WorkoutMath.minutes(v),
              ui.u(3),
              y + ui.u(5),
              Ui.MUTED,
              12);
        }
      }
      Path path = new Path();
      int n = record.points.size(), step = Math.max(1, (int) Math.ceil(n / 198.0));
      boolean pen = false;
      long end = Math.max(1, record.movingMs);
      for (int i = 0; i < n; i += pulse ? 1 : step) {
        WorkoutRecord.Point pt = record.points.get(i);
        if (pulse && !HeartReading.valid(pt.hr)) {
          pen = false;
          continue;
        }
        if (pulse && i % step != 0 && i != n - 1) continue;
        if (!grade && !pulse && pt.speedRaw <= 5) {
          pen = false;
          continue;
        }
        double v = grade ? pt.gradeRaw / 100.0 : pulse ? pt.hr : 60 / WorkoutMath.mph(pt.speedRaw);
        double ratio = grade || pulse ? (max - v) / (max - min) : (v - min) / (max - min);
        float x = left + (right - left) * pt.movingMs / end,
            y = top + (float) Math.max(0, Math.min(1, ratio)) * (bottom - top);
        if (pulse) {
          p.setColor(ui.accent);
          p.setStyle(Paint.Style.FILL);
          c.drawCircle(x, y, ui.u(2), p);
        }
        if (!pen) {
          path.moveTo(x, y);
          pen = true;
        } else path.lineTo(x, y);
      }
      p.setColor(grade ? 0xffa8aaa3 : ui.accent);
      p.setStrokeWidth(ui.u(ribbon ? 2 : 3));
      p.setStyle(Paint.Style.STROKE);
      c.drawPath(path, p);
      p.setStyle(Paint.Style.FILL);
      if (!ribbon || grade) {
        label(c, "0:00", left, y1, Ui.MUTED, 12);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(Ui.MUTED);
        p.setTextSize(ui.u(12));
        c.drawText(WorkoutMath.duration(record.movingMs), right, y1, p);
      }
    }
  }
}

package org.opentread.console;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

public final class WorkoutMath {
  public static final ZoneId ZONE = ZoneId.systemDefault();
  public static final double MILE = 1609.344;

  public static double mph(int raw) {
    return raw / 160.9344;
  }

  public static int speedStep(int raw, boolean up) {
    double mph = Math.round((mph(raw) + (up ? .1 : -.1)) * 10) / 10.0;
    return Math.max(80, Math.min(1931, (int) Math.round(mph * 160.9344)));
  }

  public static int inclineStep(int raw, boolean up) {
    return Math.max(-300, Math.min(1500, (int) Math.round(raw / 50.0) * 50 + (up ? 50 : -50)));
  }

  public static LocalDate date(long ms) {
    return Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate();
  }

  public static LocalDate weekStart(LocalDate d) {
    return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
  }

  public static long midnight(LocalDate d) {
    return d.atStartOfDay(ZONE).toInstant().toEpochMilli();
  }

  public static String duration(long ms) {
    long s = Math.max(0, ms / 1000);
    return s >= 3600
        ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
  }

  public static String pace(double meters, long moving) {
    if (meters <= 0 || moving <= 0) return "—";
    long sec = Math.round(moving / 1000.0 / (meters / MILE));
    return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
  }

  public static String minutes(double minutes) {
    long s = Math.round(Math.max(0, minutes) * 60);
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
  }

  public static String livePace(int raw) {
    return raw <= 5 ? "—" : pace(MILE, Math.round(3600000.0 / mph(raw)));
  }

  public static class Totals {
    public final ActivityType activity = new ActivityType();
    public long walkMs, runMs;
    public double walkMeters, runMeters;
    public long movingMs;
    public double meters, elevationM, maxKph, activeKcalPerKg;
    private long previous = -1;
    private int prevMode, prevSpeed, prevGrade;

    public void sample(long mono, int mode, int speed, int grade) {
      activity.sample(mono, mode, speed);
      long dt = mono - previous;
      if (previous >= 0 && dt > 0 && dt <= 1500 && mode == 2 && prevMode == 2) {
        double avg = (speed + prevSpeed) / 2.0;
        if (avg > 5) {
          double dist = avg / 100.0 / 3.6 * dt / 1000.0;
          movingMs += dt;
          meters += dist;
          activeKcalPerKg += Calories.perKgPerMinute(avg, (grade + prevGrade) / 2.0) * dt / 60000.0;
          if (activity.running()) {
            runMs += dt;
            runMeters += dist;
          } else {
            walkMs += dt;
            walkMeters += dist;
          }
          elevationM += dist * Math.max(0, (grade + prevGrade) / 2.0) / 10000.0;
        }
      }
      maxKph = Math.max(maxKph, speed / 100.0);
      previous = mono;
      prevMode = mode;
      prevSpeed = speed;
      prevGrade = grade;
    }

    public void gap() {
      previous = -1;
      activity.gap();
    }
  }

  public static class Split {
    public int number;
    public double meters;
    public final ActivityType activity = new ActivityType();
    public long walkMs, runMs;
    public double walkMeters, runMeters;
    public long movingMs;

    public Split(int n, double d, long t) {
      number = n;
      meters = d;
      movingMs = t;
    }
  }

  public static List<Split> splits(List<WorkoutRecord.Point> points) {
    List<Split> out = new ArrayList<>();
    double lastD = 0, bound = MILE;
    long lastT = 0;
    int n = 1;
    for (WorkoutRecord.Point p : points) {
      while (p.meters >= bound && p.meters > lastD) {
        double f = (bound - lastD) / (p.meters - lastD);
        long t = lastT + Math.round((p.movingMs - lastT) * f);
        long used = 0;
        for (Split s : out) used += s.movingMs;
        out.add(new Split(n++, MILE, t - used));
        bound += MILE;
      }
      lastD = p.meters;
      lastT = p.movingMs;
    }
    if (!points.isEmpty()) {
      WorkoutRecord.Point p = points.get(points.size() - 1);
      double done = (n - 1) * MILE;
      long used = 0;
      for (Split s : out) used += s.movingMs;
      if (p.meters - done > 1) out.add(new Split(n, p.meters - done, p.movingMs - used));
    }
    return out;
  }
}

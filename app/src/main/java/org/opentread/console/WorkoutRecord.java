package org.opentread.console;

import java.util.*;

public final class WorkoutRecord {
  public long walkMs, runMs;
  public double walkMeters, runMeters;
  public long id, startMs, endMs, updatedMs, movingMs;
  public String profile = "slot-1", kind = "Walk", reason = "";
  public double meters, elevationM, maxKph;
  public double weightKg, activeKcal;
  public String calorieModel = "";
  public int hrSum, hrCount;
  public final ArrayList<Point> points = new ArrayList<>();

  public static class Point {
    public long atMs, movingMs;
    public int speedRaw, gradeRaw, hr;
    public int gripHr, bluetoothHr;
    public String hrSource;
    public double meters;

    public Point(long at, long t, double d, int s, int g, int h) {
      this(at, t, d, s, g, h, 0, 0, h > 0 ? "legacy" : "none");
    }

    public Point(long at, long t, double d, int s, int g, int h, int grip, int ble, String source) {
      atMs = at;
      movingMs = t;
      meters = d;
      speedRaw = s;
      gradeRaw = g;
      hr = h;
      gripHr = grip;
      bluetoothHr = ble;
      hrSource = source;
    }
  }

  public WorkoutRecord copy() {
    WorkoutRecord r = new WorkoutRecord();
    r.id = id;
    r.startMs = startMs;
    r.endMs = endMs;
    r.updatedMs = updatedMs;
    r.movingMs = movingMs;
    r.profile = profile;
    r.kind = kind;
    r.walkMs = walkMs;
    r.runMs = runMs;
    r.walkMeters = walkMeters;
    r.runMeters = runMeters;
    r.reason = reason;
    r.meters = meters;
    r.elevationM = elevationM;
    r.maxKph = maxKph;
    r.hrSum = hrSum;
    r.hrCount = hrCount;
    r.weightKg = weightKg;
    r.activeKcal = activeKcal;
    r.calorieModel = calorieModel;
    return r;
  }

  public double avgMph() {
    return movingMs <= 0 ? 0 : meters / WorkoutMath.MILE / (movingMs / 3600000.0);
  }
}

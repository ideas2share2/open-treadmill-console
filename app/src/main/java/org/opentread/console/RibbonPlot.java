package org.opentread.console;

import java.util.Arrays;

/** Reused, bounded plot buffers. Time buckets retain first/low/high/last in original order. */
public final class RibbonPlot {
  public static final int MAX_POINTS = 200, BUCKETS = MAX_POINTS / 4;
  public final Series speed = new Series(), incline = new Series();
  public long endMs = 1;

  public static final class Series {
    public final long[] time = new long[MAX_POINTS];
    public final double[] value = new double[MAX_POINTS];
    public final int[] segment = new int[MAX_POINTS];
    public int count;
    public double min = 0, max = 6, current = Double.NaN;
    private final int[] source = new int[MAX_POINTS], candidateSegment = new int[MAX_POINTS];
    private final long[] candidateTime = new long[MAX_POINTS];
    private final double[] candidateValue = new double[MAX_POINTS];
    private final int[] order = new int[4];
    private int gap;

    void reset() {
      count = gap = 0;
      min = 0;
      max = 6;
      current = Double.NaN;
      Arrays.fill(source, -1);
    }

    void missing() {
      gap++;
      current = Double.NaN;
    }

    void put(int at, int index, long t, double v) {
      source[at] = index;
      candidateTime[at] = t;
      candidateValue[at] = v;
      candidateSegment[at] = gap;
    }

    void offer(int bucket, int index, long t, double v) {
      current = v;
      int at = bucket * 4;
      if (source[at] < 0) {
        for (int j = 0; j < 4; j++) put(at + j, index, t, v);
      } else {
        if (v < candidateValue[at + 1]) put(at + 1, index, t, v);
        if (v > candidateValue[at + 2]) put(at + 2, index, t, v);
        put(at + 3, index, t, v);
      }
    }

    void finish() {
      for (int bucket = 0; bucket < BUCKETS; bucket++) {
        int at = bucket * 4;
        if (source[at] < 0) continue;
        for (int j = 0; j < 4; j++) order[j] = at + j;
        // Four entries, reused scratch storage; order by source position, not by value.
        for (int j = 1; j < 4; j++) {
          int slot = order[j], k = j - 1;
          while (k >= 0 && source[order[k]] > source[slot]) {
            order[k + 1] = order[k];
            k--;
          }
          order[k + 1] = slot;
        }
        int previous = -1;
        for (int slot : order) {
          if (source[slot] == previous) continue;
          previous = source[slot];
          time[count] = candidateTime[slot];
          value[count] = candidateValue[slot];
          segment[count] = candidateSegment[slot];
          count++;
        }
      }
    }

    public double fraction(double v) {
      if (!Double.isFinite(v)) return 0;
      return Math.max(0, Math.min(1, (v - min) / (max - min)));
    }
  }

  public void update(WorkoutRecord record) {
    speed.reset();
    incline.reset();
    endMs = record == null ? 1 : Math.max(1, record.movingMs);
    if (record == null) return;
    long previous = -1;
    for (int i = 0; i < record.points.size(); i++) {
      WorkoutRecord.Point pt = record.points.get(i);
      long t = pt.movingMs;
      if (t < 0 || t < previous || t > endMs) {
        speed.missing();
        incline.missing();
        continue;
      }
      previous = t;
      int bucket = Math.min(BUCKETS - 1, (int) ((double) t / endMs * BUCKETS));
      if (pt.speedRaw >= 80 && pt.speedRaw <= 1931) {
        double mph = WorkoutMath.mph(pt.speedRaw);
        // Raw km/h increments can represent a nominal 6mph as 6.0025mph.
        speed.max = Math.min(12, Math.max(speed.max, Math.ceil((mph - .01) / 2) * 2));
        speed.offer(bucket, i, t, mph);
      } else speed.missing();
      if (pt.gradeRaw >= -300 && pt.gradeRaw <= 1500) {
        double grade = pt.gradeRaw / 100.0;
        if (grade < 0) incline.min = -3;
        incline.max = Math.min(15, Math.max(incline.max, Math.ceil(grade / 2) * 2));
        incline.offer(bucket, i, t, grade);
      } else incline.missing();
    }
    speed.finish();
    incline.finish();
  }
}

package org.opentread.console;

import java.time.*;
import java.util.*;

public final class Stats {
  public int count;
  public long moving;
  public double meters, walk, run;

  public static Stats of(List<WorkoutRecord> rows, String profile, LocalDate from, LocalDate to) {
    Stats s = new Stats();
    for (WorkoutRecord r : rows) {
      LocalDate d = WorkoutMath.date(r.startMs);
      if (r.movingMs > 0 && r.profile.equals(profile) && !d.isBefore(from) && d.isBefore(to)) {
        s.count++;
        s.moving += r.movingMs;
        s.meters += r.meters;
        s.run += r.runMeters;
        s.walk += r.walkMeters;
      }
    }
    return s;
  }

  public String small() {
    return String.format(Locale.US, "%.2f mi · %d workouts", meters / WorkoutMath.MILE, count);
  }
}

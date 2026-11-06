package org.opentread.console;

import android.content.*;
import java.util.*;

/** Main-thread accumulator, 1Hz samples, batched asynchronous SQLite writes. No media metadata. */
public final class WorkoutRecorder {
  public final WorkoutStore store;
  public String profile;
  public WorkoutRecord current, last;
  private WorkoutMath.Totals totals;
  private final ArrayList<WorkoutRecord.Point> pending = new ArrayList<>();
  private long lastPoint = 0, lastFlush = 0;
  private boolean finishRequested = false;
  private boolean dirty;
  private int previousMode = -1;
  private final SharedPreferences prefs;
  private final ProfileSettings profiles;

  public WorkoutRecorder(Context c) {
    prefs = c.getSharedPreferences("console-profiles", 0);
    profiles = new ProfileSettings(c);
    profile = prefs.getString("profile", "slot-1");
    if (ProfileData.index(profile) < 0) profile = "slot-1";
    store = new WorkoutStore(c);
    store.recover(
        profile,
        recovered -> {
          if (current == null
              && last == null
              && recovered != null
              && profile.equals(recovered.profile)) last = recovered;
        });
  }

  public boolean hasActive() {
    return current != null;
  }

  public WorkoutRecord shown() {
    return current != null ? current : last != null && last.profile.equals(profile) ? last : null;
  }

  public void setProfile(String p) {
    if (ProfileData.index(p) < 0) throw new IllegalArgumentException();
    profile = p;
    last = null;
    prefs.edit().putString("profile", p).apply();
  }

  public String liveKind() {
    return totals == null ? "Walk / Run" : totals.activity.label();
  }

  public boolean showingRecovered() {
    return current == null && shown() != null && "Interrupted".equals(shown().reason);
  }

  public void requestFinish() {
    finishRequested = true;
  }

  public void observe(long wall, long mono, int mode, int speed, int grade, int hr, boolean fresh) {
    observe(wall, mono, mode, speed, grade, new HeartReading(0, hr), fresh);
  }

  public void observe(
      long wall, long mono, int mode, int speed, int grade, HeartReading reading, boolean fresh) {
    int hr = reading.bpm;
    if (!fresh) {
      if (totals != null) totals.gap();
      flush(wall, mono, false);
      return;
    }
    if (current == null && mode == 2 && speed > 5) {
      current = new WorkoutRecord();
      current.id = wall;
      current.startMs = wall;
      current.updatedMs = wall;
      current.profile = profile;
      current.weightKg = profiles.get(profile).calorieWeightKg();
      current.calorieModel = current.weightKg > 0 ? Calories.MODEL : "";
      current.kind = "Walk";
      totals = new WorkoutMath.Totals();
      lastPoint = lastFlush = 0;
      finishRequested = false;
      dirty = true;
      previousMode = -1;
    }
    if (current == null) return;
    long before = current.movingMs;
    boolean justPaused = previousMode == 2 && mode != 2;
    totals.sample(mono, mode, speed, grade);
    current.movingMs = totals.movingMs;
    current.meters = totals.meters;
    current.activeKcal = current.weightKg * totals.activeKcalPerKg;
    current.elevationM = totals.elevationM;
    current.maxKph = totals.maxKph;
    current.walkMs = totals.walkMs;
    current.runMs = totals.runMs;
    current.walkMeters = totals.walkMeters;
    current.runMeters = totals.runMeters;
    current.kind = totals.runMs > totals.walkMs ? "Run" : "Walk";
    if (current.movingMs != before || previousMode != mode) {
      current.updatedMs = wall;
      dirty = true;
    }
    previousMode = mode;
    if (mode == 2 && speed > 5 && mono - lastPoint >= 1000) {
      WorkoutRecord.Point p =
          new WorkoutRecord.Point(
              wall,
              current.movingMs,
              current.meters,
              speed,
              grade,
              hr,
              reading.handles,
              reading.bluetooth,
              reading.source);
      current.points.add(p);
      pending.add(p);
      current.updatedMs = wall;
      dirty = true;
      if (hr > 0) {
        current.hrSum += hr;
        current.hrCount++;
      }
      lastPoint = mono;
    }
    if (mode == 8 || (finishRequested && (mode == 1 || mode == 3) && speed <= 5)) {
      finish(wall, mode == 8 ? "Safety key removed" : "Finished");
      return;
    }
    flush(wall, mono, justPaused);
  }

  void flush(long wall, long mono, boolean force) {
    if (current != null && dirty && (force || mono - lastFlush >= 5000)) {
      store.save(current, pending);
      pending.clear();
      dirty = false;
      lastFlush = mono;
    }
  }

  public void finish(long wall, String reason) {
    if (current == null) return;
    if (!current.points.isEmpty()) {
      WorkoutRecord.Point previous = current.points.get(current.points.size() - 1);
      if (previous.movingMs < current.movingMs) {
        WorkoutRecord.Point lastPoint =
            new WorkoutRecord.Point(
                wall, current.movingMs, current.meters, 0, previous.gradeRaw, 0);
        current.points.add(lastPoint);
        pending.add(lastPoint);
      }
    }
    current.endMs = wall;
    current.updatedMs = wall;
    current.reason = reason;
    store.save(current, pending);
    pending.clear();
    last = current;
    current = null;
    totals = null;
    finishRequested = false;
    dirty = false;
    previousMode = -1;
  }

  public void flushNow(long wall, long mono) {
    flush(wall, mono, true);
  }
}

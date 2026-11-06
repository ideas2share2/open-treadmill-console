package org.opentread.console;

import android.content.Context;

/** Real production recorder with an in-memory storage sink and simulated monotonic time. */
public final class RecorderHostTest {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    Context profileContext = new Context();
    ProfileSettings settings = new ProfileSettings(profileContext);
    settings.save(new ProfileData("slot-1", "Rider", 40, "Male", 200, 70));
    check(
        settings.get("slot-1").name.equals("Rider") && settings.get("slot-1").weightLb == 200,
        "edited profile not persisted by stable ID");
    try {
      settings.save(new ProfileData("slot-4", "Profile 2", 30, "Female", 150, 65));
      throw new AssertionError("duplicate display name accepted");
    } catch (IllegalArgumentException expected) {
    }
    WorkoutRecorder calorieRecorder = new WorkoutRecorder(profileContext);
    for (long t = 100000; t <= 160000; t += 1000)
      calorieRecorder.observe(t, t, 2, 483, 300, 0, true);
    double weight = calorieRecorder.current.weightKg;
    double calories = calorieRecorder.current.activeKcal;
    check(
        Math.abs(calories - weight * Calories.perKgPerMinute(483, 300)) < .00001,
        "recorder calorie integration");
    settings.save(new ProfileData("slot-1", "Renamed", 41, "Male", 100, 70));
    calorieRecorder.observe(161000, 161000, 2, 483, 300, 0, true);
    check(
        calorieRecorder.current.profile.equals("slot-1")
            && calorieRecorder.current.weightKg == weight
            && calorieRecorder.current.activeKcal > calories,
        "profile edit rewrote workout weight/identity");
    calorieRecorder.finish(162000, "Finished");
    check(
        calorieRecorder.store.latest.calorieModel.equals(Calories.MODEL)
            && calorieRecorder.store.latest.weightKg == weight,
        "save lost calorie model/weight");
    calorieRecorder.observe(163000, 163000, 2, 483, 0, 0, true);
    check(
        Math.abs(calorieRecorder.current.weightKg - weight / 2) < .00001,
        "next workout did not use new weight");
    calorieRecorder.finish(164000, "Finished");
    settings.save(new ProfileData("slot-4", "Fourth", 30, "", 160, 65));
    calorieRecorder.setProfile("slot-4");
    calorieRecorder.observe(165000, 165000, 2, 483, 0, 0, true);
    check(
        calorieRecorder.current.profile.equals("slot-4") && calorieRecorder.current.weightKg > 0,
        "fourth profile did not record");
    System.out.println(
        "PASS: profile persistence/duplicate names, stable renamed IDs, per-workout calorie"
            + " weight/model snapshots, next-workout edits and fourth-profile recording.");
    WorkoutRecorder r = new WorkoutRecorder(new Context());
    long base = 100000;
    for (long t = base; t <= base + 90000; t += 200) r.observe(t, t, 2, 160, 0, 120, true);
    check(r.current.movingMs == 90000, "moving time");
    r.observe(base + 90200, base + 90200, 3, 0, 0, 0, true);
    int saves = r.store.saves, points = r.store.points;
    long savedAt = r.current.updatedMs;
    double meters = r.current.meters;
    for (long t = base + 90400; t < base + 86400000; t += 200) r.observe(t, t, 3, 0, 0, 0, true);
    check(
        r.store.saves == saves && r.store.points == points,
        "paused day generated duplicate writes");
    check(
        r.current.updatedMs == savedAt && r.current.meters == meters,
        "idle inflated duration/distance");
    for (int i = 0; i < 100; i++) r.flushNow(base + 86400000, base + 86400000);
    check(r.store.saves == saves, "repeated background flush duplicated writes");
    r.finish(base + 86400000, "Saved after inactivity");
    check(r.store.saves == saves + 1 && !r.hasActive(), "idle finish not exactly one final save");
    check(r.last.movingMs == 90000, "idle finish counted pause");

    r = new WorkoutRecorder(new Context());
    for (long t = base; t <= base + 8L * 3600000; t += 1000)
      r.observe(t, t, 2, 644, 100, 130, true);
    check(r.store.saves <= 5761, "eight hours saved every sample rather than five-second batches");
    check(r.current.points.size() <= 28801, "unexpected point growth");
    check(r.current.movingMs == 8L * 3600000, "long workout timing drift");
    r.observe(base + 8L * 3600000 + 1000, base + 8L * 3600000 + 1000, 8, 0, 0, 0, true);
    check(!r.hasActive() && r.store.latest.endMs > 0, "key out did not finalize");

    r = new WorkoutRecorder(new Context());
    for (long t = base; t <= base + 10000; t += 200) r.observe(t, t, 2, 160, 0, 0, true);
    saves = r.store.saves;
    for (long t = base + 10200; t < base + 86400000; t += 200) r.observe(t, t, -1, 0, 0, 0, false);
    check(r.store.saves <= saves + 1, "disconnected day repeatedly saved stale data");

    WorkoutRecord saved = new WorkoutRecord();
    saved.profile = "slot-1";
    saved.reason = "Interrupted";
    saved.movingMs = 600000;
    saved.endMs = 100000;
    WorkoutStore.recovery = saved;
    r = new WorkoutRecorder(new Context());
    check(
        r.showingRecovered() && r.shown().movingMs == 600000 && !r.hasActive(),
        "recovery adopted active workout");
    r.observe(base, base, 2, 160, 0, 0, true);
    check(r.current != saved && r.current.movingMs == 0, "new Start resumed old timers");
    WorkoutStore.recovery = null;
    r = new WorkoutRecorder(new Context());
    r.observe(base, base, 2, 160, 0, new HeartReading(94, 108), true);
    r.observe(base + 1000, base + 1000, 2, 160, 0, new HeartReading(95, 0), true);
    r.observe(base + 2000, base + 2000, 2, 160, 0, HeartReading.NONE, true);
    WorkoutRecord.Point both = r.current.points.get(0),
        handles = r.current.points.get(1),
        missing = r.current.points.get(2);
    check(
        both.hr == 108
            && both.gripHr == 94
            && both.bluetoothHr == 108
            && both.hrSource.equals("bluetooth"),
        "both-source timestamp sample");
    check(
        handles.hr == 95 && handles.hrSource.equals("handles"),
        "contact fallback timestamp sample");
    check(
        missing.hr == 0 && missing.hrSource.equals("none") && r.current.hrCount == 2,
        "missing contact fabricated aggregate");
    System.out.println(
        "PASS: production recorder with simulated24h paused/disconnected idle: no duplicate saves,"
            + " samples or inflated totals; one idle-finish save; eight-hour running batch/memory"
            + " bounds; key-out and interrupted-summary recovery.");
  }
}

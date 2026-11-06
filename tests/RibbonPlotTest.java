import org.opentread.console.*;

public final class RibbonPlotTest {
  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  static int raw(double mph) {
    return (int) Math.round(mph * 160.9344);
  }

  static void point(WorkoutRecord r, long t, double mph, int grade) {
    r.points.add(new WorkoutRecord.Point(t, t, 0, raw(mph), grade, 0));
    r.movingMs = Math.max(r.movingMs, t);
  }

  static boolean has(RibbonPlot.Series s, long time, double value) {
    for (int i = 0; i < s.count; i++)
      if (s.time[i] == time && Math.abs(s.value[i] - value) < .001) return true;
    return false;
  }

  static void bounded(RibbonPlot.Series s, long end) {
    check(s.count <= 200, "unbounded chart path");
    long previous = -1;
    for (int i = 0; i < s.count; i++) {
      check(s.time[i] >= previous && s.time[i] <= end, "time order/range");
      previous = s.time[i];
      check(
          Double.isFinite(s.value[i]) && s.fraction(s.value[i]) >= 0 && s.fraction(s.value[i]) <= 1,
          "nonfinite/outside scale");
    }
  }

  public static void run() {
    RibbonPlot model = new RibbonPlot();
    model.update(null);
    check(model.speed.count == 0 && model.speed.max == 6, "empty chart");
    WorkoutRecord walk = new WorkoutRecord();
    point(walk, 0, .5, 0);
    point(walk, 1000, 3, 300);
    point(walk, 2000, 5, 500);
    model.update(walk);
    check(model.speed.max == 6, "startup stretched walking scale");
    check(
        Math.abs(model.speed.fraction(5) - model.speed.fraction(3) - 1.0 / 3) < 1e-9,
        "3-to5mph change must occupy a third of default plot height");
    check(model.incline.min == 0 && model.incline.max == 6, "walking incline scale");
    point(walk, 3000, 6, 0);
    model.update(walk);
    check(model.speed.max == 6, "nominal6mph rounding expanded scale");
    point(walk, 4000, 6.1, 700);
    model.update(walk);
    check(model.speed.max == 8 && model.incline.max == 8, "range failed to expand");
    point(walk, 5000, 10, -300);
    point(walk, 6000, 12, 1500);
    point(walk, 7000, 3, 300);
    model.update(walk);
    check(model.speed.max == 12, "12mph not supported");
    check(model.incline.min == -3 && model.incline.max == 15, "full grade range");
    bounded(model.speed, walk.movingMs);
    bounded(model.incline, walk.movingMs);
    point(walk, 8000, 1, 0);
    model.update(walk);
    check(model.speed.max == 12 && model.incline.max == 15, "axis shrank after interval");

    WorkoutRecord longWalk = new WorkoutRecord();
    for (int i = 0; i <= 1800; i++)
      point(longWalk, i * 1000L, i == 917 ? 5 : 3, i == 937 ? 550 : 300);
    model.update(longWalk);
    check(has(model.speed, 917000, WorkoutMath.mph(raw(5))), "brief peak lost in30min reduction");
    check(has(model.incline, 937000, 5.5), "brief incline lost in30min reduction");
    check(
        model.speed.time[0] == 0 && model.speed.time[model.speed.count - 1] == 1800000,
        "first/final sample lost");
    bounded(model.speed, longWalk.movingMs);
    bounded(model.incline, longWalk.movingMs);

    WorkoutRecord gaps = new WorkoutRecord();
    for (int i = 0; i <= 10000; i++) point(gaps, i * 1000L, i == 13 ? 0 : 3, 0);
    model.update(gaps);
    boolean broken = false;
    for (int i = 1; i < model.speed.count; i++)
      if (model.speed.segment[i] != model.speed.segment[i - 1]) broken = true;
    check(broken, "downsampling bridged missing/paused speed");
    bounded(model.speed, gaps.movingMs);
    point(gaps, 10001000, 5000, 20000);
    model.update(gaps);
    check(model.speed.max == 6 && model.incline.max == 6, "invalid data distorted scale");
    check(
        Double.isNaN(model.speed.current) && Double.isNaN(model.incline.current),
        "invalid current shown");

    WorkoutRecord reset = new WorkoutRecord();
    point(reset, 0, 3, 0);
    point(reset, 1000, 3, 0);
    model.update(reset);
    check(
        model.speed.max == 6 && model.incline.min == 0 && model.incline.max == 6,
        "new session inherited axes");
    bounded(model.speed, reset.movingMs);
    System.out.println(
        "PASS: compact mph/grade axes, warmup isolation,6mph rounding, full12mph/-3..15%, no"
            + " within-workout shrink,30min extrema, gaps, invalid samples, new-session reset"
            + " and200point buffers.");
  }
}

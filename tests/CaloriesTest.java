import org.opentread.console.*;

public final class CaloriesTest {
  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  static void near(double a, double b) {
    check(Math.abs(a - b) < .00001, a + " != " + b);
  }

  public static void run() {
    near(Calories.perKgPerMinute(483, 0), 80.5 * .1 / 200);
    near(Calories.perKgPerMinute(483, 300), (80.5 * .1 + 80.5 * .03 * 1.8) / 200);
    near(Calories.perKgPerMinute(1609, 0), (1609.0 / 6) * .2 / 200);
    near(Calories.perKgPerMinute(483, -300), Calories.perKgPerMinute(483, 0));
    for (int grade : new int[] {-300, 0, 300, 1500}) {
      double previous = 0;
      for (int speed = 80; speed <= 1931; speed++) {
        double kcal = Calories.perKgPerMinute(speed, grade);
        check(
            Double.isFinite(kcal) && kcal > 0 && kcal >= previous,
            "nonfinite/nonmonotonic estimate");
        if (previous > 0) check(kcal / previous < 1.03, "rate jump at walk/jog transition");
        previous = kcal;
      }
    }
    for (double raw : new double[] {0, 5, -1, 1932, Double.NaN, Double.POSITIVE_INFINITY})
      near(Calories.perKgPerMinute(raw, 0), 0);
    near(Calories.perKgPerMinute(483, 1501), 0);
    WorkoutMath.Totals t = new WorkoutMath.Totals();
    for (long ms = 0; ms <= 60000; ms += 1000) t.sample(ms, 2, 483, 300);
    near(t.activeKcalPerKg, Calories.perKgPerMinute(483, 300));
    double before = t.activeKcalPerKg;
    for (long ms = 61000; ms < 86400000; ms += 1000) t.sample(ms, 3, 0, 300);
    near(t.activeKcalPerKg, before);
    t.sample(86400000, 2, 483, 300);
    t.gap();
    t.sample(86401000, 2, 483, 300);
    near(t.activeKcalPerKg, before);
    t.sample(86411000, 2, 483, 300);
    near(t.activeKcalPerKg, before);
    ProfileData a = new ProfileData("slot-1", "Renamed Rider", 40, "Male", 200, 70);
    near(a.calorieWeightKg(), 90.718474);
    check(
        a.id.equals("slot-1") && ProfileTheme.accent(a.id) == ProfileTheme.FIRST,
        "rename changes identity/theme");
    check(
        new ProfileData("slot-4", "Fourth", 30, "", 0, 0).calorieWeightKg() == 0,
        "missing weight fabricated");
    check(
        new ProfileData("slot-4", "Child", 12, "", 90, 60).calorieWeightKg() == 0,
        "adult formula used for child");
    check(ProfileTheme.accent("slot-4") == ProfileTheme.FOURTH, "fourth slot accent");
    for (double invalid : new double[] {Double.NaN, -1, Double.POSITIVE_INFINITY, 10, 700}) {
      try {
        new ProfileData("slot-1", "slot-1", 40, "", invalid, 70);
        throw new AssertionError("invalid weight accepted");
      } catch (IllegalArgumentException expected) {
      }
    }
    WorkoutRecord r = new WorkoutRecord();
    check(Calories.label(r).equals("—"), "legacy calories fabricated");
    r.calorieModel = Calories.MODEL;
    r.weightKg = 80;
    r.activeKcal = 123.4;
    check(Calories.label(r).equals("123 kcal"), "calorie label");
    WorkoutRecord copy = r.copy();
    near(copy.activeKcal, r.activeKcal);
    near(copy.weightKg, r.weightKg);
    check(copy.calorieModel.equals(r.calorieModel), "model lost in snapshot");
    System.out.println(
        "PASS: ACSM active-energy reference values, full speed/grade bounds, continuous transition,"
            + " decline approximation, pause/gap/day inactivity, missing weight/child gating,"
            + " stable profile identity and calorie snapshots.");
  }
}

package org.opentread.console;

/** ACSM speed/grade estimate, excluding the standard 3.5ml/kg/min resting component. */
public final class Calories {
  public static final String MODEL = "acsm-active-v1";

  public static double perKgPerMinute(double speedRaw, double gradeRaw) {
    if (!Double.isFinite(speedRaw)
        || !Double.isFinite(gradeRaw)
        || speedRaw <= 5
        || speedRaw > 1931
        || gradeRaw < -300
        || gradeRaw > 1500) return 0;
    double speed = speedRaw / 6.0; // hundredths km/h -> m/min
    double grade = Math.max(0, gradeRaw) / 10000.0;
    double walk = .1 * speed + 1.8 * speed * grade;
    double run = .2 * speed + .9 * speed * grade;
    // The walk/jog transition is uncertain. Blend instead of a calorie-rate jump at one speed.
    double blend = Math.max(0, Math.min(1, (speedRaw / 160.9344 - 3.7) / 1.3));
    return (walk + (run - walk) * blend) / 200.0;
  }

  public static String label(WorkoutRecord r) {
    return r != null
            && MODEL.equals(r.calorieModel)
            && r.weightKg > 0
            && Double.isFinite(r.activeKcal)
            && r.activeKcal >= 0
        ? Math.round(r.activeKcal) + " kcal"
        : "—";
  }
}

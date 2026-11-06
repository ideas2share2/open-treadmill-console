package org.opentread.console;

public final class TrackProgress {
  public static final double LAP_METERS = WorkoutMath.MILE / 4;

  public static double safe(double meters) {
    return Double.isFinite(meters) && meters > 0 ? meters : 0;
  }

  public static int laps(double meters) {
    return (int) Math.floor(safe(meters) / LAP_METERS + 1e-9);
  }

  public static float fraction(double meters) {
    double lap = safe(meters) / LAP_METERS;
    double f = lap - Math.floor(lap);
    return f < 1e-9 || 1 - f < 1e-9 ? 0 : (float) f;
  }
}

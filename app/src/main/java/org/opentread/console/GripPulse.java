package org.opentread.console;

/**
 * Lightweight EMA for controller contact sensors; invalid/no-contact resets instead of lingering.
 */
public final class GripPulse {
  private double average;

  public int sample(int raw) {
    if (!HeartReading.valid(raw)) {
      average = 0;
      return 0;
    }
    average = average == 0 ? raw : average + .3 * (raw - average);
    return (int) Math.round(average);
  }

  public void clear() {
    average = 0;
  }
}

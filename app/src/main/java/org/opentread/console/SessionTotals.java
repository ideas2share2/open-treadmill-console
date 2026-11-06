package org.opentread.console;

public final class SessionTotals {
  public long movingMs = 0;
  public double meters = 0;
  private long previous = -1;
  private int previousMode = -1, previousSpeed = 0;

  public synchronized void sample(long now, int mode, int speed) {
    long dt = now - previous;
    if (previous >= 0 && dt > 0 && dt <= 1000 && previousMode == 2 && mode == 2) {
      double avg = (previousSpeed + speed) / 2.0;
      if (avg > 5) {
        movingMs += dt;
        meters += (avg / 100.0 / 3.6) * (dt / 1000.0);
      }
    }
    previous = now;
    previousMode = mode;
    previousSpeed = speed;
  }

  public synchronized void gap() {
    previous = -1;
  }

  public synchronized void reset() {
    movingMs = 0;
    meters = 0;
    gap();
  }
}

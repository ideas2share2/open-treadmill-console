package org.opentread.console;

/** A practical speed-based label, not gait recognition. No hardware commands. */
public final class ActivityType {
  public static final int RUN_SPEED_RAW = 644; // 4mph, nearest 0.01km/h
  public static final long SETTLE_MS = 3000;
  private boolean running = false, candidate = false;
  private long since = -1, last = -1;

  public boolean running() {
    return running;
  }

  public String label() {
    return running ? "Run" : "Walk";
  }

  public void sample(long mono, int mode, int speed) {
    if (mode != 2 || speed <= 5) {
      gap();
      return;
    }
    if (last >= 0 && (mono <= last || mono - last > 1500)) since = -1;
    last = mono;
    boolean next = speed >= RUN_SPEED_RAW;
    if (next == running) {
      since = -1;
      return;
    }
    if (since < 0 || candidate != next) {
      candidate = next;
      since = mono;
    }
    if (mono - since >= SETTLE_MS) {
      running = next;
      since = -1;
    }
  }

  public void gap() {
    last = -1;
    since = -1;
  }
}

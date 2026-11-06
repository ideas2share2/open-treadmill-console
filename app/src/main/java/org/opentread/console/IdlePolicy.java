package org.opentread.console;

/** Stopped-only housekeeping. No touch is required during a running workout. No hardware API. */
public final class IdlePolicy {
  public static final long IDLE_MS = 5 * 60000;
  private long stoppedSince = -1, lastTouch;
  private boolean parked;

  public void touch(long now) {
    lastTouch = now;
    parked = false;
  }

  public boolean update(long now, boolean moving, boolean safelyStopped) {
    if (moving || !safelyStopped) {
      stoppedSince = -1;
      parked = false;
      return false;
    }
    if (stoppedSince < 0 || now < stoppedSince) stoppedSince = now;
    parked = now - Math.max(stoppedSince, lastTouch) >= IDLE_MS;
    return parked;
  }

  public boolean parked() {
    return parked;
  }
}

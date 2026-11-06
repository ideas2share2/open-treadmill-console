package org.opentread.console;

/**
 * Deliberately simple family video gate. It never gates treadmill controls. Not a security
 * boundary.
 */
public final class VideoGate {
  private boolean allowed;

  public boolean unlocked() {
    return allowed;
  }

  public boolean submit(String pin) {
    if ("2468".equals(pin)) {
      allowed = true;
      return true;
    }
    return false;
  }

  public void lock() {
    allowed = false;
  }
}

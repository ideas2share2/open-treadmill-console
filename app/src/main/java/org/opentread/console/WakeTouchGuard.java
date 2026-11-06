package org.opentread.console;

/** Swallow the complete wake gesture so it cannot become a control/button press underneath. */
public final class WakeTouchGuard {
  private boolean waking;

  public boolean consume(boolean sleeping, boolean down, boolean finished) {
    if (sleeping && down) waking = true;
    boolean consumed = waking;
    if (finished) waking = false;
    return consumed;
  }
}

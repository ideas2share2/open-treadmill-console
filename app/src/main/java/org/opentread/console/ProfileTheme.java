package org.opentread.console;

public final class ProfileTheme {
  public static final int FIRST = 0xffc8ff30,
      SECOND = 0xffff74b8,
      GUEST = 0xff62bdff,
      FOURTH = 0xffc7a0ff;

  public static int accent(String profile) {
    return "slot-2".equals(profile)
        ? SECOND
        : "guest".equals(profile) ? GUEST : "slot-4".equals(profile) ? FOURTH : FIRST;
  }
}

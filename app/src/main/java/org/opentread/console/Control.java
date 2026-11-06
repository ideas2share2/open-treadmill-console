package org.opentread.console;

/** Bounded local controls. Start always 1 mph; Stop is Pause, never zero-speed. */
public final class Control {
  public static int[][] startSteps() {
    return new int[][] {{95, 0}, {12, 2}, {0, 160}};
  }

  public static boolean canStart(int mode, int speed, long age, boolean foreground) {
    return foreground
        && age >= 0
        && age < 1000
        && (mode == 1 || mode == 3)
        && speed >= 0
        && speed <= 5;
  }

  public static boolean canAdjust(int mode, long age, boolean visible, boolean stopping) {
    return visible && !stopping && age >= 0 && age < 1500 && (mode == 1 || mode == 2 || mode == 3);
  }

  public static boolean needsPause(int mode, int speed) {
    return mode != 8 && (speed > 5 || (mode != 1 && mode != 3));
  }

  public static byte[] write(int field, int value) {
    int size;
    if (field == 0 && value >= 80 && value <= 1931) size = 2;
    else if (field == 1 && value >= -300 && value <= 1500) size = 2;
    else if (field == 98 && value >= 0 && value <= 4) size = 1;
    else if (field == 12 && (value == 2 || value == 3)) size = 1;
    else if (field == 95 && value == 0) size = 1;
    else throw new IllegalArgumentException("Command not allowed");
    int sections = field / 8 + 1;
    byte[] p = new byte[4 + 1 + sections + size + 4];
    p[0] = 2;
    p[1] = (byte) p.length;
    p[2] = 2;
    p[3] = (byte) sections;
    p[4 + field / 8] = (byte) (1 << (field % 8));
    int at = 4 + sections;
    p[at++] = (byte) value;
    if (size == 2) p[at++] = (byte) (value >> 8);
    p[at++] = 3;
    p[at++] = 0;
    p[at++] = 0;
    p[at] = 1;
    int sum = 0;
    for (int i = 0; i < p.length - 1; i++) sum += p[i] & 255;
    p[p.length - 1] = (byte) sum;
    return p;
  }
}

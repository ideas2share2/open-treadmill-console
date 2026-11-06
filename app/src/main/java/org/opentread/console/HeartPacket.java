package org.opentread.console;

/** Standard Bluetooth Heart Rate Measurement; no estimates. */
public final class HeartPacket {
  public static int decode(byte[] p) {
    if (p == null || p.length < 2) return 0;
    int f = p[0] & 255;
    if ((f & 4) != 0 && (f & 2) == 0) return 0;
    int n;
    if ((f & 1) != 0) {
      if (p.length < 3) return 0;
      n = (p[1] & 255) | ((p[2] & 255) << 8);
    } else n = p[1] & 255;
    return n >= 20 && n <= 250 ? n : 0;
  }
}

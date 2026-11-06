package org.opentread.console;

import java.util.Arrays;

/** Read-only FitPro requests. No API accepts a write value or motor command. */
public final class Protocol {
  public static final class Rejected extends IllegalArgumentException {
    public final int status;

    Rejected(int s) {
      super("controller status " + s);
      status = s;
    }
  }

  public static final class ShapeMismatch extends IllegalArgumentException {
    ShapeMismatch(int expected, int got) {
      super("expected " + expected + " data bytes, received " + got);
    }
  }

  public static byte[] readField(int field) {
    if (field < 0 || field > 127) throw new IllegalArgumentException("field");
    int sections = field / 8 + 1;
    byte[] p = new byte[6 + sections];
    p[0] = 2;
    p[1] = (byte) p.length;
    p[2] = 2;
    p[3] = 0;
    p[4] = (byte) sections;
    p[5 + field / 8] = (byte) (1 << (field % 8));
    int sum = 0;
    for (int i = 0; i < p.length - 1; i++) sum += p[i] & 255;
    p[p.length - 1] = (byte) sum;
    return p;
  }

  public static byte[] payload(byte[] raw, int size, int count) {
    if (raw == null || count < 5 || count > raw.length || count > 64)
      throw new IllegalArgumentException("invalid packet size");
    int n = raw[1] & 255;
    if (n < 5 || n > count || n > 64) throw new IllegalArgumentException("invalid length " + n);
    int sum = 0;
    for (int i = 0; i < n - 1; i++) sum += raw[i] & 255;
    if ((sum & 255) != (raw[n - 1] & 255)) throw new IllegalArgumentException("checksum mismatch");
    if (raw[0] != 4 || raw[2] != 2) throw new IllegalArgumentException("unexpected reply");
    int status = raw[3] & 255;
    if (status == 8) throw new ControllerAuth.Required();
    if (status != 2) throw new Rejected(status);
    if (n != size + 5) throw new ShapeMismatch(size, n - 5);
    return Arrays.copyOfRange(raw, 4, n - 1);
  }

  public static long unsigned(byte[] p) {
    long v = 0;
    for (int i = 0; i < p.length; i++) v |= (p[i] & 255L) << (8 * i);
    return v;
  }

  public static String hex(byte[] p, int n) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < n; i++) b.append(String.format("%02x", p[i] & 255));
    return b.toString();
  }
}

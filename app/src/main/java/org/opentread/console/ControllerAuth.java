// Interoperability portions adapted from Hyperborea (MIT); see NOTICE.md and bundled license.
package org.opentread.console;

import java.util.Arrays;

/**
 * Normal FitPro V1 session verification, derived from the saved interoperability sources. Only
 * metadata reads and VerifySecurity. No mode, speed, grade, calibration or firmware API.
 */
public final class ControllerAuth {
  public interface Wire {
    byte[] exchange(byte[] request) throws Exception;
  }

  public static final class Identity {
    public int software, serial, model, part, library;
    public boolean pulse;
  }

  public static final class Required extends IllegalArgumentException {
    public Required() {
      super("Controller session verification required");
    }
  }

  public static Identity verify(Wire wire) throws Exception {
    byte[] device = payload(wire.exchange(deviceInfo()), 129, 6);
    Identity id = new Identity();
    id.software = device[0] & 255;
    id.serial = u32(device, 2);
    id.pulse = supportsField(device, 10);
    if (id.software <= 75)
      throw new IllegalArgumentException(
          "Unexpected controller software; verification unavailable");
    byte[] supported = payload(wire.exchange(supportedCommands()), 136, 1);
    for (int needed : new int[] {130, 132, 144}) {
      boolean found = false;
      for (byte b : supported) if ((b & 255) == needed) found = true;
      if (!found)
        throw new IllegalArgumentException("Controller does not declare required session command");
    }
    byte[] sys = payload(wire.exchange(systemInfo()), 130, 11);
    id.model = u32(sys, 3);
    id.part = u32(sys, 7);
    byte[] ver = payload(wire.exchange(versionInfo()), 132, 3);
    id.library = ver[0] & 255;
    if (id.model <= 0 || id.part <= 0 || id.library <= 0)
      throw new IllegalArgumentException("Invalid controller identity");
    payload(wire.exchange(verification(id.serial, id.part, id.model, id.library)), 144, 1);
    return id;
  }

  public static boolean supportsField(byte[] device, int field) {
    if (device == null || field < 0 || device.length < 9) return false;
    int sections = device[8] & 255, section = field / 8;
    return sections <= 16
        && section < sections
        && 9 + sections <= device.length
        && (device[9 + section] & (1 << (field % 8))) != 0;
  }

  public static byte[] deviceInfo() {
    return frame(2, 129, new byte[0]);
  }

  public static byte[] supportedCommands() {
    return frame(4, 136, new byte[0]);
  }

  public static byte[] systemInfo() {
    return frame(2, 130, new byte[3]);
  }

  public static byte[] versionInfo() {
    return frame(2, 132, new byte[2]);
  }

  public static byte[] verification(int serial, int part, int model, int library) {
    if (library < 1 || library > 255 || part <= 0 || model <= 0)
      throw new IllegalArgumentException("Invalid verification parameters");
    byte[] data = new byte[36];
    byte[] hash = hash(serial, part, model);
    System.arraycopy(hash, 0, data, 0, 32);
    int secret = library * 8;
    for (int i = 0; i < 4; i++) data[32 + i] = (byte) (secret >>> (8 * i));
    return frame(2, 144, data);
  }

  public static byte[] hash(int serial, int part, int model) {
    if (part == 370357 && model == 39915) part = 374677;
    byte[] h = new byte[32];
    for (int i = 0; i < 32; i++) {
      int v = i + 1;
      if (((serial >>> i) & 1) != 0) {
        int p = i < 16 ? Integer.rotateLeft(part, 16) >>> i : part >>> i;
        v ^= p & 255;
      } else v ^= (v * (i + model)) & 255;
      h[i] = (byte) v;
    }
    return h;
  }

  private static byte[] frame(int device, int cmd, byte[] body) {
    byte[] p = new byte[body.length + 4];
    p[0] = (byte) device;
    p[1] = (byte) p.length;
    p[2] = (byte) cmd;
    System.arraycopy(body, 0, p, 3, body.length);
    int sum = 0;
    for (int i = 0; i < p.length - 1; i++) sum += p[i] & 255;
    p[p.length - 1] = (byte) sum;
    return p;
  }

  public static byte[] payload(byte[] raw, int command, int min) {
    if (raw == null || raw.length < 5) throw new IllegalArgumentException("Short session reply");
    int n = raw[1] & 255;
    if (n < 5 || n > raw.length || n > 64)
      throw new IllegalArgumentException("Invalid session reply length");
    int sum = 0;
    for (int i = 0; i < n - 1; i++) sum += raw[i] & 255;
    if ((sum & 255) != (raw[n - 1] & 255))
      throw new IllegalArgumentException("Session reply checksum mismatch");
    boolean allowedDevice =
        raw[0] == 4 || (raw[0] == 2 && (command == 130 || command == 132 || command == 144));
    if (!allowedDevice || (raw[2] & 255) != command)
      throw new IllegalArgumentException("Unexpected session reply identity/command");
    if ((raw[3] & 255) != 2)
      throw new IllegalArgumentException("Session command rejected: " + (raw[3] & 255));
    if (n - 5 < min) throw new IllegalArgumentException("Incomplete controller identity");
    return Arrays.copyOfRange(raw, 4, n - 1);
  }

  private static int u32(byte[] p, int at) {
    if (at < 0 || at + 4 > p.length) throw new IllegalArgumentException("Short identity field");
    return (p[at] & 255)
        | ((p[at + 1] & 255) << 8)
        | ((p[at + 2] & 255) << 16)
        | ((p[at + 3] & 255) << 24);
  }
}

package org.opentread.console;

/**
 * Source-aware instantaneous reading. Never carries values across missing contact or stale data.
 */
public final class HeartReading {
  public static final HeartReading NONE = new HeartReading(0, 0);
  public final int handles, bluetooth, bpm;
  public final String source;

  public HeartReading(int grip, int ble) {
    handles = valid(grip) ? grip : 0;
    bluetooth = valid(ble) ? ble : 0;
    bpm = bluetooth > 0 ? bluetooth : handles;
    source = bluetooth > 0 ? "bluetooth" : handles > 0 ? "handles" : "none";
  }

  public static boolean valid(int bpm) {
    return bpm >= 30 && bpm <= 230;
  }

  public String label() {
    if (handles > 0 && bluetooth > 0)
      return "Handles " + handles + " bpm\nBluetooth " + bluetooth + " bpm";
    if (bpm > 0) return "♥ " + bpm + " bpm · " + (bluetooth > 0 ? "Bluetooth" : "Handles");
    return "Grip handles /\nconnect Bluetooth";
  }
}

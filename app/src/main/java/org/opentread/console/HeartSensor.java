package org.opentread.console;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Optional standard BLE HR, user initiated, bounded scan. No treadmill commands. */
public final class HeartSensor {
  static final UUID HRS = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
      HR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
      CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
  final Context context;
  final Handler handler = new Handler(Looper.getMainLooper());
  BluetoothGatt gatt;
  BluetoothLeScanner scanner;
  volatile int value;
  volatile long at;
  volatile String status = "Connect sensor";
  boolean scanning = false;
  volatile int generation;
  final Runnable scanTimeout =
      () -> {
        stopScan();
        if (gatt == null) status = "Connect sensor";
      };
  java.util.function.Consumer<BluetoothDevice> found;

  public HeartSensor(Context c) {
    context = c;
  }

  public int bpm() {
    return SystemClock.elapsedRealtime() - at < 5000 ? value : 0;
  }

  void denied() {
    value = 0;
    status = "Sensor permission needed";
  }

  public String name(BluetoothDevice d) {
    try {
      return d.getName();
    } catch (SecurityException e) {
      return null;
    }
  }

  final ScanCallback callback =
      new ScanCallback() {
        public void onScanResult(int t, ScanResult r) {
          handler.post(
              () -> {
                if (scanning && found != null) found.accept(r.getDevice());
              });
        }

        public void onScanFailed(int e) {
          handler.post(() -> stopScan());
          status = "Scan unavailable";
        }
      };

  public void scan(java.util.function.Consumer<BluetoothDevice> receiver) {
    stopScan();
    try {
      BluetoothManager m = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
      BluetoothAdapter a = m == null ? null : m.getAdapter();
      if (a == null || !a.isEnabled()) {
        status = "Turn on Bluetooth";
        return;
      }
      scanner = a.getBluetoothLeScanner();
      if (scanner == null) return;
      found = receiver;
      scanning = true;
      status = "Searching…";
      scanner.startScan(
          Collections.singletonList(
              new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HRS)).build()),
          new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
          callback);
      handler.postDelayed(scanTimeout, 12000);
    } catch (SecurityException e) {
      scanning = false;
      denied();
    }
  }

  public void stopScan() {
    handler.removeCallbacks(scanTimeout);
    if (scanning && scanner != null)
      try {
        scanner.stopScan(callback);
      } catch (SecurityException e) {
        denied();
      }
    scanning = false;
    found = null;
  }

  public void connect(BluetoothDevice d) {
    close();
    int token = generation;
    status = "Connecting…";
    try {
      gatt =
          d.connectGatt(
              context,
              false,
              new BluetoothGattCallback() {
                public void onConnectionStateChange(BluetoothGatt g, int s, int state) {
                  if (token != generation) return;
                  try {
                    if (s == BluetoothGatt.GATT_SUCCESS
                        && state == BluetoothProfile.STATE_CONNECTED) g.discoverServices();
                    else if (state == BluetoothProfile.STATE_DISCONNECTED
                        || s != BluetoothGatt.GATT_SUCCESS) {
                      value = 0;
                      status = "Connect sensor";
                      g.close();
                      handler.post(
                          () -> {
                            if (gatt == g) gatt = null;
                          });
                    }
                  } catch (SecurityException e) {
                    denied();
                  }
                }

                public void onServicesDiscovered(BluetoothGatt g, int s) {
                  if (token != generation) return;
                  try {
                    BluetoothGattService service = g.getService(HRS);
                    BluetoothGattCharacteristic c =
                        service == null ? null : service.getCharacteristic(HR);
                    if (s != BluetoothGatt.GATT_SUCCESS || c == null) {
                      status = "No heart-rate service";
                      g.disconnect();
                      return;
                    }
                    if (!g.setCharacteristicNotification(c, true)) {
                      status = "Sensor notifications unavailable";
                      g.disconnect();
                      return;
                    }
                    BluetoothGattDescriptor desc = c.getDescriptor(CCCD);
                    if (desc != null) {
                      desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                      if (!g.writeDescriptor(desc)) status = "Sensor setup failed";
                    } else {
                      status = "Sensor setup failed";
                      g.disconnect();
                    }
                  } catch (SecurityException e) {
                    denied();
                  }
                }

                public void onDescriptorWrite(
                    BluetoothGatt g, BluetoothGattDescriptor d, int statusCode) {
                  if (token != generation) return;
                  status =
                      statusCode == BluetoothGatt.GATT_SUCCESS
                          ? "Sensor connected"
                          : "Sensor setup failed";
                }

                public void onCharacteristicChanged(
                    BluetoothGatt g, BluetoothGattCharacteristic c) {
                  if (token != generation) return;
                  if (HR.equals(c.getUuid())) {
                    value = HeartPacket.decode(c.getValue());
                    at = SystemClock.elapsedRealtime();
                  }
                }
              },
              BluetoothDevice.TRANSPORT_LE);
    } catch (SecurityException e) {
      denied();
    }
  }

  public void close() {
    generation++;
    handler.removeCallbacksAndMessages(null);
    stopScan();
    if (gatt != null) {
      try {
        gatt.disconnect();
        gatt.close();
      } catch (SecurityException e) {
        denied();
      }
      gatt = null;
    }
    value = 0;
    status = "Connect sensor";
  }
}

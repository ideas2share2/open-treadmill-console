package org.opentread.console;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.util.Log;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Controller from tested manual-v1; owned by foreground service during video. */
public final class ControllerEngine {
  final String TAG = "MediaController", ACTION = "org.opentread.console.USB";
  final Context context;
  final Runnable onChange;
  final AtomicInteger pendingSpeed = new AtomicInteger(-1),
      pendingGrade = new AtomicInteger(Integer.MIN_VALUE),
      pendingFan = new AtomicInteger(-1);

  void clearAdjustments() {
    pendingSpeed.set(-1);
    pendingGrade.set(Integer.MIN_VALUE);
    pendingFan.set(-1);
  }

  boolean canAdjust() {
    return authenticated
        && uiResponsive()
        && active
        && sampleTime > 0
        && Control.canAdjust(
            mode,
            SystemClock.elapsedRealtime() - sampleTime,
            foreground,
            pausing || stopRequest.get());
  }

  public void speedStep(boolean up) {
    if (canAdjust() && mode == 2) {
      int pending = pendingSpeed.get();
      pendingSpeed.set(WorkoutMath.speedStep(pending < 0 ? speedRaw : pending, up));
    }
  }

  public void gradeStep(boolean up) {
    if (canAdjust()) {
      int pending = pendingGrade.get();
      pendingGrade.set(
          WorkoutMath.inclineStep(pending == Integer.MIN_VALUE ? inclineRaw : pending, up));
    }
  }

  public void fanStep() {
    if (canAdjust()) {
      int pending = pendingFan.get();
      pendingFan.set(((pending < 0 ? Math.max(0, fan) : pending) + 1) % 5);
    }
  }

  boolean keyMode() throws Exception {
    int m = read(12, 1);
    return m == 1 || m == 2 || m == 3;
  }

  void adjustNow() throws Exception {
    int s = pendingSpeed.getAndSet(-1),
        g = pendingGrade.getAndSet(Integer.MIN_VALUE),
        f = pendingFan.getAndSet(-1);
    if (s < 0 && g == Integer.MIN_VALUE && f < 0) return;
    snapshot();
    if (!canAdjust()) {
      clearAdjustments();
      return;
    }
    if (s >= 0 && mode == 2 && canAdjust() && read(12, 1) == 2 && canAdjust()) write(0, s);
    if (g != Integer.MIN_VALUE && canAdjust() && keyMode() && canAdjust()) write(1, g);
    if (f >= 0 && canAdjust() && keyMode() && canAdjust()) write(98, f);
  }

  final AtomicBoolean startRequest = new AtomicBoolean(), stopRequest = new AtomicBoolean();
  final AtomicLong levelAfterStopAt = new AtomicLong();
  final SessionTotals totals = new SessionTotals();
  volatile boolean active = false, foreground = false, motionPossible = false, holding = false;
  volatile boolean pausing = false, authenticated = false;
  volatile boolean parked;
  volatile long uiHeartbeatAt;
  volatile boolean pulseEnabled;
  boolean pulseDeclared, pulseRejected;
  int pulseRejectedMode = -1, pulseRetries;
  volatile int pulseReads;
  volatile String pulseStatus = "Waiting for controller", pulseBytes = "—";
  volatile int grip;
  volatile long gripAt;
  long lastPulseRead;
  int lastPulsePacked = -1;
  final GripPulse gripFilter = new GripPulse();

  public int gripBpm() {
    return fresh() && SystemClock.elapsedRealtime() - gripAt < 2500 ? grip : 0;
  }

  public String gripStatus() {
    if (!fresh()) return "Handles · waiting for controller";
    if (gripBpm() > 0) return "Handles · measuring";
    return pulseEnabled ? "Handles · waiting for pulse" : "Handles · tap for status";
  }

  public String gripDetails() {
    return "Handle sensor: "
        + (fresh() ? pulseStatus : "Controller disconnected or stale")
        + "\nSuccessful reads: "
        + pulseReads
        + " · payload: "
        + pulseBytes
        + "\n\n"
        + "Hold both metal contacts with still hands for 20–30 seconds. A valid pulse is shown as"
        + " BPM. This controller field has no verified separate touch flag, so Console cannot"
        + " confirm contact before a pulse arrives.\n\n"
        + "Bluetooth sensor: connected separately below. Phone audio does not provide heart rate.";
  }

  void pollPulse() throws Exception {
    long now = SystemClock.elapsedRealtime();
    if (parked || stopRequest.get() || now - lastPulseRead < 1000) return;
    // A valid status-4 reply while key-out/idle need not mean no sensor. Retry only on a
    // later usable mode transition, at most twice per verified session. Never retry malformed data.
    if (!pulseEnabled
        && pulseDeclared
        && pulseRejected
        && pulseRejectedMode != mode
        && (mode == 1 || mode == 2 || mode == 3)
        && pulseRetries < 2) {
      pulseEnabled = true;
      pulseRejected = false;
      pulseRetries++;
    }
    if (!pulseEnabled) return;
    lastPulseRead = now;
    try {
      byte[] pulse = transact(Protocol.readField(10), 4);
      int packed =
          (pulse[0] & 255)
              | ((pulse[1] & 255) << 8)
              | ((pulse[2] & 255) << 16)
              | ((pulse[3] & 255) << 24);
      if (pulseReads == 0 || packed != lastPulsePacked) {
        lastPulsePacked = packed;
        pulseBytes = Protocol.hex(pulse, pulse.length);
      }
      grip = gripFilter.sample(pulse[0] & 255);
      gripAt = SystemClock.elapsedRealtime();
      pulseStatus = grip > 0 ? "Pulse received" : "Reading controller; no valid pulse yet";
      pulseReads++;
    } catch (Protocol.Rejected | Protocol.ShapeMismatch unsupported) {
      grip = 0;
      gripAt = 0;
      gripFilter.clear();
      pulseRejected =
          unsupported instanceof Protocol.Rejected && ((Protocol.Rejected) unsupported).status == 4;
      pulseRejectedMode = mode;
      pulseStatus =
          unsupported instanceof Protocol.ShapeMismatch
              ? "Unexpected sensor reply size (" + unsupported.getMessage() + "); read disabled"
              : "Controller rejected sensor read (status "
                  + ((Protocol.Rejected) unsupported).status
                  + ")"
                  + (pulseRejected && pulseRetries < 2 ? "; awaiting mode change" : "");
      pulseEnabled = false;
      Log.w(
          TAG,
          "Optional handle pulse unavailable: "
              + unsupported.getMessage()
              + " mode="
              + mode
              + "; continuing motor telemetry");
    }
  }

  public void uiHeartbeat() {
    if (foreground) uiHeartbeatAt = SystemClock.elapsedRealtime();
  }

  boolean uiResponsive() {
    long age = SystemClock.elapsedRealtime() - uiHeartbeatAt;
    return uiHeartbeatAt > 0 && age >= 0 && age < 5000;
  }

  long lastAuthentication = -10000;
  volatile Thread worker;
  volatile int mode = -1, speedRaw = -1, inclineRaw = 0, fan = -1;
  volatile long sampleTime = 0;
  volatile String message = "Connecting to treadmill…";
  UsbManager manager;
  UsbDevice device;
  UsbDeviceConnection usb;
  UsbInterface iface;
  UsbEndpoint in, out;
  Handler handler = new Handler(Looper.getMainLooper());
  final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (ACTION.equals(i.getAction())) {
            if (device != null && manager.hasPermission(device)) begin();
            else {
              message = "Allow ICON USB access to connect";
              render();
            }
          }
        }
      };

  public ControllerEngine(Context c, Runnable changed) {
    context = c;
    onChange = changed;
    manager = (UsbManager) c.getSystemService(Context.USB_SERVICE);
    androidx.core.content.ContextCompat.registerReceiver(
        c,
        receiver,
        new IntentFilter(ACTION),
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  public void controlsVisible(boolean visible) {
    foreground = visible;
    if (!visible) {
      levelAfterStopAt.set(0);
      clearAdjustments();
      holding = false;
      startRequest.set(false);
      if (motionPossible) stopRequest.set(true);
      else active = false;
      totals.gap();
    } else {
      uiHeartbeat();
      request();
    }
  }

  public void userStart() {
    if (canStart()) {
      startRequest.set(true);
      message = "Starting…";
      render();
    }
  }

  public void userStop() {
    requestStop(false);
  }

  // Only the explicit on-screen finish/Stop action requests deck leveling. Pause, watchdog,
  // backgrounding, key removal and reconnect must never introduce incline movement.
  public void userFinish() {
    requestStop(true);
  }

  void requestStop(boolean level) {
    levelAfterStopAt.set(level && canAdjust() ? SystemClock.elapsedRealtime() : 0);
    pausing = true;
    clearAdjustments();
    holding = false;
    startRequest.set(false);
    stopRequest.set(true);
    message = "Stop requested…";
    render();
  }

  boolean canLevel(long requestedAt) {
    long now = SystemClock.elapsedRealtime();
    return requestedAt > 0
        && now >= requestedAt
        && now - requestedAt < 5000
        && active
        && authenticated
        && foreground
        && uiResponsive()
        && sampleTime > 0
        && now >= sampleTime
        && now - sampleTime < 1500
        && !motionPossible
        && !stopRequest.get()
        && !startRequest.get()
        && (mode == 1 || mode == 3)
        && speedRaw >= 0
        && speedRaw <= 5;
  }

  void levelStoppedDeck(long requestedAt) throws Exception {
    if (!canLevel(requestedAt)) return;
    snapshot();
    if (!canLevel(requestedAt)) return;
    int grade = (short) read(17, 2);
    if (grade < -300 || grade > 1500) throw new Exception("Invalid incline before leveling");
    if (grade == 0) return;
    int checkedMode = read(12, 1);
    if ((checkedMode != 1 && checkedMode != 3) || !canLevel(requestedAt)) return;
    try {
      write(1, 0);
      Log.i(TAG, "FINISH_LEVEL_REQUESTED — stopped deck target 0%; no retry or replay");
    } catch (Protocol.Rejected rejected) {
      // An optional leveling rejection must not disable otherwise working belt controls.
      Log.w(TAG, "FINISH_LEVEL_REJECTED status=" + rejected.status + "; use incline buttons");
    }
  }

  public void shutdown() {
    controlsVisible(false);
    try {
      context.unregisterReceiver(receiver);
    } catch (Exception ignored) {
    }
  }

  void render() {
    if (onChange != null) handler.post(onChange);
  }

  boolean fresh() {
    return active && sampleTime > 0 && SystemClock.elapsedRealtime() - sampleTime < 1500;
  }

  boolean canStart() {
    return authenticated
        && uiResponsive()
        && active
        && sampleTime > 0
        && !pausing
        && !stopRequest.get()
        && !motionPossible
        && Control.canStart(mode, speedRaw, SystemClock.elapsedRealtime() - sampleTime, foreground);
  }

  boolean canReset() {
    return fresh() && !motionPossible && (mode == 1 || mode == 3 || mode == 8) && speedRaw <= 5;
  }

  void request() {
    if (!foreground || active) return;
    if (worker != null) {
      handler.postDelayed(() -> request(), 250);
      return;
    }
    device = null;
    for (UsbDevice d : manager.getDeviceList().values())
      if (d.getVendorId() == 0x213c && d.getProductId() == 2) {
        device = d;
        break;
      }
    if (device == null) {
      message = "Treadmill controller not found";
      render();
      return;
    }
    if (manager.hasPermission(device)) begin();
    else
      manager.requestPermission(
          device,
          PendingIntent.getBroadcast(
              context,
              0,
              new Intent(ACTION).setPackage(context.getPackageName()),
              PendingIntent.FLAG_UPDATE_CURRENT));
  }

  void begin() {
    if (!foreground || active || worker != null) return;
    active = true;
    authenticated = false;
    sampleTime = 0;
    mode = speedRaw = -1;
    startRequest.set(false);
    holding = false;
    stopRequest.set(false);
    levelAfterStopAt.set(0);
    motionPossible = false;
    pausing = false;
    message = "Connecting…";
    worker = new Thread(() -> work(), "manual-console-usb");
    worker.start();
    render();
  }

  byte[] exchange(byte[] request) throws Exception {
    if (request.length > 64) throw new IllegalArgumentException("Oversize controller request");
    byte[] tx = Arrays.copyOf(request, 64), rx = new byte[64];
    if (usb.bulkTransfer(out, tx, 64, 300) != 64) throw new Exception("USB request failed");
    int n = usb.bulkTransfer(in, rx, 64, 500);
    if (n < 5) throw new Exception("USB reply unavailable");
    return Arrays.copyOf(rx, n);
  }

  byte[] transact(byte[] request, int size) throws Exception {
    byte[] rx = exchange(request);
    return Protocol.payload(rx, size, rx.length);
  }

  void authenticate() throws Exception {
    authenticated = false;
    sampleTime = 0;
    startRequest.set(false);
    holding = false;
    levelAfterStopAt.set(0);
    clearAdjustments();
    message = "Verifying controller session…";
    render();
    long remaining;
    while ((remaining = 3000 - (SystemClock.elapsedRealtime() - lastAuthentication)) > 0) {
      if (!active || (!foreground && !motionPossible && !stopRequest.get()))
        throw new InterruptedException("Controller verification cancelled while hidden");
      Thread.sleep(Math.min(100, remaining));
    }
    if (!active || (!foreground && !motionPossible && !stopRequest.get()))
      throw new InterruptedException("Controller verification cancelled while hidden");
    lastAuthentication = SystemClock.elapsedRealtime();
    ControllerAuth.Identity identity =
        ControllerAuth.verify(
            req -> {
              byte[] reply = exchange(req);
              Thread.sleep(80);
              return reply;
            });
    authenticated = true;
    pulseEnabled = identity.pulse;
    pulseDeclared = identity.pulse;
    pulseRejected = false;
    pulseRejectedMode = -1;
    pulseRetries = 0;
    pulseReads = 0;
    pulseBytes = "—";
    lastPulsePacked = -1;
    pulseStatus = identity.pulse ? "Waiting for first pulse read" : "Pulse field not declared";
    grip = 0;
    gripAt = 0;
    lastPulseRead = 0;
    gripFilter.clear();
    Log.i(
        TAG,
        "SESSION_VERIFIED sw="
            + identity.software
            + " model="
            + identity.model
            + " part="
            + identity.part
            + " library="
            + identity.library
            + " — pending starts discarded");
  }

  int read(int field, int size) throws Exception {
    return (int) Protocol.unsigned(transact(Protocol.readField(field), size));
  }

  void write(int field, int value) throws Exception {
    if (!authenticated) throw new ControllerAuth.Required();
    Log.i(TAG, "WRITE field=" + field + " value=" + value);
    transact(Control.write(field, value), 2);
  }

  void snapshot() throws Exception {
    int m = read(12, 1), s = read(0, 2);
    if (s < 0 || s > 1931) throw new Exception("Reported speed outside verified limits");
    mode = m;
    speedRaw = s;
    sampleTime = SystemClock.elapsedRealtime();
  }

  void startNow() throws Exception {
    snapshot();
    if (!canStart()) {
      message = "Start blocked — wait for Ready with belt stopped";
      return;
    }
    motionPossible = true;
    for (int[] cmd : Control.startSteps()) {
      int checkedMode = read(12, 1);
      if (!foreground
          || !uiResponsive()
          || stopRequest.get()
          || (checkedMode != 1 && checkedMode != 2 && checkedMode != 3)) {
        stopRequest.set(true);
        return;
      }
      if (!foreground || stopRequest.get()) {
        stopRequest.set(true);
        return;
      }
      write(cmd[0], cmd[1]);
    }
    snapshot();
    message = "Running";
    Log.i(TAG, "START_ACCEPTED mode=" + mode + " speed=" + speedRaw);
  }

  void pauseBelt() throws Exception {
    clearAdjustments();
    startRequest.set(false);
    holding = false;
    snapshot();
    if (mode == 8) {
      motionPossible = false;
      message = "Safety key removed";
      Log.i(TAG, "KEY_OUT — no redundant Pause write");
      return;
    }
    if (Control.needsPause(mode, speedRaw)) write(12, 3);
    int zeros = 0;
    for (int i = 0; i < 10; i++) {
      snapshot();
      if (mode == 8) {
        motionPossible = false;
        message = "Safety key removed";
        return;
      }
      if ((mode == 3 || mode == 1) && speedRaw <= 5) zeros++;
      else zeros = 0;
      if (zeros >= 2) {
        motionPossible = false;
        message = "Paused";
        Log.i(TAG, "PAUSE_CONFIRMED mode=" + mode + " speed=" + speedRaw);
        return;
      }
      Thread.sleep(100);
    }
    throw new Exception("Pause not confirmed");
  }

  void work() {
    try {
      usb = manager.openDevice(device);
      if (usb == null) throw new Exception("USB open failed");
      iface = device.getInterface(0);
      if (!usb.claimInterface(iface, true)) throw new Exception("USB interface busy");
      in = out = null;
      for (int i = 0; i < iface.getEndpointCount(); i++) {
        UsbEndpoint e = iface.getEndpoint(i);
        if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e;
        else out = e;
      }
      if (in == null || out == null) throw new Exception("USB endpoints missing");
      byte[] drain = new byte[64];
      for (int i = 0; i < 4; i++) if (usb.bulkTransfer(in, drain, 64, 80) <= 0) break;
      authenticate();
      // Restoring a database or USB session must never adopt a previously Running state.
      // Reconcile it with Pause before enabling local controls; a new Start needs a fresh hold.
      pausing = true;
      try {
        snapshot();
        if (mode != 8 && (mode == 2 || speedRaw > 5)) {
          motionPossible = true;
          pauseBelt();
        }
      } finally {
        pausing = false;
      }
      int tick = 0, previousMode = -1;
      while (active && (foreground || motionPossible || stopRequest.get())) {
        try {
          if (motionPossible && !uiResponsive() && !stopRequest.get()) {
            Log.w(TAG, "UI_HEARTBEAT_TIMEOUT — requesting Pause, no automatic restart");
            userStop();
          }
          if (stopRequest.getAndSet(false)) {
            long levelAt = levelAfterStopAt.getAndSet(0);
            pausing = true;
            try {
              pauseBelt();
              levelStoppedDeck(levelAt);
            } finally {
              clearAdjustments();
              pausing = false;
            }
            render();
            continue;
          }
          if (startRequest.getAndSet(false)) startNow();
          if (stopRequest.get()) continue;
          adjustNow();
          snapshot();
          if (mode == 8) {
            clearAdjustments();
            motionPossible = false;
            holding = false;
            startRequest.set(false);
            message = "Safety key removed — Start disabled";
          } else if (mode == 2) {
            parked = false;
            motionPossible = true;
            message = "Running";
          } else if (mode == 3) {
            motionPossible = speedRaw > 5;
            if (motionPossible) stopRequest.set(true);
            message = motionPossible ? "Confirming Stop…" : "Paused — hold to run at 1 mph";
          } else if (mode == 1) {
            motionPossible = speedRaw > 5;
            if (motionPossible) {
              stopRequest.set(true);
              message = "Confirming Stop…";
            } else if (!holding) message = "Ready — hold Start for 2 seconds";
          } else {
            message = "Controller not ready";
            if (motionPossible) stopRequest.set(true);
          }
          if (stopRequest.get()) continue;
          int g = (short) read(17, 2), f = read(98, 1);
          if (g < -300 || g > 1500) throw new Exception("Reported incline outside verified limits");
          if (f < 0 || f > 4) throw new Exception("Reported fan state outside verified limits");
          inclineRaw = g;
          fan = f;
          pollPulse();
          totals.sample(SystemClock.elapsedRealtime(), mode, speedRaw);
          if (++tick % 10 == 0 || mode != previousMode) {
            Log.i(
                TAG,
                "STATE mode="
                    + mode
                    + " speed="
                    + speedRaw
                    + " incline="
                    + g
                    + " fan="
                    + f
                    + " movingMs="
                    + totals.movingMs
                    + " meters="
                    + totals.meters);
            previousMode = mode;
          }
          render();
          Thread.sleep(parked && !motionPossible ? 750 : 150);
        } catch (ControllerAuth.Required expired) {
          boolean requirePause = motionPossible || stopRequest.get();
          authenticated = false;
          sampleTime = 0;
          startRequest.set(false);
          holding = false;
          clearAdjustments();
          Log.w(TAG, "SESSION_EXPIRED — discarded control requests; no automatic Start replay");
          authenticate();
          snapshot();
          if (mode == 8) {
            motionPossible = false;
            stopRequest.set(false);
          } else if (requirePause || mode == 2 || speedRaw > 5) {
            motionPossible = true;
            stopRequest.set(true);
          }
          message =
              mode == 8
                  ? "Safety key removed — Start disabled"
                  : "Reconnected — hold Start when stopped";
          render();
        }
      }
    } catch (Exception e) {
      startRequest.set(false);
      totals.gap();
      Log.e(TAG, "CONSOLE_ERROR", e);
      if (motionPossible) {
        try {
          pauseBelt();
        } catch (Exception failed) {
          message = "Stop unconfirmed — use physical Stop or pull safety key";
          Log.e(TAG, "STOP_UNCONFIRMED", failed);
        }
      } else message = "Controller disconnected — tap Reconnect";
      sampleTime = 0;
      render();
    } finally {
      levelAfterStopAt.set(0);
      clearAdjustments();
      authenticated = false;
      active = false;
      sampleTime = 0;
      totals.gap();
      try {
        if (usb != null) {
          usb.releaseInterface(iface);
          usb.close();
        }
      } catch (Exception ignored) {
      }
      usb = null;
      worker = null;
      render();
    }
  }
}

package org.opentread.console;

import android.content.Context;
import android.hardware.usb.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Runs the production worker against an in-memory USB controller. Never opens real USB or ADB. */
public final class EngineHostTest {
  static final List<ControllerEngine> engines = new ArrayList<>();
  static ControllerEngine frozen;

  static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  static void until(BooleanSupplier condition, long timeout, String message) throws Exception {
    long end = System.nanoTime() / 1000000 + timeout;
    while (System.nanoTime() / 1000000 < end) {
      for (ControllerEngine e : engines) if (e != frozen) e.uiHeartbeat();
      if (condition.getAsBoolean()) break;
      Thread.sleep(10);
    }
    check(condition.getAsBoolean(), message);
  }

  static byte[] reply(int cmd, int status, byte[] payload) {
    byte[] r = new byte[payload.length + 5];
    r[0] = 4;
    r[1] = (byte) r.length;
    r[2] = (byte) cmd;
    r[3] = (byte) status;
    System.arraycopy(payload, 0, r, 4, payload.length);
    for (int i = 0; i < r.length - 1; i++) r[r.length - 1] += r[i];
    return r;
  }

  static final class Board extends UsbDeviceConnection {
    volatile int mode = 8, speed = 0, fan = 0, grade = 0, authCount = 0;
    volatile boolean refuseAuth, expireNextWrite, corruptNextRead, keepSpeedOnPause;
    boolean pulseCapability, rejectPulse, shortPulse, rejectPulseKeyOut;
    volatile int pulse = 100;
    int pulseReads;
    volatile int modeAfterFirstWrite = -1;
    int modeReadsUntilHook;
    Runnable modeReadHook;
    final List<String> writes = Collections.synchronizedList(new ArrayList<>());
    byte[] pending;

    @Override
    public synchronized int bulkTransfer(UsbEndpoint endpoint, byte[] b, int count, int timeout) {
      if (endpoint.getDirection() == 128) {
        if (pending == null) return -1;
        System.arraycopy(pending, 0, b, 0, pending.length);
        int n = pending.length;
        pending = null;
        return n;
      }
      int cmd = b[2] & 255;
      if (cmd == 129)
        pending =
            reply(
                cmd,
                2,
                pulseCapability
                    ? new byte[] {83, 1, 1, 0, 0, 0, 0, 0, 2, 0, 4}
                    : new byte[] {83, 1, 1, 0, 0, 0});
      else if (cmd == 136) pending = reply(cmd, 2, new byte[] {(byte) 130, (byte) 132, (byte) 144});
      else if (cmd == 130) {
        byte[] body = new byte[11];
        int[] v = {19221, 425467};
        for (int j = 0; j < 2; j++)
          for (int i = 0; i < 4; i++) body[3 + j * 4 + i] = (byte) (v[j] >>> (8 * i));
        pending = reply(cmd, 2, body);
      } else if (cmd == 132) pending = reply(cmd, 2, new byte[] {83, 113, 2});
      else if (cmd == 144) {
        authCount++;
        pending = reply(cmd, refuseAuth ? 8 : 2, new byte[] {8});
      } else {
        check(cmd == 2, "unexpected opcode: " + cmd);
        boolean write = b[3] != 0;
        int sections = b[write ? 3 : 4] & 255, maskAt = write ? 4 : 5, field = -1;
        for (int section = 0; section < sections; section++)
          for (int bit = 0; bit < 8; bit++)
            if ((b[maskAt + section] & (1 << bit)) != 0) {
              check(field < 0, "only one field per request");
              field = section * 8 + bit;
            }
        check(field >= 0, "missing field");
        if (write) {
          int at = maskAt + sections;
          int value = b[at] & 255;
          if (field == 0 || field == 1) value |= (b[at + 1] & 255) << 8;
          writes.add(field + "=" + value);
          if (expireNextWrite) {
            expireNextWrite = false;
            pending = reply(cmd, 8, new byte[0]);
          } else {
            if (field == 12) {
              if (value == 3 && mode == 1 && speed == 0) {
                pending = reply(cmd, 4, new byte[0]);
                return count;
              }
              mode = value;
              if (value == 3 && !keepSpeedOnPause) speed = 0;
            } else if (field == 0) speed = value;
            else if (field == 1) grade = (short) value;
            else if (field == 98) fan = value;
            else check(field == 95 && value == 0, "write whitelist");
            if (writes.size() == 1 && modeAfterFirstWrite >= 0) mode = modeAfterFirstWrite;
            pending = reply(cmd, 2, new byte[] {0, 0});
          }
        } else {
          int value, size;
          if (field == 12) {
            value = mode;
            size = 1;
            if (modeReadsUntilHook > 0 && --modeReadsUntilHook == 0 && modeReadHook != null)
              modeReadHook.run();
          } else if (field == 10) {
            pulseReads++;
            if (rejectPulse || (rejectPulseKeyOut && mode == 8)) {
              pending = reply(cmd, 4, new byte[0]);
              return count;
            }
            value = pulse;
            size = shortPulse ? 1 : 4;
          } else if (field == 0) {
            value = speed;
            size = 2;
          } else if (field == 17) {
            value = grade;
            size = 2;
          } else {
            check(field == 98, "read whitelist");
            value = fan;
            size = 1;
          }
          byte[] body = new byte[size];
          for (int i = 0; i < size; i++) body[i] = (byte) (value >>> (8 * i));
          pending = reply(cmd, 2, body);
          if (corruptNextRead) {
            corruptNextRead = false;
            pending[pending.length - 1] ^= 1;
          }
        }
      }
      return count;
    }
  }

  static ControllerEngine engine(Board board) {
    UsbManager manager =
        new UsbManager() {
          public UsbDeviceConnection openDevice(UsbDevice d) {
            return board;
          }
        };
    Context context =
        new Context() {
          public Object getSystemService(String name) {
            return manager;
          }
        };
    ControllerEngine e = new ControllerEngine(context, null);
    engines.add(e);
    e.controlsVisible(true);
    return e;
  }

  static void ready(ControllerEngine e) throws Exception {
    until(
        () -> e.fresh() && e.authenticated && !e.pausing,
        5500,
        "worker did not connect: " + e.message);
  }

  static void close(ControllerEngine e) throws Exception {
    e.controlsVisible(false);
    until(() -> e.worker == null, 5500, "worker did not close");
  }

  static ControllerEngine stoppedDirect(Board b) {
    b.mode = 3;
    b.grade = 300;
    ControllerEngine e = new ControllerEngine(new Context(), null);
    e.usb = b;
    e.in = new UsbEndpoint(128);
    e.out = new UsbEndpoint(0);
    e.active = e.foreground = e.authenticated = true;
    e.mode = 3;
    e.speedRaw = 0;
    e.sampleTime = android.os.SystemClock.elapsedRealtime();
    e.uiHeartbeat();
    return e;
  }

  static void finishLevelTests() throws Exception {
    Board success = new Board();
    success.mode = 1;
    success.grade = 300;
    ControllerEngine finishing = engine(success);
    ready(finishing);
    check(success.writes.isEmpty(), "startup leveled deck without explicit Stop");
    finishing.userStart();
    until(() -> finishing.mode == 2 && finishing.speedRaw == 160, 2500, "finish test Start");
    finishing.userStop();
    until(() -> finishing.mode == 3 && !finishing.pausing, 2500, "ordinary Pause");
    check(success.grade == 300 && !success.writes.contains("1=0"), "Pause changed incline");
    finishing.userStart();
    until(() -> finishing.mode == 2 && finishing.speedRaw == 160, 2500, "finish test Resume");
    success.writes.clear();
    finishing.userFinish();
    until(() -> success.grade == 0 && !finishing.pausing, 2500, "finish did not level deck");
    check(
        success.writes.equals(Arrays.asList("12=3", "1=0")),
        "finish must confirm Pause before one leveling command: " + success.writes);
    finishing.userFinish();
    until(() -> !finishing.pausing && !finishing.stopRequest.get(), 2500, "repeat finish");
    check(Collections.frequency(success.writes, "1=0") == 1, "flat deck leveled redundantly");
    close(finishing);

    Board keyOut = new Board();
    keyOut.grade = 300;
    ControllerEngine keyed = engine(keyOut);
    ready(keyed);
    keyed.userFinish();
    until(() -> !keyed.pausing && !keyed.stopRequest.get(), 2500, "key-out finish");
    keyOut.mode = 1;
    until(() -> keyed.mode == 1, 2000, "key-in not seen");
    Thread.sleep(250);
    check(
        keyOut.writes.isEmpty() && keyOut.grade == 300, "key-in replayed key-out leveling request");
    close(keyed);

    for (int unsafe = 0; unsafe < 10; unsafe++) {
      Board b = new Board();
      ControllerEngine e = stoppedDirect(b);
      long requestAt = android.os.SystemClock.elapsedRealtime();
      if (unsafe == 0) requestAt -= 5001;
      if (unsafe == 1) e.uiHeartbeatAt -= 5001;
      if (unsafe == 2) e.foreground = false;
      if (unsafe == 3) e.motionPossible = true;
      if (unsafe == 4) b.mode = 8;
      if (unsafe == 5) b.speed = 160;
      if (unsafe == 6) b.mode = 2;
      if (unsafe == 7) e.authenticated = false;
      if (unsafe == 8) e.startRequest.set(true);
      if (unsafe == 9) e.stopRequest.set(true);
      e.levelStoppedDeck(requestAt);
      check(b.writes.isEmpty(), "unsafe leveling case " + unsafe);
      e.shutdown();
    }
    for (int interruption = 0; interruption < 4; interruption++) {
      Board b = new Board();
      ControllerEngine e = stoppedDirect(b);
      int kind = interruption;
      b.modeReadsUntilHook = 2;
      b.modeReadHook =
          () -> {
            if (kind == 0) e.userStop();
            if (kind == 1) e.foreground = false;
            if (kind == 2) e.uiHeartbeatAt -= 5001;
            if (kind == 3) e.startRequest.set(true);
          };
      e.levelStoppedDeck(android.os.SystemClock.elapsedRealtime());
      check(b.writes.isEmpty(), "leveling ignored interruption during final mode reply " + kind);
      e.shutdown();
    }
    Board expires = new Board();
    expires.mode = 1;
    expires.grade = 300;
    ControllerEngine retry = engine(expires);
    ready(retry);
    expires.expireNextWrite = true;
    retry.userFinish();
    until(() -> expires.authCount == 2 && retry.fresh(), 5500, "level expiry did not reverify");
    Thread.sleep(250);
    check(
        expires.writes.equals(Arrays.asList("1=0")) && expires.grade == 300,
        "expired leveling replayed or changed speed: " + expires.writes);
    close(retry);
    System.out.println(
        "PASS: explicit Stop confirms Pause then levels once; Pause preserves grade;"
            + " flat/key-out/stale/hidden/running/unauthenticated/interrupted commands blocked;"
            + " no key-in or session-expiry leveling replay.");
  }

  static void run() throws Exception {
    finishLevelTests();
    Board pulses = new Board();
    pulses.pulseCapability = true;
    ControllerEngine pulseEngine = engine(pulses);
    until(() -> pulseEngine.gripBpm() == 100, 3000, "declared pulse field not read");
    pulses.pulse = 0;
    until(() -> pulseEngine.gripBpm() == 0, 2500, "lost contact retained grip");
    check(pulses.writes.isEmpty(), "pulse read sent field write");
    check(
        pulseEngine.pulseReads > 0 && pulseEngine.pulseBytes.equals("00000000"),
        "diagnostic payload missing");
    check(pulseEngine.gripStatus().contains("waiting for pulse"), "zero falsely claimed contact");
    close(pulseEngine);
    Board gatedPulse = new Board();
    gatedPulse.pulseCapability = true;
    gatedPulse.rejectPulseKeyOut = true;
    ControllerEngine gated = engine(gatedPulse);
    until(
        () -> gatedPulse.pulseReads == 1 && !gated.pulseEnabled,
        3000,
        "initial pulse rejection missing");
    check(gated.pulseStatus.contains("status 4"), "rejection reason missing");
    gatedPulse.mode = 1;
    until(() -> gated.gripBpm() == 100, 3000, "key-in did not retry optional pulse");
    check(gatedPulse.writes.isEmpty(), "sensor retry changed controller state");
    close(gated);
    Board rejectedPulse = new Board();
    rejectedPulse.pulseCapability = true;
    rejectedPulse.rejectPulse = true;
    ControllerEngine optional = engine(rejectedPulse);
    until(
        () -> rejectedPulse.pulseReads == 1 && !optional.pulseEnabled && optional.fresh(),
        3000,
        "optional pulse rejection disconnected motor telemetry");
    check(
        optional.active && optional.gripBpm() == 0 && rejectedPulse.writes.isEmpty(),
        "optional rejection not isolated");
    rejectedPulse.mode = 1;
    until(
        () -> rejectedPulse.pulseReads == 2 && !optional.pulseEnabled,
        3000,
        "mode change retry missing");
    rejectedPulse.mode = 3;
    until(
        () -> rejectedPulse.pulseReads == 3 && !optional.pulseEnabled,
        3000,
        "second mode retry missing");
    rejectedPulse.mode = 1;
    Thread.sleep(1200);
    optional.uiHeartbeat();
    check(rejectedPulse.pulseReads == 3 && optional.fresh(), "optional retries were unbounded");
    close(optional);
    Board shortPulse = new Board();
    shortPulse.pulseCapability = true;
    shortPulse.shortPulse = true;
    ControllerEngine shortOptional = engine(shortPulse);
    until(
        () -> shortPulse.pulseReads == 1 && !shortOptional.pulseEnabled && shortOptional.fresh(),
        3000,
        "valid optional pulse shape mismatch disconnected core telemetry");
    check(shortPulse.writes.isEmpty(), "wrong pulse shape sent a write");
    close(shortOptional);
    for (int field : new int[] {0, 1, 98}) {
      for (boolean stop : new boolean[] {true, false}) {
        Board interrupted = new Board();
        interrupted.mode = 2;
        interrupted.speed = 160;
        ControllerEngine direct = new ControllerEngine(new Context(), null);
        direct.usb = interrupted;
        direct.in = new UsbEndpoint(128);
        direct.out = new UsbEndpoint(0);
        direct.active = direct.foreground = direct.authenticated = true;
        direct.mode = 2;
        direct.speedRaw = 160;
        direct.sampleTime = android.os.SystemClock.elapsedRealtime();
        direct.uiHeartbeat();
        if (field == 0) direct.speedStep(true);
        else if (field == 1) direct.gradeStep(true);
        else direct.fanStep();
        interrupted.modeReadsUntilHook = 2; // snapshot read, then final permission read
        interrupted.modeReadHook =
            () -> {
              if (stop) direct.userStop();
              else direct.uiHeartbeatAt = android.os.SystemClock.elapsedRealtime() - 5001;
            };
        direct.adjustNow();
        check(
            interrupted.writes.isEmpty(),
            "adjustment sent after Stop/UI stall during mode reply: " + field);
        if (stop) check(direct.stopRequest.get(), "Stop was lost during adjustment");
        direct.active = false;
        direct.shutdown();
      }
    }
    Board b = new Board();
    ControllerEngine e = engine(b);
    ready(e);
    check(b.authCount == 1 && b.writes.isEmpty(), "startup authenticates without field writes");
    check(!e.canStart() && !e.canAdjust(), "key-out gates");
    e.userStart();
    e.speedStep(true);
    e.gradeStep(true);
    e.fanStep();
    Thread.sleep(200);
    check(b.writes.isEmpty(), "key-out input sent a write");
    close(e);
    e.controlsVisible(true);
    ready(e);
    check(b.authCount == 2 && b.writes.isEmpty(), "fast reconnect failed or replayed input");
    close(e);

    b = new Board();
    b.mode = 1;
    e = engine(b);
    ready(e);
    e.userStop();
    ControllerEngine idleStop = e;
    until(() -> !idleStop.pausing && !idleStop.stopRequest.get(), 2500, "idle Stop did not finish");
    check(e.fresh() && b.writes.isEmpty(), "already-idle Stop disconnected or wrote Pause");
    e.userStop();
    until(() -> !idleStop.pausing && !idleStop.stopRequest.get(), 2500, "repeat idle Stop");
    check(e.fresh() && b.writes.isEmpty(), "repeat idle Stop was not harmless");
    close(e);

    b = new Board();
    b.refuseAuth = true;
    e = engine(b);
    ControllerEngine refused = e;
    until(() -> refused.worker == null, 3000, "refused authentication did not disconnect");
    check(
        !e.authenticated && !e.canStart() && b.writes.isEmpty(),
        "rejected verification enabled controls");

    b = new Board();
    b.mode = 1;
    e = engine(b);
    ready(e);
    b.expireNextWrite = true;
    e.userStart();
    Board expired = b;
    until(() -> expired.authCount == 2, 5500, "expiry did not reverify");
    ControllerEngine recovered = e;
    until(
        () ->
            recovered.fresh()
                && (recovered.mode == 1 || recovered.mode == 3)
                && recovered.speedRaw == 0,
        2500,
        "expiry did not finish safe Pause");
    check(b.writes.equals(Arrays.asList("95=0")), "expired Start was replayed: " + b.writes);
    check(!e.startRequest.get() && !e.holding, "expiry retained held Start");
    close(e);

    for (int changedMode : new int[] {8, 99}) {
      b = new Board();
      b.mode = 1;
      b.modeAfterFirstWrite = changedMode;
      e = engine(b);
      ready(e);
      e.userStart();
      Board changed = b;
      until(() -> !changed.writes.isEmpty(), 2000, "Start did not enter sequence");
      Thread.sleep(400);
      check(
          !b.writes.contains("12=2") && !b.writes.contains("0=160"),
          "Start continued after mode changed");
      close(e);
    }

    b = new Board();
    b.mode = 1;
    e = engine(b);
    ready(e);
    e.userStart();
    ControllerEngine running = e;
    until(
        () -> running.fresh() && running.mode == 2 && running.speedRaw == 160,
        2500,
        "normal local Start");
    e.speedStep(true);
    e.gradeStep(true);
    e.fanStep();
    Board adjusted = b;
    until(
        () -> adjusted.grade == 50 && adjusted.fan == 1 && adjusted.speed > 160,
        2000,
        "bounded local adjustments");
    e.controlsVisible(false);
    until(() -> running.worker == null, 3000, "background worker not stopped");
    check(b.mode == 3 && b.speed == 0 && !e.motionPossible, "background did not Pause");
    check(Collections.frequency(b.writes, "12=2") == 1, "unexpected extra Start");

    b = new Board();
    e = engine(b);
    ready(e);
    e.holding = true;
    e.sampleTime = 0;
    check(!e.canStart() && !e.canAdjust(), "missing sample enabled controls");
    close(e);
    check(!e.holding, "background retained Start hold");

    b = new Board();
    e = engine(b);
    ready(e);
    b.corruptNextRead = true;
    ControllerEngine broken = e;
    until(() -> broken.worker == null, 2500, "corrupt reply did not disconnect");
    check(!e.fresh() && !e.canStart() && b.writes.isEmpty(), "bad packet did not fail closed");

    b = new Board();
    b.fan = 255;
    e = engine(b);
    ControllerEngine badFan = e;
    until(() -> badFan.worker == null, 2500, "invalid fan state not rejected");
    check(!e.fresh() && b.writes.isEmpty(), "invalid fan kept controls active");

    b = new Board();
    b.mode = 3;
    b.speed = 160;
    e = engine(b);
    ControllerEngine inconsistent = e;
    until(
        () -> inconsistent.fresh() && inconsistent.mode == 3 && inconsistent.speedRaw == 0,
        2500,
        "paused/nonzero reading not reconciled");
    check(b.writes.equals(Arrays.asList("12=3")), "inconsistent state used anything except Pause");
    close(e);

    b = new Board();
    b.mode = 2;
    b.speed = 805;
    e = engine(b);
    ControllerEngine afterRestart = e;
    until(
        () -> afterRestart.fresh() && afterRestart.mode == 3 && afterRestart.speedRaw == 0,
        2500,
        "startup adopted a previously running controller");
    check(b.writes.equals(Arrays.asList("12=3")), "startup restoration sent a non-Pause command");
    check(!e.startRequest.get() && !e.holding, "startup retained a Start request");
    close(e);

    b = new Board();
    b.mode = 1;
    e = engine(b);
    ready(e);
    e.userStart();
    ControllerEngine stalled = e;
    until(() -> stalled.mode == 2 && stalled.speedRaw == 160, 2500, "freeze test did not start");
    frozen = e;
    until(
        () -> stalled.mode == 3 && stalled.speedRaw == 0 && !stalled.motionPossible,
        7500,
        "USB worker failed to Pause when UI heartbeat stopped");
    e.userStart();
    check(!e.startRequest.get() && !e.canAdjust(), "unresponsive UI accepted input");
    check(
        Collections.frequency(b.writes, "12=2") == 1 && b.writes.contains("12=3"),
        "watchdog replayed Start or omitted Pause");
    frozen = null;
    close(e);
    engines.clear();
    System.out.println(
        "PASS: production controller worker on fake USB: key-out, normal handshake, rapid"
            + " reconnect, rejected auth, expiry/no Start replay, key/unknown-mode interruption,"
            + " local Start/adjust/Pause, background Stop, stale/corrupt data and fan bounds;"
            + " optional pulse mode-transition retry/cap, diagnostic publication, no sensor"
            + " writes.");
  }

  public static void main(String[] args) throws Exception {
    int repeats = args.length == 0 ? 1 : Integer.parseInt(args[0]);
    try {
      for (int i = 0; i < repeats; i++) run();
    } finally {
      // Fake USB workers only; failed assertions must not leave the test process hanging.
      for (ControllerEngine e : engines) e.shutdown();
      for (ControllerEngine e : engines) {
        Thread worker = e.worker;
        if (worker != null) worker.join(5000);
      }
    }
  }
}

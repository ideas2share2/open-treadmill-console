import org.opentread.console.IdlePolicy;
import org.opentread.console.WakeTouchGuard;

public final class IdleTest {
  static void check(boolean v, String why) {
    if (!v) throw new AssertionError(why);
  }

  public static void run() {
    IdlePolicy p = new IdlePolicy();
    p.touch(0);
    check(!p.update(0, false, true), "immediate park");
    check(!p.update(299999, false, true), "early park");
    check(p.update(300000, false, true), "five-minute idle");
    p.touch(300001);
    check(!p.parked() && !p.update(600000, false, true), "touch failed to wake/reset");
    check(p.update(600001, false, true), "touch-relative deadline");
    for (long now = 600002; now < 3L * 86400000; now += 200)
      check(!p.update(now, true, false), "running workout parked for lack of touch");
    long stop = 3L * 86400000;
    check(!p.update(stop, false, true), "immediate park after running");
    check(p.update(stop + 300000, false, true), "stop after long workout");
    check(!p.update(stop + 86400000, false, false), "uncertain motion allowed sleep");
    check(!p.update(10, false, true), "clock reset parked immediately");
    IdlePolicy allDay = new IdlePolicy();
    allDay.touch(0);
    allDay.update(0, false, true);
    for (long now = 300000; now <= 12L * 3600000; now += 1000)
      check(allDay.update(now, false, true), "long stopped idle escaped parked state");
    WakeTouchGuard wake = new WakeTouchGuard();
    check(!wake.consume(false, true, false), "awake touch swallowed");
    check(!wake.consume(false, false, true), "awake up swallowed");
    check(wake.consume(true, true, false), "wake down reached controls");
    check(wake.consume(false, false, false), "wake move reached controls after screen restored");
    check(wake.consume(false, false, true), "wake up clicked through");
    check(!wake.consume(false, true, false), "second deliberate tap lost");
    check(!wake.consume(false, false, true), "second tap up lost");
    check(wake.consume(true, true, false), "second idle cycle not guarded");
    check(wake.consume(false, false, true), "canceled wake not consumed");
    check(!wake.consume(false, true, false), "cancel left gesture latched");
    System.out.println(
        "PASS: stopped-only five-minute idle; touch wakes; three simulated running days never park;"
            + " unknown motion stays awake; monotonic reset;12hour stopped idle; full wake gesture"
            + " consumed without click-through, next deliberate tap and canceled-wake recovery.");
  }
}

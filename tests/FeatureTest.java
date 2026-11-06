import org.opentread.console.*;

public final class FeatureTest {
  static void check(boolean b, String s) {
    if (!b) throw new AssertionError(s);
  }

  public static void run() {
    ActivityType a = new ActivityType();
    check(a.label().equals("Walk"), "initial walk");
    for (int i = 0; i < 3000; i += 200) a.sample(i, 2, 644);
    check(!a.running(), "short peak stays walk");
    a.sample(3000, 2, 644);
    check(a.running(), "4mph sustained Run");
    a.sample(3200, 2, 643);
    a.sample(3400, 2, 644);
    check(a.running(), "threshold bounce stays Run");
    for (int i = 3600; i <= 6600; i += 200) a.sample(i, 2, 643);
    check(!a.running(), "sustained slower Walk");
    a = new ActivityType();
    a.sample(0, 2, 800);
    a.sample(1000, 2, 800);
    a.sample(2000, 3, 0);
    a.sample(3000, 2, 800);
    a.sample(4000, 2, 800);
    a.sample(5000, 2, 800);
    check(!a.running(), "pause cancels candidate");
    a.sample(6000, 2, 800);
    check(a.running(), "fresh sustained after pause");
    a = new ActivityType();
    a.sample(0, 2, 900);
    a.sample(1000, 2, 900);
    a.sample(5000, 2, 900);
    check(!a.running(), "stale gap not elapsed run time");
    a.gap();
    a.sample(7000, 8, 900);
    check(!a.running(), "key out not Run");
    WorkoutMath.Totals totals = new WorkoutMath.Totals();
    for (int i = 0; i <= 60000; i += 1000) totals.sample(i, 2, 160, 0);
    for (int i = 61000; i <= 180000; i += 1000) totals.sample(i, 2, 805, 0);
    check(totals.runMs > totals.walkMs, "mixed workout majority run");
    check(totals.walkMs + totals.runMs == totals.movingMs, "split times conserve total");
    check(
        Math.abs(totals.walkMeters + totals.runMeters - totals.meters) < .00001,
        "split distances conserve total");
    long prior = totals.movingMs;
    totals.gap();
    totals.sample(190000, 2, 805, 0);
    check(totals.movingMs == prior, "gap excludes distance and time");
    VideoGate gate = new VideoGate();
    check(!gate.unlocked(), "video locked on creation");
    for (String s : new String[] {null, "", "246", "24680", "0000", " 2468", "２４６８"})
      check(!gate.submit(s) && !gate.unlocked(), "wrong PIN denied");
    check(gate.submit("2468") && gate.unlocked(), "exact PIN grants video");
    gate.lock();
    check(!gate.unlocked(), "leave/stop/key/user event relocks");
    check(!new VideoGate().unlocked(), "restart doesn't retain grant");
    check(ProfileTheme.accent("slot-1") == 0xffc8ff30, "Profile One lime");
    check(ProfileTheme.accent("slot-2") == 0xffff74b8, "Profile Two pink");
    check(ProfileTheme.accent("guest") == 0xff62bdff, "Guest blue");
    check(ProfileTheme.accent("other") == ProfileTheme.FIRST, "safe profile fallback");
    System.out.println(
        "PASS: speed-based Walk/Run threshold, settle/bounce/pause/gap behavior and mixed totals;"
            + " video PIN unlock/reset; profile colors.");
  }
}

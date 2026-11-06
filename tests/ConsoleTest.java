import org.opentread.console.*;
import java.time.*;
import java.util.*;

public class ConsoleTest {
  static void check(boolean b, String what) {
    if (!b) throw new AssertionError(what);
  }

  static void near(double a, double b) {
    check(Math.abs(a - b) < .001, a + " != " + b);
  }

  static void rejected(int f, int v) {
    try {
      Control.write(f, v);
      throw new AssertionError("allowed " + f + "=" + v);
    } catch (IllegalArgumentException ok) {
    }
  }

  public static void main(String[] args) throws Exception {
    ProtocolTest.main(args);
    FeatureTest.run();
    AuthTest.run();
    PersonalBestsTest.run();
    IdleTest.run();
    DataFeaturesTest.run();
    RibbonPlotTest.run();
    CaloriesTest.run();
    for (int mode : new int[] {1, 2, 3})
      check(Control.canAdjust(mode, 0, true, false), "local adjustment gate");
    for (int mode : new int[] {-1, 0, 4, 8, 99})
      check(!Control.canAdjust(mode, 0, true, false), "invalid mode blocked");
    check(!Control.canAdjust(2, 0, false, false), "background adjustment blocked");
    check(!Control.canAdjust(2, 0, true, true), "stopping adjustment blocked");
    check(!Control.canAdjust(2, 1500, true, false), "stale adjustment blocked");
    check(!Control.canAdjust(2, -1, true, false), "invalid clock blocked");
    for (int raw : new int[] {80, 160, 500, 1931})
      for (boolean up : new boolean[] {false, true}) {
        int next = WorkoutMath.speedStep(raw, up);
        check(next >= 80 && next <= 1931, "speed limits");
        check(Math.abs(WorkoutMath.mph(next) - WorkoutMath.mph(raw)) < .14, "increment");
      }
    check(WorkoutMath.speedStep(1931, true) == 1931, "upper speed clamp");
    check(WorkoutMath.speedStep(80, false) == 80, "lower clamp");
    check(WorkoutMath.inclineStep(-300, false) == -300, "negative grade clamp");
    check(WorkoutMath.inclineStep(1500, true) == 1500, "upper grade clamp");
    check(WorkoutMath.inclineStep(0, true) == 50, "half percent");
    rejected(0, 0);
    rejected(0, 79);
    rejected(0, 1932);
    rejected(1, -301);
    rejected(1, 1501);
    rejected(98, -1);
    rejected(98, 5);
    rejected(95, 1);
    rejected(12, 1);
    rejected(96, 1);
    byte[] negative = Control.write(1, -300);
    check((negative[5] & 255) == 212 && (negative[6] & 255) == 254, "signed incline encoding");
    WorkoutMath.Totals t = new WorkoutMath.Totals();
    for (int i = 0; i <= 60; i++) t.sample(i * 1000, 2, 160, 1000);
    near(t.meters, 26.6666666667);
    near(t.elevationM, 2.66666667);
    check(t.movingMs == 60000, "moving minute");
    t.sample(61000, 3, 0, 0);
    t.sample(62000, 3, 0, 0);
    check(t.movingMs == 60000, "pause excluded");
    t.sample(70000, 2, 160, -300);
    check(t.movingMs == 60000, "gap excluded");
    t.gap();
    t.sample(71000, 2, 160, 0);
    check(t.movingMs == 60000, "reconnect excluded");
    LocalDate sun = LocalDate.of(2026, 11, 1);
    check(WorkoutMath.weekStart(LocalDate.of(2026, 11, 7)).equals(sun), "Sunday start");
    check(WorkoutMath.weekStart(sun).equals(sun), "Sunday inclusive");
    long week = WorkoutMath.midnight(sun.plusDays(7)) - WorkoutMath.midnight(sun);
    check(week == 169L * 3600000, "DST fall week");
    check(
        WorkoutMath.date(Instant.parse("2026-11-04T02:00:00Z").toEpochMilli())
            .equals(LocalDate.of(2026, 11, 3)),
        "local date");
    ArrayList<WorkoutRecord.Point> pts = new ArrayList<>();
    pts.add(new WorkoutRecord.Point(0, 0, 0, 160, 0, 0));
    pts.add(new WorkoutRecord.Point(1, 600000, WorkoutMath.MILE * .5, 160, 0, 0));
    pts.add(new WorkoutRecord.Point(2, 1800000, WorkoutMath.MILE * 1.5, 160, 0, 0));
    pts.add(new WorkoutRecord.Point(3, 2760000, WorkoutMath.MILE * 2.3, 160, 0, 0));
    List<WorkoutMath.Split> splits = WorkoutMath.splits(pts);
    check(splits.size() == 3, "2 full plus partial");
    check(splits.get(0).movingMs == 1200000, "interpolated mile 1");
    check(splits.get(1).movingMs == 1200000, "interpolated mile 2");
    check(splits.get(2).movingMs == 360000, "partial mile");
    near(splits.get(2).meters, WorkoutMath.MILE * .3);
    check(HeartPacket.decode(new byte[] {0, 120}) == 120, "8bit HR");
    check(HeartPacket.decode(new byte[] {1, (byte) 200, 0}) == 200, "16bit HR");
    check(HeartPacket.decode(new byte[] {4, 120}) == 0, "contact lost");
    check(HeartPacket.decode(new byte[] {6, 120}) == 120, "contact detected");
    check(HeartPacket.decode(new byte[] {1, 120}) == 0, "truncated HR");
    check(HeartPacket.decode(new byte[] {0, 0}) == 0, "zero HR");
    WorkoutRecord r = new WorkoutRecord();
    r.profile = "slot-1";
    r.kind = "Walk";
    r.startMs = WorkoutMath.midnight(sun);
    r.meters = WorkoutMath.MILE;
    r.walkMeters = WorkoutMath.MILE;
    r.movingMs = 1200000;
    WorkoutRecord c = r.copy();
    c.profile = "slot-2";
    c.kind = "Run";
    c.walkMeters = 0;
    c.runMeters = c.meters;
    WorkoutRecord guest = r.copy();
    guest.profile = "guest";
    List<WorkoutRecord> all = Arrays.asList(r, c, guest);
    Stats a = Stats.of(all, "slot-1", sun, sun.plusDays(7));
    near(a.meters, WorkoutMath.MILE);
    check(a.count == 1 && a.moving == 1200000, "separate profile totals");
    near(a.walk, WorkoutMath.MILE);
    near(a.run, 0);
    check(Stats.of(all, "slot-1", sun.minusDays(7), sun).count == 0, "exclusive period end");
    check(WorkoutMath.pace(WorkoutMath.MILE, 1200000).equals("20:00"), "pace");
    System.out.println(
        "PASS: bounded local controls; signed grade; pace/distance/elevation/pause/gaps;"
            + " Sunday-local DST; mile splits; HR formats/contact; separate profiles and"
            + " exclusive date ranges.");
  }
}

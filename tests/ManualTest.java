import org.opentread.console.Control;
import org.opentread.console.Protocol;
import org.opentread.console.SessionTotals;

public class ManualTest {
  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }

  public static void run() {
    check(Control.canStart(1, 0, 0, true));
    check(Control.canStart(3, 0, 999, true));
    for (int mode : new int[] {0, 2, 8, 10, 12, 99}) check(!Control.canStart(mode, 0, 0, true));
    check(!Control.canStart(1, 160, 0, true));
    check(!Control.canStart(1, -1, 0, true));
    check(!Control.canStart(1, 0, 1000, true));
    check(!Control.canStart(1, 0, -1, true));
    check(!Control.canStart(1, 0, 0, false));
    check(!Control.needsPause(8, 0));
    check(!Control.needsPause(8, 160));
    check(!Control.needsPause(3, 0));
    check(!Control.needsPause(1, 0));
    check(Control.needsPause(1, 160));
    check(Control.needsPause(3, 160));
    check(Control.needsPause(2, 0));
    check(Control.needsPause(2, 160));
    int[][] plan = Control.startSteps();
    check(
        plan.length == 3
            && plan[0][0] == 95
            && plan[1][0] == 12
            && plan[1][1] == 2
            && plan[2][0] == 0
            && plan[2][1] == 160);
    check(Protocol.hex(Control.write(0, 160), 12).equals("020c020101a00003000001b6"));
    for (int field = 0; field < 128; field++)
      for (int value : new int[] {-1, 0, 1, 2, 3, 80, 160, 1931, 65535}) {
        boolean allowed =
            (field == 0 && value >= 80 && value <= 1931)
                || (field == 1 && value >= -300 && value <= 1500)
                || (field == 98 && value >= 0 && value <= 4)
                || (field == 12 && (value == 2 || value == 3))
                || (field == 95 && value == 0);
        try {
          byte[] p = Control.write(field, value);
          check(allowed);
          int sum = 0;
          for (int i = 0; i < p.length - 1; i++) sum += p[i] & 255;
          check((sum & 255) == (p[p.length - 1] & 255));
        } catch (IllegalArgumentException e) {
          check(!allowed);
        }
      }
    SessionTotals t = new SessionTotals();
    for (int i = 0; i <= 60; i++) t.sample(i * 1000, 2, 160);
    check(t.movingMs == 60000);
    check(Math.abs(t.meters - 26.6666666667) < 0.0001);
    t.sample(61000, 3, 0);
    t.sample(62000, 3, 0);
    check(t.movingMs == 60000);
    t.sample(70000, 2, 160);
    check(t.movingMs == 60000);
    t.gap();
    t.sample(71000, 2, 160);
    check(t.movingMs == 60000);
    t.reset();
    check(t.movingMs == 0 && t.meters == 0);
    System.out.println(
        "PASS: Start gates/order/whitelist; key-out Pause suppression; 60-second distance/time,"
            + " pause, stale gap and reset.");
  }
}

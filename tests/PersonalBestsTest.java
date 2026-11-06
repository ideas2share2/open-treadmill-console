import org.opentread.console.*;
import java.time.*;
import java.util.*;

public final class PersonalBestsTest {
  static void check(boolean b, String msg) {
    if (!b) throw new AssertionError(msg);
  }

  static WorkoutRecord r(String p, LocalDate day, int minutes, double miles) {
    WorkoutRecord r = new WorkoutRecord();
    r.id = r.startMs = WorkoutMath.midnight(day) + 12 * 3600000L;
    r.endMs = r.startMs + minutes * 60000L;
    r.updatedMs = r.endMs;
    r.profile = p;
    r.movingMs = minutes * 60000L;
    r.meters = miles * WorkoutMath.MILE;
    return r;
  }

  public static void run() {
    LocalDate today = LocalDate.of(2026, 11, 3);
    ArrayList<WorkoutRecord> rows = new ArrayList<>();
    for (int i = 3; i >= 1; i--) rows.add(r("slot-1", today.minusDays(i), 10, 1));
    WorkoutRecord peak = r("slot-1", today, 1, .2);
    rows.add(peak);
    rows.add(r("slot-1", today, 5, .5));
    rows.add(r("slot-1", today, 5, .5));
    rows.add(r("slot-2", today, 60, 8));
    PersonalBests b = PersonalBests.of(rows, "slot-1", today);
    check(
        b.currentStreak == 3 && !b.todayDone,
        "yesterday streak alive; two 5min sessions do NOT qualify today");
    check(b.bestStreak == 3 && b.qualifyingDays == 3, "daily dedup/profile separation");
    check(b.fastest.movingMs == 600000, "instant peak/short test not fastest qualifying session");
    check(b.longestTime.profile.equals("slot-1"), "PB profile isolation");
    WorkoutRecord done = r("slot-1", today, 10, 1.5);
    rows.add(done);
    b = PersonalBests.of(rows, "slot-1", today);
    check(
        b.currentStreak == 4 && b.bestStreak == 4 && b.todayDone, "exact 10min boundary qualifies");
    check(b.fastest == done, "fastest sustained qualifying average");
    rows.add(r("slot-1", today, 20, 1));
    b = PersonalBests.of(rows, "slot-1", today);
    check(
        b.currentStreak == 4 && b.qualifyingDays == 4,
        "multiple qualifying sessions count one day");
    check(b.longestTime.movingMs == 1200000, "longest moving time");
    check(b.longestDistance == done, "longest distance independent of time");
    check(
        PersonalBests.of(rows, "slot-1", today.plusDays(1)).currentStreak == 4,
        "today still an opportunity");
    check(
        PersonalBests.of(rows, "slot-1", today.plusDays(2)).currentStreak == 0,
        "missed day resets current");
    check(
        PersonalBests.of(rows, "slot-1", today.plusDays(2)).bestStreak == 4,
        "miss preserves lifetime best");
    WorkoutRecord future = r("slot-1", today.plusDays(1), 100, 10);
    rows.add(future);
    WorkoutRecord unfinished = r("slot-1", today, 100, 10);
    unfinished.endMs = 0;
    rows.add(unfinished);
    WorkoutRecord zero = r("slot-1", today, 0, 0);
    rows.add(zero);
    b = PersonalBests.of(rows, "slot-1", today);
    check(b.longestTime.movingMs == 1200000, "future/unfinished/zero rows excluded");
    check(PersonalBests.of(rows, "guest", today).sessions == 0, "honest empty guest");
    ArrayList<WorkoutRecord> dst = new ArrayList<>();
    for (int i = 0; i < 7; i++) dst.add(r("slot-2", today.minusDays(i), 10, 1));
    PersonalBests c = PersonalBests.of(dst, "slot-2", today);
    check(c.bestStreak == 7 && c.currentStreak == 7, "Central calendar streak crosses fall DST");
    check(c.badges.toString().contains("Week warrior"), "7day milestone");
    ArrayList<WorkoutRecord> leap = new ArrayList<>();
    LocalDate march = LocalDate.of(2028, 3, 1);
    leap.add(r("guest", march.minusDays(2), 10, 1));
    leap.add(r("guest", march.minusDays(1), 10, 1));
    leap.add(r("guest", march, 10, 1));
    check(PersonalBests.of(leap, "guest", march).bestStreak == 3, "leap-day consecutive dates");
    ArrayList<WorkoutRecord> tie = new ArrayList<>();
    WorkoutRecord first = r("slot-1", today.minusDays(1), 10, 1), second = r("slot-1", today, 10, 1);
    tie.add(second);
    tie.add(first);
    check(
        PersonalBests.of(tie, "slot-1", today).longestTime == first,
        "ties retain first achievement despite descending rows");
    System.out.println(
        "PASS: per-user duration/distance/fastest10min PBs; single-session 10min eligibility;"
            + " same-day dedup; current/best streak gaps/DST/leap-day/future filtering; ties and"
            + " milestones.");
  }
}

package org.opentread.console;

import java.time.LocalDate;
import java.util.*;

/** Computed from completed local workout summaries, never from peak-speed commands or mock data. */
public final class PersonalBests {
  public static final long TEN_MINUTES = 600000;
  public final String profile;
  public WorkoutRecord longestTime, longestDistance, fastest;
  public int sessions, currentStreak, bestStreak, qualifyingDays;
  public double totalMeters;
  public long totalMoving;
  public boolean todayDone;
  public LocalDate bestStart, bestEnd;
  public final ArrayList<String> badges = new ArrayList<>();

  private PersonalBests(String p) {
    profile = p;
  }

  public static PersonalBests of(List<WorkoutRecord> rows, String profile, LocalDate today) {
    PersonalBests b = new PersonalBests(profile);
    TreeSet<LocalDate> days = new TreeSet<>();
    for (WorkoutRecord r : rows) {
      if (!profile.equals(r.profile)
          || r.endMs <= 0
          || r.startMs <= 0
          || r.movingMs <= 0
          || !Double.isFinite(r.meters)
          || r.meters < 0) continue;
      LocalDate date = WorkoutMath.date(r.startMs);
      if (date.isAfter(today)) continue;
      b.sessions++;
      b.totalMeters += r.meters;
      b.totalMoving += r.movingMs;
      if (b.longestTime == null
          || r.movingMs > b.longestTime.movingMs
          || (r.movingMs == b.longestTime.movingMs && r.startMs < b.longestTime.startMs))
        b.longestTime = r;
      if (b.longestDistance == null
          || r.meters > b.longestDistance.meters + 0.000001
          || (Math.abs(r.meters - b.longestDistance.meters) < 0.000001
              && r.startMs < b.longestDistance.startMs)) b.longestDistance = r;
      if (r.movingMs >= TEN_MINUTES) {
        days.add(date);
        if (r.meters > 0
            && (b.fastest == null
                || r.avgMph() > b.fastest.avgMph() + 0.000001
                || (Math.abs(r.avgMph() - b.fastest.avgMph()) < 0.000001
                    && r.startMs < b.fastest.startMs))) b.fastest = r;
      }
    }
    b.qualifyingDays = days.size();
    b.todayDone = days.contains(today);
    LocalDate anchor = b.todayDone ? today : today.minusDays(1);
    while (days.contains(anchor)) {
      b.currentStreak++;
      anchor = anchor.minusDays(1);
    }
    LocalDate previous = null, start = null;
    int run = 0;
    for (LocalDate day : days) {
      if (previous != null && previous.plusDays(1).equals(day)) run++;
      else {
        run = 1;
        start = day;
      }
      if (run > b.bestStreak) {
        b.bestStreak = run;
        b.bestStart = start;
        b.bestEnd = day;
      }
      previous = day;
    }
    if (b.qualifyingDays > 0) b.badges.add("Hello, habit · first 10 minutes");
    if (b.totalMeters >= WorkoutMath.MILE) b.badges.add("Ahead of the couch · first mile");
    if (b.bestStreak >= 7) b.badges.add("Week warrior · 7-day streak");
    if (b.bestStreak >= 30) b.badges.add("On a roll · 30-day streak");
    if (b.totalMeters >= 42195) b.badges.add("Marathon total · one workout at a time");
    if (b.totalMeters >= 100 * WorkoutMath.MILE) b.badges.add("Century club · 100 total miles");
    if (b.sessions >= 42) b.badges.add("42 club · the answer was more steps");
    if (b.sessions >= 100) b.badges.add("Triple digits · 100 saved workouts");
    return b;
  }

  public String streakNote() {
    if (currentStreak == 0) return "One saved 10-minute workout starts it.";
    return todayDone ? "Today counts. Nice work." : "Keep it going with 10 minutes today.";
  }
}

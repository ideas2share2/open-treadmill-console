import org.opentread.console.*;

public final class DataFeaturesTest {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void run() {
    GripPulse p = new GripPulse();
    check(p.sample(0) == 0 && p.sample(255) == 0, "no contact/noise");
    check(p.sample(100) == 100 && p.sample(120) == 106, "grip smoothing");
    check(p.sample(0) == 0 && p.sample(150) == 150, "regrip must not retain old average");
    HeartReading r = new HeartReading(94, 108);
    check(
        r.bpm == 108 && r.source.equals("bluetooth") && r.handles == 94,
        "BLE priority preserves handles");
    check(new HeartReading(94, 0).source.equals("handles"), "grip fallback");
    check(new HeartReading(0, 0).source.equals("none"), "no fake reading");
    check(new HeartReading(255, 900).bpm == 0, "bad data recorded");
    byte[] device = {83, 1, 1, 0, 0, 0, 0, 0, 2, 0, 4};
    check(ControllerAuth.supportsField(device, 10), "field10 capability");
    check(!ControllerAuth.supportsField(device, 9), "wrong bit");
    check(!ControllerAuth.supportsField(new byte[6], 10), "short mask");
    device[8] = (byte) 255;
    check(!ControllerAuth.supportsField(device, 10), "invalid mask");
    check(TrackProgress.laps(0) == 0 && TrackProgress.fraction(0) == 0, "track start");
    check(Math.abs(TrackProgress.fraction(TrackProgress.LAP_METERS / 2) - .5) < 1e-6, "half lap");
    check(
        TrackProgress.laps(TrackProgress.LAP_METERS) == 1
            && TrackProgress.fraction(TrackProgress.LAP_METERS) == 0,
        "lap boundary");
    check(TrackProgress.laps(WorkoutMath.MILE) == 4, "four laps per mile");
    check(
        TrackProgress.laps(Double.NaN) == 0 && TrackProgress.fraction(-1) == 0, "invalid distance");
    for (String host : new String[] {"open.spotify.com", "music.youtube.com", "m.youtube.com"}) {
      check(MediaPolicy.audible(true, host), "active video sound blocked");
      check(!MediaPolicy.audible(false, host), "locked/hidden audio allowed");
    }
    for (String host :
        new String[] {
          null,
          "netflix.com",
          "evilspotify.com",
          "open.spotify.com.evil.test",
          "accounts.spotify.com",
          "example.com"
        }) check(!MediaPolicy.audible(true, host), "untrusted audio");
    check(MediaPolicy.provider("netflix").equals("youtube"), "retired source restored");
    check(MediaPolicy.provider(null).equals("youtube"), "missing source");
    check(MediaPolicy.home("music").equals("https://music.youtube.com/"), "music home");
    check(MediaPolicy.home("spotify").equals("https://open.spotify.com/"), "spotify home");
    check(MediaPolicy.searchBase("music").endsWith("search?q="), "music search");
    check(MediaPolicy.searchBase("spotify").endsWith("/search/"), "spotify search");
    System.out.println(
        "PASS: pulse capability/filter/contact loss; source-aware Bluetooth priority/grip fallback;"
            + " quarter-mile wrap/reset; active trusted-site audible playback only.");
  }
}

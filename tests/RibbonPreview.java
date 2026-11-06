import org.opentread.console.*;

/**
 * Exports synthetic scenarios through the production reducer for a local plot preview. No device.
 */
public final class RibbonPreview {
  static void series(String name, RibbonPlot.Series s) {
    System.out.print(
        "\""
            + name
            + "\":{\"min\":"
            + s.min
            + ",\"max\":"
            + s.max
            + ",\"current\":"
            + s.current
            + ",\"points\":[");
    for (int i = 0; i < s.count; i++) {
      if (i > 0) System.out.print(",");
      System.out.print("[" + s.time[i] + "," + s.value[i] + "," + s.segment[i] + "]");
    }
    System.out.print("]}");
  }

  public static void main(String[] args) {
    boolean fast = args.length > 0 && args[0].equals("fast");
    WorkoutRecord r = new WorkoutRecord();
    for (int i = 0; i <= 1800; i++) {
      double mph =
          i < 20 ? .5 : i < 600 ? 3 : i < 960 ? (fast ? 10 : 5) : i < 1200 ? (fast ? 12 : 4) : 3;
      int grade = i < 600 ? 300 : i < 960 ? 500 : 300;
      r.points.add(
          new WorkoutRecord.Point(
              i * 1000L, i * 1000L, 0, (int) Math.round(mph * 160.9344), grade, 0));
    }
    r.movingMs = 1800000;
    RibbonPlot model = new RibbonPlot();
    model.update(r);
    System.out.print("{\"end\":" + model.endMs + ",");
    series("speed", model.speed);
    System.out.print(",");
    series("incline", model.incline);
    System.out.println("}");
  }
}

package org.opentread.console;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Native dashboard from the three approved Console mockups. Single lazy video session. */
public final class MainActivity extends Activity {
  private static final DateTimeFormatter HEADER_CLOCK =
      DateTimeFormatter.ofPattern("EEE, MMM d, yyyy  ·  h:mm:ss a", Locale.US);
  private static final DateTimeFormatter IDLE_CLOCK =
      DateTimeFormatter.ofPattern("h:mm a", Locale.US);
  Ui ui;
  boolean showBests = false, bestsLoading = false;
  PersonalBests bests;
  long bestsRevision = -1, bestsRetryAt = 0;
  LocalDate bestsDay;
  LinearLayout progressOverview;
  RecordsPanel recordsPanel;
  TextView bestsLink;
  long lastLogoTap = 0;
  int logoTaps = 0;

  ConsoleService service;
  ProfileSettings profileSettings;
  final ProfileData[] people = new ProfileData[4];
  AlertDialog profileDialog;
  boolean bound = false, resumed = false, held = false, querying = false;
  Handler handler = new Handler(Looper.getMainLooper());
  String tab = "Workout", filter = "All", range = "Last 30 days", chartKind = "Pace";
  boolean monthly = false;
  LocalDate period = WorkoutMath.weekStart(LocalDate.now(WorkoutMath.ZONE));
  long revision = -1, queryGeneration = 0, selectedId = 0;
  long queryRetryAt, lastClockSecond = -1;
  volatile long detailGeneration;
  boolean uiParked;
  FrameLayout screenHost, idleCover;
  TextView idleClock;
  long lastIdleMinute = -1;
  float awakeBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
  final WakeTouchGuard wakeTouch = new WakeTouchGuard();
  String releasedVideoUri = "", videoSite = "youtube";
  final Runnable evictVideo = () -> releaseVideo();
  List<WorkoutRecord> rows = new ArrayList<>(), historyRows = new ArrayList<>();
  WorkoutRecord detail;
  EmbeddedVideo video;
  FrameLayout videoHost;
  LinearLayout videoCover;
  TextView resumeVideo;
  final VideoGate videoGate = new VideoGate();
  AlertDialog videoPinDialog;
  AlertDialog heartDialog;
  TextView heartDetails;
  LinearLayout phoneAudioPanel;
  int lastControllerMode = -1;

  LinearLayout root,
      header,
      workout,
      history,
      progress,
      dock,
      workDock,
      otherDock,
      body,
      historyDetail,
      historyFilters,
      progressPeople;
  TextView date,
      elapsed,
      distance,
      pace,
      elevation,
      heart,
      calories,
      speed,
      grade,
      fan,
      start,
      status,
      otherStatus,
      kind,
      periodLabel,
      recent,
      progressRange,
      lastMonth,
      detailTitle,
      detailMeta,
      detailMetrics,
      detailMore;
  final java.util.ArrayList<TextView> weekButtons = new java.util.ArrayList<>(),
      monthButtons = new java.util.ArrayList<>();
  TextView[] tabs = new TextView[3], profiles = new TextView[4], smallTotals = new TextView[4];
  TextView speedDown, speedUp, gradeDown, gradeUp, returnButton;
  Charts.Trace liveTrace, detailTrace;
  Charts.MiniTrack miniTrack;
  Charts.Bars smallBars, bigBars;
  ListView list;
  HistoryAdapter adapter;
  TextView histEmpty;
  LinearLayout.LayoutParams mainBodyParams;
  final Runnable tick =
      new Runnable() {
        public void run() {
          if (resumed) {
            update();
            handler.postDelayed(this, uiParked ? 1000 : 500);
          }
        }
      };
  final Runnable startHeld =
      () -> {
        if (held
            && resumed
            && service != null
            && tab.equals("Workout")
            && service.engine.holding
            && service.engine.canStart()) {
          service.engine.userStart();
        }
        held = false;
        if (service != null) service.engine.holding = false;
      };
  final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
          service = ((ConsoleService.LocalBinder) b).service();
          bound = true;
          if (resumed) service.show();
          refreshSelection();
          queryData();
          update();
        }

        public void onServiceDisconnected(ComponentName n) {
          service = null;
          update();
        }
      };

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().setStatusBarColor(Ui.BG);
    getWindow().setNavigationBarColor(Ui.BG);
    immersive();
    ui = new Ui(this);
    profileSettings = new ProfileSettings(this);
    loadProfiles();
    build();
    startForegroundService(new Intent(this, ConsoleService.class));
    bound = bindService(new Intent(this, ConsoleService.class), connection, BIND_AUTO_CREATE);
  }

  void immersive() {
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  LinearLayout.LayoutParams lp(int w, int h) {
    return new LinearLayout.LayoutParams(w < 0 ? w : ui.u(w), h < 0 ? h : ui.u(h));
  }

  void weighted(LinearLayout parent, View view, float weight) {
    parent.addView(view, new LinearLayout.LayoutParams(0, -1, weight));
  }

  TextView text(String t, int size, boolean b) {
    return ui.text(t, size, Ui.TEXT, b);
  }

  TextView button(String t, int size, Runnable run) {
    return ui.button(t, size, 0, Ui.TEXT, run);
  }

  void space(LinearLayout l, int n) {
    l.addView(new View(this), lp(n, n));
  }

  void build() {
    lastClockSecond = -1;
    root = ui.col();
    root.setBackgroundColor(Ui.BG);
    screenHost = new FrameLayout(this);
    setContentView(screenHost);
    screenHost.addView(root, new FrameLayout.LayoutParams(-1, -1));
    buildHeader();
    View border = new View(this);
    border.setBackgroundColor(Ui.LINE);
    root.addView(border, lp(-1, 1));
    body = ui.col();
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    buildWorkout();
    buildHistory();
    buildProgress();
    buildDock();
    showTab(false);
    idleCover = new FrameLayout(this);
    idleCover.setBackgroundColor(android.graphics.Color.BLACK);
    idleCover.setVisibility(View.GONE);
    idleCover.setClickable(true);
    idleCover.setContentDescription("Console idle screen. Tap to wake.");
    idleCover.setOnClickListener(v -> wakeUi());
    idleClock = ui.text("Console\nTap to wake", 30, 0xff757575, false);
    idleClock.setGravity(Gravity.CENTER);
    idleCover.addView(idleClock, new FrameLayout.LayoutParams(-1, -1));
    screenHost.addView(idleCover, new FrameLayout.LayoutParams(-1, -1));
  }

  void buildHeader() {
    header = ui.row();
    ui.pad(header, 28, 9);
    root.addView(header, lp(-1, 74));
    TextView logo = ui.text("open", 48, ui.accent, true);
    logo.setTypeface(Typeface.create(ui.bold, 700, true));
    logo.setOnClickListener(v -> secretTap());
    header.addView(logo, lp(174, -1));
    ui.vline(header);
    String[] names = {"Workout", "History", "Progress"};
    for (int i = 0; i < 3; i++) {
      String s = names[i];
      tabs[i] = button(s, 25, () -> switchTab(s));
      tabs[i].setBackgroundColor(Ui.BG);
      header.addView(tabs[i], lp(158, -1));
    }
    date = text("", 18, false);
    date.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    header.addView(date, new LinearLayout.LayoutParams(0, -1, 1));
    space(header, 20);
    ui.vline(header);
    for (int i = 0; i < 4; i++) {
      String p = ProfileData.IDS[i];
      profiles[i] = button(profileName(p), 21, () -> chooseProfile(p));
      profiles[i].setTag(p);
      profiles[i].setSingleLine(true);
      profiles[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
      profiles[i].setContentDescription(profileName(p) + ". Long press to edit profile.");
      profiles[i].setOnLongClickListener(
          v -> {
            editProfile(p);
            return true;
          });
      header.addView(profiles[i], lp(128, 48));
      if (i < 3) space(header, 10);
    }
  }

  void buildWorkout() {
    workout = ui.row();
    ui.pad(workout, 28, 14);
    body.addView(workout, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout left = ui.col();
    weighted(workout, left, .164f);
    LinearLayout elapsedHeader = ui.row();
    left.addView(elapsedHeader, lp(-1, 40));
    elapsedHeader.addView(text("Elapsed Time", 22, false), new LinearLayout.LayoutParams(0, -1, 1));
    miniTrack = new Charts.MiniTrack(this, ui);
    elapsedHeader.addView(miniTrack, lp(52, 32));
    elapsed = text("00:00", 76, true);
    elapsed.setMaxLines(1);
    elapsed.setAutoSizeTextTypeUniformWithConfiguration(
        ui.u(44), ui.u(76), 2, android.util.TypedValue.COMPLEX_UNIT_PX);
    left.addView(elapsed, lp(-1, 100));
    ui.line(left);
    left.addView(text("Distance", 22, false), lp(-1, 38));
    distance = text("0.00 mi", 62, true);
    distance.setMaxLines(1);
    distance.setAutoSizeTextTypeUniformWithConfiguration(
        ui.u(36), ui.u(62), 2, android.util.TypedValue.COMPLEX_UNIT_PX);
    left.addView(distance, lp(-1, 100));
    ui.line(left);
    pace = metric(left, "Pace", "— /mi");
    elevation = metric(left, "Est. Elev. Gain", "0 ft");
    ui.line(left);
    calories = metric(left, "Est. active cal", "—");
    calories.setTextColor(ui.accent);
    calories.setOnClickListener(
        v -> editProfile(service == null ? "slot-1" : service.recorder.profile));
    heart = metric(left, "Heart Rate", "Connect");
    heart.setTextColor(ui.accent);
    heart.setOnClickListener(v -> sensorStatus());
    kind = ui.text("Walk / Run  ·  Auto", 16, ui.accent, true);
    kind.setGravity(Gravity.CENTER);
    left.addView(kind, lp(-1, 42));
    status = ui.text("Connecting…", 13, Ui.MUTED, false);
    status.setMaxLines(3);
    status.setOnClickListener(
        v -> {
          if (service != null) service.engine.request();
        });
    left.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
    ui.vline(workout);
    LinearLayout center = ui.col();
    weighted(workout, center, .603f);
    videoHost = new FrameLayout(this);
    videoHost.setBackground(ui.shape(Ui.PANEL, Ui.LINE, ui.u(10)));
    center.addView(videoHost, new LinearLayout.LayoutParams(-1, 0, 1));
    videoCover = ui.col();
    videoCover.setGravity(Gravity.CENTER);
    videoCover.setBackgroundColor(Ui.PANEL);
    videoHost.addView(videoCover, new FrameLayout.LayoutParams(-1, -1));
    videoCover.addView(ui.text("Make time for yourself.", 34, Ui.TEXT, true), lp(-2, 64));
    phoneAudioPanel = null;
    videoCover.addView(
        ui.text("Your video. Your music. Your pace.", 21, Ui.MUTED, false), lp(-2, 45));
    resumeVideo = ui.button("Continue media", 21, ui.accent, Ui.BG, () -> openVideo("resume"));
    resumeVideo.setVisibility(
        video == null && releasedVideoUri.isEmpty() ? View.GONE : View.VISIBLE);
    videoCover.addView(resumeVideo, lp(220, 52));
    LinearLayout choices = ui.row();
    space(videoCover, 20);
    videoCover.addView(choices, lp(-2, 62));
    choices.addView(
        ui.button("YouTube", 23, ui.accent, Ui.BG, () -> openVideo("youtube")), lp(180, -1));
    space(choices, 20);
    choices.addView(button("YT Music", 23, () -> openVideo("music")), lp(180, -1));
    space(choices, 20);
    choices.addView(button("Spotify", 23, () -> openVideo("spotify")), lp(180, -1));
    space(videoCover, 12);
    videoCover.addView(button("Phone audio · Bluetooth", 20, () -> showPhoneAudio()), lp(320, 52));
    videoCover.addView(
        ui.text("PIN required · Media loads only when you choose it.", 14, Ui.MUTED, false),
        lp(-2, 50));
    liveTrace = new Charts.Trace(this, ui, true);
    center.addView(liveTrace, lp(-1, 100));
    ui.vline(workout);
    LinearLayout right = ui.col();
    weighted(workout, right, .215f);
    LinearLayout periodTabs = ui.row();
    right.addView(periodTabs, lp(-1, 48));
    weighted(periodTabs, periodButton(false), 1);
    weighted(periodTabs, periodButton(true), 1);
    LinearLayout dateRow = ui.row();
    right.addView(dateRow, lp(-1, 63));
    dateRow.addView(button("‹", 28, () -> shiftPeriod(-1)), lp(42, -1));
    periodLabel = text("", 20, true);
    periodLabel.setGravity(Gravity.CENTER);
    dateRow.addView(periodLabel, new LinearLayout.LayoutParams(0, -1, 1));
    dateRow.addView(button("›", 28, () -> shiftPeriod(1)), lp(42, -1));
    smallBars = new Charts.Bars(this, ui, true);
    right.addView(smallBars, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < 4; i++) {
      LinearLayout row = ui.row();
      right.addView(row, lp(-1, 32));
      TextView label = ui.text(people[i].name, 16, ProfileTheme.accent(people[i].id), true);
      label.setSingleLine(true);
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
      smallTotals[i] = text("0.00 mi · 0 workouts", 15, true);
      smallTotals[i].setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
      row.addView(smallTotals[i], lp(-2, -1));
    }
    ui.line(right);
    right.addView(
        ui.button("View history  ›", 21, 0, ui.accent, () -> switchTab("History")), lp(-1, 48));
    recent = text("No saved workouts yet", 16, false);
    recent.setMaxLines(3);
    right.addView(recent, lp(-1, 80));
    bestsLink = ui.button("Your bests & streaks  ›", 18, 0, ui.accent, () -> openBests());
    right.addView(bestsLink, lp(-1, 44));
  }

  TextView metric(LinearLayout p, String label, String val) {
    LinearLayout r = ui.row();
    p.addView(r, lp(-1, 48));
    TextView k = text(label, 18, false);
    r.addView(k, new LinearLayout.LayoutParams(0, -1, 1));
    TextView v = text(val, 19, true);
    v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    r.addView(v, lp(-2, -1));
    return v;
  }

  void buildHistory() {
    history = ui.row();
    ui.pad(history, 32, 20);
    body.addView(history, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout left = ui.col();
    weighted(history, left, .51f);
    left.addView(text("Your activity", 50, true), lp(-1, 66));
    left.addView(text("Every run. Every walk.", 25, false), lp(-1, 40));
    historyFilters = ui.row();
    left.addView(historyFilters, lp(-1, 76));
    for (String f : new String[] {"All", "Walks", "Runs"}) {
      TextView b =
          button(
              f,
              21,
              () -> {
                filter = f.equals("All") ? "All" : f.equals("Walks") ? "Walk" : "Run";
                refreshHistoryFilters();
                queryData();
              });
      historyFilters.addView(b, lp(124, 48));
    }
    historyFilters.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    TextView r = button(range + "  ▾", 20, () -> chooseRange());
    historyFilters.addView(r, lp(240, 48));
    ui.line(left);
    TextView headings =
        text("Date                 Workout                Distance           Time", 20, true);
    left.addView(headings, lp(-1, 42));
    FrameLayout table = new FrameLayout(this);
    left.addView(table, new LinearLayout.LayoutParams(-1, 0, 1));
    list = new ListView(this);
    list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));
    list.setDividerHeight(ui.u(1));
    list.setCacheColorHint(Ui.BG);
    adapter = new HistoryAdapter();
    list.setAdapter(adapter);
    list.setOnItemClickListener((p, v, pos, id) -> selectWorkout(historyRows.get(pos).id));
    table.addView(list, new FrameLayout.LayoutParams(-1, -1));
    histEmpty =
        ui.text(
            "Your saved walks and runs will appear here.\n\n"
                + "History starts with this version of Console.",
            21,
            Ui.MUTED,
            false);
    histEmpty.setGravity(Gravity.CENTER);
    table.addView(histEmpty, new FrameLayout.LayoutParams(-1, -1));
    ui.vline(history);
    historyDetail = ui.col();
    weighted(history, historyDetail, .46f);
    detailTitle = text("Choose a workout", 44, true);
    historyDetail.addView(detailTitle, lp(-1, 62));
    detailMeta = text("", 22, false);
    historyDetail.addView(detailMeta, lp(-1, 40));
    detailMetrics = text("", 50, true);
    historyDetail.addView(detailMetrics, lp(-1, 100));
    detailMore = text("", 20, false);
    historyDetail.addView(detailMore, lp(-1, 55));
    ui.line(historyDetail);
    LinearLayout chartTabs = ui.row();
    historyDetail.addView(chartTabs, lp(-1, 48));
    for (String s : new String[] {"Pace", "Incline", "Heart rate", "Splits"})
      weighted(
          chartTabs,
          ui.button(
              s,
              21,
              0,
              Ui.TEXT,
              () -> {
                chartKind = s;
                refreshChartTabs(chartTabs);
                detailTrace.data(detail, s);
              }),
          1);
    detailTrace = new Charts.Trace(this, ui, false);
    historyDetail.addView(detailTrace, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView info = ui.text("Select a saved workout to see details.", 20, Ui.MUTED, false);
    info.setTag("info");
    historyDetail.addView(info, lp(-1, 90));
    refreshChartTabs(chartTabs);
    refreshHistoryFilters();
  }

  void refreshChartTabs(LinearLayout l) {
    for (int i = 0; i < l.getChildCount(); i++) {
      TextView t = (TextView) l.getChildAt(i);
      boolean on = t.getText().toString().equals(chartKind);
      t.setBackground(ui.shape(on ? ui.accent : 0, Ui.LINE, ui.u(12)));
      t.setTextColor(on ? Ui.BG : Ui.TEXT);
    }
  }

  void refreshHistoryFilters() {
    if (historyFilters == null) return;
    for (int i = 0; i < 3; i++) {
      TextView t = (TextView) historyFilters.getChildAt(i);
      String f = i == 0 ? "All" : i == 1 ? "Walk" : "Run";
      boolean on = filter.equals(f);
      t.setTextColor(on ? Ui.BG : Ui.TEXT);
      t.setBackground(ui.shape(on ? ui.accent : 0, Ui.LINE, ui.u(12)));
    }
    ui.set((TextView) historyFilters.getChildAt(4), range + "  ▾");
  }

  class HistoryAdapter extends BaseAdapter {
    public int getCount() {
      return historyRows.size();
    }

    public Object getItem(int i) {
      return historyRows.get(i);
    }

    public long getItemId(int i) {
      return historyRows.get(i).id;
    }

    public View getView(int pos, View recycled, android.view.ViewGroup parent) {
      LinearLayout row;
      if (recycled instanceof LinearLayout) row = (LinearLayout) recycled;
      else {
        row = ui.row();
        ui.pad(row, 16, 8);
        for (int i = 0; i < 4; i++) {
          TextView t = text("", i == 1 ? 22 : 20, i == 1);
          row.addView(
              t, new LinearLayout.LayoutParams(0, ui.u(62), i == 0 ? 1.1f : i == 1 ? 1.3f : 1));
        }
      }
      WorkoutRecord r = historyRows.get(pos);
      String date =
          WorkoutMath.date(r.startMs).format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
      if (pos == 0
          || WorkoutMath.date(historyRows.get(pos - 1).startMs).getMonth()
              != WorkoutMath.date(r.startMs).getMonth())
        date =
            WorkoutMath.date(r.startMs).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
                + "\n"
                + WorkoutMath.date(r.startMs).getDayOfMonth();
      String[] values = {
        date, r.kind, fmt("%.2f mi", r.meters / WorkoutMath.MILE), WorkoutMath.duration(r.movingMs)
      };
      for (int i = 0; i < 4; i++) ui.set((TextView) row.getChildAt(i), values[i]);
      row.setBackgroundColor(r.id == selectedId ? ui.selection : Ui.BG);
      return row;
    }
  }

  void chooseRange() {
    new AlertDialog.Builder(this)
        .setTitle("Workout dates")
        .setItems(
            new String[] {"Last 30 days", "This month", "All time"},
            (d, n) -> {
              range = new String[] {"Last 30 days", "This month", "All time"}[n];
              refreshHistoryFilters();
              queryData();
            })
        .show();
  }

  void buildProgress() {
    progress = ui.col();

    body.addView(progress, new LinearLayout.LayoutParams(-1, -1));
    progressOverview = ui.col();
    ui.pad(progressOverview, 32, 20);
    progress.addView(progressOverview, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout top = ui.row();
    progressOverview.addView(top, lp(-1, 80));
    TextView title = text("A little better, every week.", 44, true);
    top.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
    top.addView(periodButton(false), lp(135, 48));
    top.addView(periodButton(true), lp(135, 48));
    space(top, 25);
    top.addView(button("‹", 28, () -> shiftPeriod(-1)), lp(48, 48));
    progressRange = text("", 24, true);
    progressRange.setGravity(Gravity.CENTER);
    top.addView(progressRange, lp(280, -1));
    top.addView(button("›", 28, () -> shiftPeriod(1)), lp(48, 48));
    progressPeople = ui.row();
    progressOverview.addView(progressPeople, lp(-1, 210));
    ui.line(progressOverview);
    LinearLayout chart = ui.row();
    progressOverview.addView(chart, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout labels = ui.col();
    chart.addView(labels, lp(295, -1));
    labels.addView(text("Miles together", 34, true), lp(-1, 60));
    labels.addView(ui.text("Sunday–Saturday · device local time", 16, Ui.MUTED, false), lp(-1, 48));
    for (ProfileData person : people)
      labels.addView(
          ui.text("■  " + person.name, 21, ProfileTheme.accent(person.id), true), lp(-1, 40));
    bigBars = new Charts.Bars(this, ui, false);
    chart.addView(bigBars, new LinearLayout.LayoutParams(0, -1, 1));
    ui.line(progressOverview);
    LinearLayout bottom = ui.row();
    progressOverview.addView(bottom, lp(-1, 60));
    lastMonth = text("Last complete month · No records yet", 20, false);
    bottom.addView(lastMonth, new LinearLayout.LayoutParams(0, -1, 1));
    bottom.addView(
        ui.button("Your bests & streaks  ›", 20, ui.accent, Ui.BG, () -> openBests()), lp(270, -1));
    space(bottom, 12);
    bottom.addView(
        ui.button("History  ›", 20, 0, ui.accent, () -> switchTab("History")), lp(145, -1));
    recordsPanel = null;

    applyBestsMode();
  }

  void buildDock() {
    View border = new View(this);
    border.setBackgroundColor(Ui.LINE);
    root.addView(border, lp(-1, 1));
    dock = ui.col();
    root.addView(dock, lp(-1, 148));
    workDock = ui.row();
    ui.pad(workDock, 28, 17);
    dock.addView(workDock, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout sp = ui.col();
    weighted(workDock, sp, 1.05f);
    TextView spl = text("Speed", 20, false);
    spl.setGravity(Gravity.CENTER);
    sp.addView(spl, lp(-1, 28));
    LinearLayout spr = ui.row();
    sp.addView(spr, new LinearLayout.LayoutParams(-1, 0, 1));
    speedDown =
        button(
            "−",
            42,
            () -> {
              if (service != null) service.engine.speedStep(false);
            });
    spr.addView(speedDown, lp(82, 64));
    speed = text("— mph", 49, true);
    speed.setGravity(Gravity.CENTER);
    spr.addView(speed, new LinearLayout.LayoutParams(0, -1, 1));
    speedUp =
        button(
            "+",
            42,
            () -> {
              if (service != null) service.engine.speedStep(true);
            });
    spr.addView(speedUp, lp(82, 64));
    ui.vline(workDock);
    LinearLayout gr = ui.col();
    weighted(workDock, gr, 1.05f);
    TextView gl = text("Incline", 20, false);
    gl.setGravity(Gravity.CENTER);
    gr.addView(gl, lp(-1, 28));
    LinearLayout grr = ui.row();
    gr.addView(grr, new LinearLayout.LayoutParams(-1, 0, 1));
    gradeDown =
        button(
            "−",
            42,
            () -> {
              if (service != null) service.engine.gradeStep(false);
            });
    grr.addView(gradeDown, lp(82, 64));
    grade = text("— %", 49, true);
    grade.setGravity(Gravity.CENTER);
    grr.addView(grade, new LinearLayout.LayoutParams(0, -1, 1));
    gradeUp =
        button(
            "+",
            42,
            () -> {
              if (service != null) service.engine.gradeStep(true);
            });
    grr.addView(gradeUp, lp(82, 64));
    ui.vline(workDock);
    fan =
        button(
            "Fan",
            23,
            () -> {
              if (service != null) service.engine.fanStep();
            });
    weighted(workDock, fan, .45f);
    ui.vline(workDock);
    start = ui.button("Hold to start", 25, ui.accent, Ui.BG, () -> {});
    weighted(workDock, start, .6f);
    start.setOnTouchListener((v, e) -> startTouch(e));
    space(workDock, 24);
    TextView stop = ui.button("■  Stop", 28, Ui.RED, Ui.TEXT, () -> stop());
    weighted(workDock, stop, .57f);
    otherDock = ui.row();
    ui.pad(otherDock, 32, 24);
    dock.addView(otherDock, new LinearLayout.LayoutParams(-1, -1));
    otherStatus = text("Workout paused", 29, true);
    otherDock.addView(otherStatus, new LinearLayout.LayoutParams(0, -1, 1));
    returnButton =
        ui.button("▶  Return to workout", 27, ui.accent, Ui.BG, () -> switchTab("Workout"));
    otherDock.addView(returnButton, lp(420, -1));
    space(otherDock, 24);
    otherDock.addView(ui.button("■  Stop", 28, Ui.RED, Ui.TEXT, () -> stop()), lp(220, -1));
  }

  boolean startTouch(MotionEvent event) {
    if (service == null) return true;
    ControllerEngine e = service.engine;
    int a = event.getActionMasked();
    if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) {
      cancelHold();
      return true;
    }
    if (a == MotionEvent.ACTION_DOWN) {
      if (e.fresh() && e.mode == 2) {
        cancelHold();
        e.userStop();
      } else if (e.canStart()) {
        held = true;
        e.holding = true;
        ui.set(start, "Keep holding…");
        handler.postDelayed(startHeld, 2000);
      }
      return true;
    }
    if (a == MotionEvent.ACTION_UP
        || a == MotionEvent.ACTION_CANCEL
        || (a == MotionEvent.ACTION_MOVE
            && (event.getX() < 0
                || event.getY() < 0
                || event.getX() > start.getWidth()
                || event.getY() > start.getHeight()))) {
      cancelHold();
    }
    return true;
  }

  void cancelHold() {
    held = false;
    handler.removeCallbacks(startHeld);
    if (service != null) service.engine.holding = false;
  }

  void stop() {
    cancelHold();
    if (service == null) return;
    service.recorder.requestFinish();
    service.engine.userFinish();
    lockVideo();
  }

  void switchTab(String s) {
    if (tab.equals(s)) return;
    cancelHold();
    if (!s.equals("Workout") && service != null) service.engine.userStop();
    tab = s;
    if (video != null) {
      video.visible(videoGate.unlocked() && resumed && s.equals("Workout"));
    }
    showTab(true);
  }

  void showTab(boolean query) {
    workout.setVisibility(tab.equals("Workout") ? View.VISIBLE : View.GONE);
    history.setVisibility(tab.equals("History") ? View.VISIBLE : View.GONE);
    progress.setVisibility(tab.equals("Progress") ? View.VISIBLE : View.GONE);
    workDock.setVisibility(tab.equals("Workout") ? View.VISIBLE : View.GONE);
    otherDock.setVisibility(tab.equals("Workout") ? View.GONE : View.VISIBLE);
    for (TextView t : tabs) {
      boolean selected = t.getText().toString().equals(tab);
      t.setTextColor(Ui.TEXT);
      t.setBackground(ui.tab(selected));
      t.setTypeface(selected ? ui.bold : ui.regular);
    }
    if (query) queryData();
  }

  void openVideo(String which) {
    if (!resumed) return;
    if (!videoGate.unlocked()) {
      if (videoPinDialog != null && videoPinDialog.isShowing()) return;
      final EditText pin = new EditText(this);
      pin.setInputType(
          android.text.InputType.TYPE_CLASS_NUMBER
              | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
      pin.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(4)});
      pin.setSingleLine(true);
      pin.setTextSize(26);
      pin.setGravity(Gravity.CENTER);
      pin.setHint("4-digit PIN");
      videoPinDialog =
          new AlertDialog.Builder(this)
              .setTitle("Media PIN")
              .setView(pin)
              .setPositiveButton("Unlock", null)
              .setNegativeButton("Cancel", null)
              .create();
      videoPinDialog.setOnShowListener(
          d -> {
            videoPinDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui.accent);
            videoPinDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ui.accent);
            videoPinDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      if (videoGate.submit(pin.getText().toString())) {
                        videoPinDialog.dismiss();
                        showVideo(which);
                      } else {
                        pin.setText("");
                        pin.setError("Try again");
                      }
                    });
            pin.requestFocus();
            videoPinDialog
                .getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
          });
      videoPinDialog.show();
      return;
    }
    showVideo(which);
  }

  void showVideo(String which) {
    if (!resumed || !videoGate.unlocked()) return;
    if (video != null && video.failed) {
      releaseVideo();
      openVideo(which);
      return;
    }
    boolean created = video == null;
    if (video == null) {
      try {
        video = new EmbeddedVideo(this, () -> showPhoneAudio());
        if (video.failed) {
          releaseVideo();
          Toast.makeText(
                  this,
                  "Video could not start. Treadmill controls remain available.",
                  Toast.LENGTH_LONG)
              .show();
          return;
        }
        videoHost.addView(video, 0, new FrameLayout.LayoutParams(-1, -1));
      } catch (Exception e) {
        Toast.makeText(this, "Video could not start", Toast.LENGTH_LONG).show();
        lockVideo();
        return;
      }
    } else if (video.getParent() != videoHost) {
      if (video.getParent() instanceof android.view.ViewGroup)
        ((android.view.ViewGroup) video.getParent()).removeView(video);
      videoHost.addView(video, 0, new FrameLayout.LayoutParams(-1, -1));
    }
    videoCover.setVisibility(View.GONE);
    video.setVisibility(View.VISIBLE);
    video.visible(tab.equals("Workout"));
    try {
      if (!"resume".equals(which)) {
        videoSite = which;
        video.open(which);
      } else if (created) video.restore(releasedVideoUri, videoSite);
    } catch (RuntimeException stopped) {
      releaseVideo();
      Toast.makeText(this, "Media stopped. Choose a source to retry.", Toast.LENGTH_LONG).show();
    }
  }

  void lockVideo() {
    hidePhoneAudio();
    videoGate.lock();
    if (videoPinDialog != null && videoPinDialog.isShowing()) videoPinDialog.dismiss();
    if (video != null) {
      video.visible(false);
      video.setVisibility(View.GONE);
    }
    if (videoCover != null) {
      videoCover.setVisibility(View.VISIBLE);
      resumeVideo.setVisibility(
          video == null && releasedVideoUri.isEmpty() ? View.GONE : View.VISIBLE);
    }
  }

  void releaseVideo() {
    if (phoneAudioPanel != null) lockVideo();
    if (video != null) {
      videoSite = video.site;
      if (video.current != null && video.current.startsWith("https://"))
        releasedVideoUri = video.current;
      lockVideo();
      EmbeddedVideo old = video;
      video = null;
      if (old.getParent() instanceof android.view.ViewGroup)
        ((android.view.ViewGroup) old.getParent()).removeView(old);
      old.close();
    }
    if (resumeVideo != null)
      resumeVideo.setVisibility(releasedVideoUri.isEmpty() ? View.GONE : View.VISIBLE);
  }

  void hidePhoneAudio() {
    if (phoneAudioPanel == null) return;
    if (phoneAudioPanel.getParent() instanceof android.view.ViewGroup)
      ((android.view.ViewGroup) phoneAudioPanel.getParent()).removeView(phoneAudioPanel);
    phoneAudioPanel = null;
  }

  void showPhoneAudio() {
    if (!resumed) return;
    // Release the single browser before using external audio; no second player or polling service.
    releaseVideo();
    lockVideo();
    phoneAudioPanel = ui.col();
    phoneAudioPanel.setGravity(Gravity.CENTER);
    phoneAudioPanel.setBackgroundColor(Ui.PANEL);
    ui.pad(phoneAudioPanel, 28, 20);
    videoHost.addView(phoneAudioPanel, new FrameLayout.LayoutParams(-1, -1));
    videoCover.setVisibility(View.GONE);
    phoneAudioPanel.addView(ui.text("♪  Phone audio", 34, ui.accent, true), lp(-2, 64));
    TextView help =
        ui.text(
            "Play music, podcasts or audiobooks from your phone.\n\n"
                + "Hold the physical Bluetooth Audio button for 3 seconds until it flashes, then"
                + " pair from your phone’s Bluetooth settings. For a previously paired phone, press"
                + " the button once.\n\n"
                + "Use your phone for play, pause and volume. Connection status and song titles are"
                + " managed by the speaker receiver, so they aren’t available to Console.",
            20,
            Ui.TEXT,
            false);
    help.setGravity(Gravity.CENTER);
    phoneAudioPanel.addView(help, lp(-1, 260));
    phoneAudioPanel.addView(
        button(
            "Disconnect & return to media",
            21,
            () -> {
              new AlertDialog.Builder(this)
                  .setTitle("Return to screen audio")
                  .setMessage(
                      "Pause your phone’s audio and disconnect the treadmill in your phone’s"
                          + " Bluetooth settings, then return here. This keeps phone audio from"
                          + " overlapping with YouTube or music on the screen.")
                  .setPositiveButton(
                      "I’ve disconnected",
                      (d, n) -> {
                        hidePhoneAudio();
                        videoCover.setVisibility(View.VISIBLE);
                        openVideo("youtube");
                      })
                  .setNegativeButton("Keep phone audio", null)
                  .show();
            }),
        lp(380, 58));
  }

  void refreshSelection() {
    String p = service == null ? "slot-1" : service.recorder.profile;
    for (TextView b : profiles) {
      boolean on = p.equals(b.getTag());
      b.setTextColor(on ? Ui.BG : Ui.TEXT);
      b.setBackground(ui.shape(on ? ui.accent : 0, on ? ui.accent : Ui.MUTED, ui.u(13)));
    }
  }

  boolean profileChangeReady() {
    if (service == null || !service.engine.fresh()) {
      Toast.makeText(this, "Reconnect the treadmill before changing users", Toast.LENGTH_LONG)
          .show();
      return false;
    }
    if (service.engine.motionPossible || service.engine.speedRaw > 5) {
      Toast.makeText(this, "Pause the treadmill before changing users", Toast.LENGTH_SHORT).show();
      return false;
    }
    return true;
  }

  void chooseProfile(String p) {
    if (service == null || service.recorder.profile.equals(p)) return;
    if (!profileChangeReady()) return;
    Runnable change =
        () -> {
          if (!profileChangeReady()) return;
          cancelHold();
          service.recorder.finish(System.currentTimeMillis(), "Changed user");
          service.recorder.setProfile(p);
          rebuildDashboard();
        };
    if (service.recorder.hasActive())
      new AlertDialog.Builder(this)
          .setTitle("Finish this workout?")
          .setMessage("Save the paused workout before switching to " + profileName(p) + ".")
          .setPositiveButton("Save & switch", (d, n) -> change.run())
          .setNegativeButton("Cancel", null)
          .show();
    else change.run();
  }

  void loadProfiles() {
    for (int i = 0; i < 4; i++) people[i] = profileSettings.get(ProfileData.IDS[i]);
  }

  String profileName(String id) {
    int i = ProfileData.index(id);
    return i < 0 ? id : people[i].name;
  }

  void rebuildDashboard() {
    cancelHold();
    lockVideo();
    releaseVideo();
    ui = new Ui(this);
    weekButtons.clear();
    monthButtons.clear();
    build();
    selectedId = 0;
    detail = null;
    bestsRevision = -1;
    refreshDetail();
    refreshSelection();
    queryData();
    update();
  }

  boolean profileEditReady() {
    if (!resumed || isFinishing() || isDestroyed() || !profileChangeReady()) return false;
    if (service.recorder.hasActive()) {
      Toast.makeText(this, "Stop and save this workout before editing profiles", Toast.LENGTH_LONG)
          .show();
      return false;
    }
    return true;
  }

  void editProfile(String id) {
    if ((profileDialog != null && profileDialog.isShowing()) || !profileEditReady()) return;
    profileDialog =
        ProfileEditor.show(
            this,
            ui,
            profileSettings,
            id,
            () -> profileEditReady(),
            () -> {
              loadProfiles();
              rebuildDashboard();
            });
    profileDialog.setOnDismissListener(d -> profileDialog = null);
  }

  TextView periodButton(boolean month) {
    TextView b = button(month ? "Month" : "Week", 20, () -> setPeriod(month));
    (month ? monthButtons : weekButtons).add(b);
    return b;
  }

  void setPeriod(boolean month) {
    monthly = month;
    LocalDate today = LocalDate.now(WorkoutMath.ZONE);
    period = month ? today.withDayOfMonth(1) : WorkoutMath.weekStart(today);
    queryData();
  }

  void shiftPeriod(int n) {
    LocalDate next = monthly ? period.plusMonths(n) : period.plusWeeks(n),
        today = LocalDate.now(WorkoutMath.ZONE);
    LocalDate limit = monthly ? today.withDayOfMonth(1) : WorkoutMath.weekStart(today);
    if (next.isAfter(limit)) return;
    period = next;
    queryData();
  }

  LocalDate periodEnd() {
    return monthly ? period.plusMonths(1) : period.plusWeeks(1);
  }

  LocalDate historyFrom() {
    LocalDate t = LocalDate.now(WorkoutMath.ZONE);
    return range.equals("This month")
        ? t.withDayOfMonth(1)
        : range.equals("All time") ? LocalDate.of(1970, 1, 1) : t.minusDays(29);
  }

  void queryData() {
    if (service == null || SystemClock.elapsedRealtime() < queryRetryAt) return;
    if (querying) {
      queryGeneration++;
      return;
    }
    querying = true;
    long generation = ++queryGeneration;
    WorkoutStore store = service.recorder.store;
    String profile = service.recorder.profile, k = filter;
    LocalDate a = period,
        b = periodEnd(),
        hf = historyFrom(),
        today = LocalDate.now(WorkoutMath.ZONE),
        last = today.withDayOfMonth(1).minusMonths(1);
    LocalDate from = a.isBefore(last) ? a : last;
    if (hf.isBefore(from)) from = hf;
    long lo = WorkoutMath.midnight(from), hi = WorkoutMath.midnight(today.plusDays(1));
    store.execute(
        () -> {
          try {
            final long loadedRevision = store.revision.get();
            List<WorkoutRecord> all = store.list(null, lo, hi, "All");
            ArrayList<WorkoutRecord> hist = new ArrayList<>();
            for (WorkoutRecord r : all)
              if (r.profile.equals(profile)
                  && !WorkoutMath.date(r.startMs).isBefore(hf)
                  && (k.equals("All") || r.kind.equals(k))) hist.add(r);
            handler.post(
                () -> {
                  querying = false;
                  if (service == null || isFinishing() || isDestroyed()) return;
                  if (generation != queryGeneration) {
                    queryData();
                    return;
                  }
                  rows = all;
                  queryRetryAt = 0;
                  historyRows = hist;
                  revision = loadedRevision;
                  boolean present = false;
                  for (WorkoutRecord wr : hist) if (wr.id == selectedId) present = true;
                  if (!present) {
                    selectedId = 0;
                    detail = null;
                    refreshDetail();
                  }
                  adapter.notifyDataSetChanged();
                  histEmpty.setVisibility(hist.isEmpty() ? View.VISIBLE : View.GONE);
                  if (tab.equals("History")) {
                    if (selectedId == 0 && !hist.isEmpty()) selectWorkout(hist.get(0).id);
                    else if (detail != null && detail.endMs == 0) selectWorkout(detail.id);
                  }
                  refreshStats(a, b, last);
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  querying = false;
                  queryRetryAt = SystemClock.elapsedRealtime() + 30000;
                  if (isFinishing() || isDestroyed()) return;
                  Toast.makeText(this, "Workout records could not be loaded", Toast.LENGTH_LONG)
                      .show();
                });
          }
        });
  }

  void refreshStats(LocalDate a, LocalDate b, LocalDate last) {
    for (TextView t : weekButtons) {
      t.setBackground(ui.shape(!monthly ? ui.accent : 0, Ui.LINE, ui.u(13)));
      t.setTextColor(!monthly ? Ui.BG : Ui.TEXT);
    }
    for (TextView t : monthButtons) {
      t.setBackground(ui.shape(monthly ? ui.accent : 0, Ui.LINE, ui.u(13)));
      t.setTextColor(monthly ? Ui.BG : Ui.TEXT);
    }
    String label =
        monthly
            ? a.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
            : a.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                + " – "
                + b.minusDays(1).format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
    ui.set(periodLabel, label);
    ui.set(progressRange, label);
    smallBars.data(rows, a, b);
    if (tab.equals("Progress")) bigBars.data(rows, a, b);
    for (int i = 0; i < 4; i++) ui.set(smallTotals[i], Stats.of(rows, people[i].id, a, b).small());
    StringBuilder r = new StringBuilder();
    int n = 0;
    for (WorkoutRecord w : historyRows) {
      if (n++ >= 2) break;
      r.append(WorkoutMath.date(w.startMs).format(DateTimeFormatter.ofPattern("MMM d", Locale.US)))
          .append(" · ")
          .append(w.kind)
          .append(" · ")
          .append(fmt("%.2f mi", w.meters / WorkoutMath.MILE))
          .append("\n");
    }
    ui.set(recent, r.length() > 0 ? r.toString().trim() : "No saved workouts yet");
    if (!tab.equals("Progress") || showBests) return;
    progressPeople.removeAllViews();
    StringBuilder monthSummary = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      if (i > 0) ui.vline(progressPeople);
      personSummary(
          people[i].name, Stats.of(rows, people[i].id, a, b), ProfileTheme.accent(people[i].id));
      if (i > 0) monthSummary.append("  ·  ");
      monthSummary
          .append(people[i].name)
          .append(" ")
          .append(
              fmt(
                  "%.2f mi",
                  Stats.of(rows, people[i].id, last, last.plusMonths(1)).meters
                      / WorkoutMath.MILE));
    }
    ui.set(
        lastMonth,
        "Last complete month · "
            + last.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
            + "\n"
            + monthSummary);
  }

  void personSummary(String p, Stats s, int color) {
    LinearLayout box = ui.col();
    weighted(progressPeople, box, 1);
    TextView name = ui.text("■  " + p, 23, color, true);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    box.addView(name, lp(-1, 38));
    box.addView(text(fmt("%.2f mi", s.meters / WorkoutMath.MILE), 42, true), lp(-1, 60));
    box.addView(
        text(s.count + " workouts · " + WorkoutMath.duration(s.moving), 18, false), lp(-1, 34));
    box.addView(
        text("Avg pace  " + WorkoutMath.pace(s.meters, s.moving) + " /mi", 18, false), lp(-1, 34));
    box.addView(
        text(
            fmt("Walk %.2f · Run %.2f mi", s.walk / WorkoutMath.MILE, s.run / WorkoutMath.MILE),
            17,
            false),
        lp(-1, 34));
  }

  void selectWorkout(long id) {
    if (service == null) return;
    selectedId = id;
    long generation = ++detailGeneration;
    adapter.notifyDataSetChanged();
    WorkoutStore store = service.recorder.store;
    store.execute(
        () -> {
          if (generation != detailGeneration) return;
          WorkoutRecord r = store.detail(id);
          handler.post(
              () -> {
                if (selectedId != id || generation != detailGeneration || isDestroyed()) return;
                detail = r;
                refreshDetail();
              });
        });
  }

  void refreshDetail() {
    if (detail == null) {
      ui.set(detailTitle, "Choose a workout");
      ui.set(detailMeta, "");
      ui.set(detailMetrics, "");
      ui.set(detailMore, "");
      detailTrace.data(null, chartKind);
      return;
    }
    WorkoutRecord r = detail;
    ZonedDateTime dt = Instant.ofEpochMilli(r.startMs).atZone(WorkoutMath.ZONE);
    String time = dt.getHour() < 12 ? "Morning" : dt.getHour() < 17 ? "Afternoon" : "Evening";
    ui.set(detailTitle, time + " " + r.kind.toLowerCase(Locale.US));
    ui.set(
        detailMeta,
        profileName(r.profile)
            + " · "
            + dt.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US))
            + ("Interrupted".equals(r.reason) ? " · Interrupted" : ""));
    ui.set(
        detailMetrics,
        fmt("%.2f mi    |    %s", r.meters / WorkoutMath.MILE, WorkoutMath.duration(r.movingMs)));
    ui.set(
        detailMore,
        "Avg pace  "
            + WorkoutMath.pace(r.meters, r.movingMs)
            + " /mi       Est. elevation  "
            + Math.round(r.elevationM * 3.28084)
            + " ft\nEst. active calories  "
            + Calories.label(r));
    TextView info = historyDetail.findViewWithTag("info");
    int gripSamples = 0, bleSamples = 0, legacySamples = 0;
    for (WorkoutRecord.Point point : r.points) {
      if (HeartReading.valid(point.gripHr)) gripSamples++;
      if (HeartReading.valid(point.bluetoothHr)) bleSamples++;
      if (HeartReading.valid(point.hr) && "legacy".equals(point.hrSource)) legacySamples++;
    }
    ui.set(
        info,
        fmt("Average speed                         %.1f mph", r.avgMph())
            + "\n\nEstimated active calories                 "
            + Calories.label(r)
            + (r.weightKg > 0
                ? fmt("\nWeight at workout: %.1f lb", r.weightKg / .45359237)
                : "\nNo calorie estimate was saved for this workout")
            + "\nAdult speed/grade estimate; decline uses a level-ground approximation."
            + "\n\nHeart rate                         "
            + (r.hrCount > 0
                ? Math.round((double) r.hrSum / r.hrCount) + " bpm avg"
                : "No readings recorded")
            + "\nHandles: "
            + gripSamples
            + " samples · Bluetooth: "
            + bleSamples
            + " samples"
            + (legacySamples > 0 ? " · Earlier: " + legacySamples : ""));
    detailTrace.data(r, chartKind);
  }

  void sensorStatus() {
    if (service == null || (heartDialog != null && heartDialog.isShowing())) return;
    heartDetails = new TextView(this);
    heartDetails.setTextSize(17);
    heartDetails.setPadding(28, 20, 28, 20);
    heartDetails.setText(service.engine.gripDetails() + "\n" + service.heart.status);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(heartDetails);
    heartDialog =
        new AlertDialog.Builder(this)
            .setTitle("Heart-rate sensors")
            .setView(scroll)
            .setPositiveButton("Bluetooth sensor…", (d, n) -> connectSensor())
            .setNegativeButton("Close", null)
            .create();
    heartDialog.setOnDismissListener(
        d -> {
          heartDialog = null;
          heartDetails = null;
        });
    heartDialog.show();
  }

  void connectSensor() {
    if (service == null) return;
    if (service.engine.motionPossible) {
      Toast.makeText(this, "Pause before connecting a sensor", Toast.LENGTH_SHORT).show();
      return;
    }
    String[] sensorPermissions =
        Build.VERSION.SDK_INT >= 31
            ? new String[] {
              Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
            }
            : new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
    for (String permission : sensorPermissions) {
      if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(sensorPermissions, 7);
        return;
      }
    }
    if (service.heart.gatt != null) {
      new AlertDialog.Builder(this)
          .setTitle("Heart-rate sensor")
          .setMessage(service.heart.status)
          .setPositiveButton("Disconnect", (d, n) -> service.heart.close())
          .setNegativeButton("Keep connected", null)
          .show();
      return;
    }
    ArrayList<BluetoothDevice> devices = new ArrayList<>();
    ArrayList<String> labels = new ArrayList<>();
    ArrayAdapter<String> found =
        new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Nearby heart-rate sensors")
            .setAdapter(
                found,
                (d, n) -> {
                  if (n < devices.size()) service.heart.connect(devices.get(n));
                })
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnDismissListener(
        d -> {
          if (service != null) service.heart.stopScan();
        });
    dialog.show();
    service.heart.scan(
        device -> {
          if (!devices.contains(device)) {
            devices.add(device);
            String name = service.heart.name(device);
            labels.add(name == null ? "Heart-rate sensor " + (devices.size()) : name);
            found.notifyDataSetChanged();
          }
        });
  }

  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 7 && g.length > 0) {
      boolean allowed = true;
      for (int result : g) if (result != PackageManager.PERMISSION_GRANTED) allowed = false;
      if (allowed) connectSensor();
    }
  }

  void update() {
    if (ui == null) return;
    long second = System.currentTimeMillis() / 1000;
    if (!uiParked && second != lastClockSecond) {
      lastClockSecond = second;
      ui.set(date, ZonedDateTime.now(WorkoutMath.ZONE).format(HEADER_CLOCK));
    }
    if (service == null) {
      start.setEnabled(false);
      return;
    }
    ControllerEngine e = service.engine;
    if (heartDetails != null) ui.set(heartDetails, e.gripDetails() + "\n" + service.heart.status);
    if (resumed) e.uiHeartbeat();
    if (video != null && video.failed) releaseVideo();
    if (service.idle.parked()) {
      if (!uiParked) {
        enterIdleScreen();
      }
      updateIdleClock();
      ui.set(status, "Idle — tap to wake");
      return;
    } else if (uiParked) wakeUi();
    boolean f = e.fresh();
    WorkoutRecord w = service.recorder.shown();
    ui.set(elapsed, WorkoutMath.duration(w == null ? 0 : w.movingMs));
    ui.set(distance, fmt("%.2f mi", w == null ? 0 : w.meters / WorkoutMath.MILE));
    ui.set(calories, Calories.label(w));
    miniTrack.data(w == null ? 0 : w.meters);
    ui.set(pace, (f ? WorkoutMath.livePace(e.speedRaw) : "—") + " /mi");
    ui.set(elevation, Math.round(w == null ? 0 : w.elevationM * 3.28084) + " ft");
    HeartReading reading = service.heartReading();
    ui.set(
        heart,
        reading.bpm == 0
            ? "Connect"
            : reading.bpm + " bpm · " + (reading.bluetooth > 0 ? "BLE" : "Grip"));
    ui.set(kind, service.recorder.liveKind() + "  ·  Auto");
    if (f) {
      if (e.mode == 8 && lastControllerMode >= 0 && lastControllerMode != 8) lockVideo();
      lastControllerMode = e.mode;
    }
    ui.set(
        status,
        e.message
            + (service.recorder.showingRecovered() ? "\nRecovered workout saved in History." : "")
            + (service.recorder.store.warning.isEmpty()
                ? ""
                : "\n" + service.recorder.store.warning));
    ui.set(speed, f ? fmt("%.1f mph", WorkoutMath.mph(e.speedRaw)) : "— mph");
    ui.set(grade, f ? fmt("%.1f %%", e.inclineRaw / 100.0) : "— %");
    ui.set(fan, "Fan\n" + (f ? (e.fan == 0 ? "Off" : Integer.toString(e.fan)) : "—"));
    if (!held)
      ui.set(
          start,
          f && e.mode == 2
              ? "Ⅱ  Pause"
              : "Hold 2 sec\n"
                  + (f && e.mode == 3 && service.recorder.hasActive() ? "Resume" : "Start")
                  + " · 1 mph");
    start.setEnabled(f && (e.mode == 2 || e.canStart()));
    start.setAlpha(start.isEnabled() ? 1 : .42f);
    boolean adjust = e.canAdjust();
    enabled(speedDown, adjust && e.mode == 2);
    enabled(speedUp, adjust && e.mode == 2);
    enabled(gradeDown, adjust);
    enabled(gradeUp, adjust);
    enabled(fan, adjust);
    ui.set(
        otherStatus,
        !f
            ? "Readings unavailable"
            : e.mode == 8
                ? "Safety key removed"
                : e.mode == 2
                    ? "Pausing workout…"
                    : service.recorder.hasActive()
                        ? "Workout paused"
                        : "Ready for your next workout");
    if (tab.equals("Workout")) liveTrace.data(w, "Pace");
    if (revision != service.recorder.store.revision.get() && !querying) queryData();
    loadBests();
  }

  void openBests() {
    showBests = true;
    switchTab("Progress");
    applyBestsMode();
    loadBests();
  }

  void applyBestsMode() {
    if (progressOverview == null) return;
    if (showBests && recordsPanel == null) {
      recordsPanel =
          new RecordsPanel(
              this,
              ui,
              new RecordsPanel.Actions() {
                public void overview() {
                  showBests = false;
                  applyBestsMode();
                  queryData();
                }

                public void session(WorkoutRecord r) {
                  openBestSession(r);
                }
              });
      progress.addView(recordsPanel, new LinearLayout.LayoutParams(-1, -1));
    }
    progressOverview.setVisibility(showBests ? View.GONE : View.VISIBLE);
    if (recordsPanel != null) {
      recordsPanel.setVisibility(showBests ? View.VISIBLE : View.GONE);
      if (bests != null && service != null && bests.profile.equals(service.recorder.profile))
        recordsPanel.data(bests, profileName(bests.profile));
    }
  }

  void openBestSession(WorkoutRecord r) {
    if (r == null) return;
    range = "All time";
    filter = "All";
    selectedId = r.id;
    refreshHistoryFilters();
    switchTab("History");
    selectWorkout(r.id);
  }

  void loadBests() {
    if (service == null || bestsLoading || SystemClock.elapsedRealtime() < bestsRetryAt) return;
    String profile = service.recorder.profile;
    WorkoutStore store = service.recorder.store;
    LocalDate today = LocalDate.now(WorkoutMath.ZONE);
    if (bests != null
        && bests.profile.equals(profile)
        && today.equals(bestsDay)
        && bestsRevision == store.completedRevision.get()) return;
    bestsLoading = true;
    store.execute(
        () -> {
          try {
            long loaded = store.completedRevision.get();
            PersonalBests result =
                PersonalBests.of(
                    store.list(profile, 0, WorkoutMath.midnight(today.plusDays(1)), "All"),
                    profile,
                    today);
            handler.post(
                () -> {
                  bestsLoading = false;
                  if (service == null || isFinishing() || isDestroyed()) return;
                  if (!profile.equals(service.recorder.profile)) {
                    loadBests();
                    return;
                  }
                  bests = result;
                  bestsDay = today;
                  bestsRevision = loaded;
                  if (recordsPanel != null) recordsPanel.data(result, profileName(result.profile));
                  ui.set(
                      bestsLink,
                      result.currentStreak > 0
                          ? result.currentStreak + "-day streak · Your bests  ›"
                          : "Your bests & streaks  ›");
                });
          } catch (Exception failed) {
            handler.post(
                () -> {
                  bestsLoading = false;
                  bestsRevision = store.completedRevision.get();
                  bestsRetryAt = SystemClock.elapsedRealtime() + 30000;
                });
          }
        });
  }

  void secretTap() {
    if (service == null || !service.engine.canReset()) return;
    long now = SystemClock.elapsedRealtime();
    if (now - lastLogoTap > 1500) logoTaps = 0;
    lastLogoTap = now;
    if (++logoTaps == 5) {
      logoTaps = 0;
      Toast.makeText(this, "Secret found. The laundry is still waiting.", Toast.LENGTH_LONG).show();
    }
  }

  void enabled(TextView t, boolean on) {
    t.setEnabled(on);
    t.setAlpha(on ? 1 : .4f);
  }

  String fmt(String f, Object... args) {
    return String.format(Locale.US, f, args);
  }

  protected void onResume() {
    super.onResume();
    resumed = true;
    handler.removeCallbacks(evictVideo);
    handler.removeCallbacks(tick);
    handler.post(tick);
    wakeUi();
    immersive();
    if (service != null) {
      service.show();
      queryData();
    }
    if (video != null) video.visible(videoGate.unlocked() && tab.equals("Workout"));
  }

  protected void onPause() {
    cancelHold();
    if (profileDialog != null) profileDialog.dismiss();
    if (heartDialog != null) heartDialog.dismiss();
    resumed = false;
    handler.removeCallbacks(tick);
    handler.removeCallbacks(evictVideo);
    handler.postDelayed(evictVideo, 120000);
    lockVideo();
    if (service != null) service.hide();
    super.onPause();
  }

  void wakeUi() {
    if (uiParked) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.screenBrightness = awakeBrightness;
      getWindow().setAttributes(attributes);
      if (idleCover != null) idleCover.setVisibility(View.GONE);
      root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      lastClockSecond = -1;
      android.util.Log.i("ConsoleIdle", "WAKE brightness=" + attributes.screenBrightness);
      queryData();
    }
    uiParked = false;
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (service != null) service.userInteraction();
  }

  void enterIdleScreen() {
    cancelHold();
    if (profileDialog != null) profileDialog.dismiss();
    releaseVideo();
    if (heartDialog != null) heartDialog.dismiss();
    WindowManager.LayoutParams attributes = getWindow().getAttributes();
    awakeBrightness = attributes.screenBrightness;
    attributes.screenBrightness = .01f;
    getWindow().setAttributes(attributes);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    uiParked = true;
    lastIdleMinute = -1;
    root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    idleCover.setVisibility(View.VISIBLE);
    android.util.Log.i(
        "ConsoleIdle",
        "ENTER brightness="
            + attributes.screenBrightness
            + " videoReleased="
            + (video == null)
            + " keepScreenOn="
            + ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                != 0));
  }

  void updateIdleClock() {
    long minute = System.currentTimeMillis() / 60000;
    if (minute == lastIdleMinute) return;
    lastIdleMinute = minute;
    idleClock.setText(
        "Console\n" + ZonedDateTime.now(WorkoutMath.ZONE).format(IDLE_CLOCK) + "\nTap to wake");
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (wakeTouch.consume(
        uiParked,
        action == MotionEvent.ACTION_DOWN,
        action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      if (uiParked && action == MotionEvent.ACTION_DOWN) wakeUi();
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  public void onUserInteraction() {
    super.onUserInteraction();
    wakeUi();
  }

  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if ((level >= TRIM_MEMORY_RUNNING_LOW && level <= TRIM_MEMORY_RUNNING_CRITICAL)
        || level >= TRIM_MEMORY_BACKGROUND) releaseVideo();
  }

  public void onLowMemory() {
    super.onLowMemory();
    releaseVideo();
  }

  public void onBackPressed() {
    if (video != null
        && !video.failed
        && video.session != null
        && tab.equals("Workout")
        && video.watch) {
      video.session.exitFullScreen();
      return;
    }
    if (!tab.equals("Workout")) {
      switchTab("Workout");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Leave Console?")
        .setMessage("The treadmill will be asked to pause.")
        .setPositiveButton(
            "Leave",
            (d, n) -> {
              if (service != null) service.engine.userStop();
              finish();
            })
        .setNegativeButton("Stay", null)
        .show();
  }

  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (video != null) video.close();
    if (bound) unbindService(connection);
    bound = false;
    service = null;
    detailGeneration++;
    queryGeneration++;
    super.onDestroy();
  }
}

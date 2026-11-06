package org.opentread.console;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class WorkoutStore extends SQLiteOpenHelper {
  private final Context context;
  private final ExecutorService io =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "console-storage"));
  private final AtomicBoolean closing = new AtomicBoolean();
  public volatile String warning = "";
  public final AtomicLong revision = new AtomicLong();
  public final AtomicLong completedRevision = new AtomicLong();
  private long lastCheckpointMs;
  static final String RECOVER_SQL =
      "UPDATE workouts SET end_ms=updated_ms,reason='Interrupted' WHERE end_ms=0";

  public WorkoutStore(Context c) {
    super(c, "console-workouts.db", null, 4);
    context = c.getApplicationContext();
    setWriteAheadLoggingEnabled(true);
  }

  public boolean execute(Runnable task) {
    if (closing.get()) return false;
    try {
      io.execute(
          () -> {
            try {
              task.run();
            } catch (RuntimeException failed) {
              warning = "Workout saves unavailable — check tablet storage";
              Log.e("ConsoleStore", "Storage task failed; controller remains independent", failed);
            }
          });
      return true;
    } catch (RejectedExecutionException closed) {
      return false;
    }
  }

  public void shutdown() {
    if (!closing.compareAndSet(false, true)) return;
    io.execute(
        () -> {
          try {
            super.close();
          } catch (RuntimeException failed) {
            Log.e("ConsoleStore", "Storage close failed", failed);
          }
        });
    io.shutdown();
  }

  @Override
  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE workouts(id INTEGER PRIMARY KEY,profile TEXT NOT NULL,kind TEXT NOT"
            + " NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL DEFAULT 0,updated_ms INTEGER"
            + " NOT NULL,moving_ms INTEGER NOT NULL DEFAULT 0,meters REAL NOT NULL DEFAULT"
            + " 0,elevation_m REAL NOT NULL DEFAULT 0,max_kph REAL NOT NULL DEFAULT 0,hr_sum"
            + " INTEGER NOT NULL DEFAULT 0,hr_count INTEGER NOT NULL DEFAULT 0,reason TEXT NOT NULL"
            + " DEFAULT '',walk_ms INTEGER NOT NULL DEFAULT 0,run_ms INTEGER NOT NULL DEFAULT"
            + " 0,walk_meters REAL NOT NULL DEFAULT 0,run_meters REAL NOT NULL DEFAULT 0,weight_kg"
            + " REAL NOT NULL DEFAULT 0,active_kcal REAL NOT NULL DEFAULT 0,calorie_model TEXT NOT"
            + " NULL DEFAULT '')");
    d.execSQL("CREATE INDEX workouts_profile_date ON workouts(profile,start_ms)");
    d.execSQL(
        "CREATE TABLE samples(id INTEGER PRIMARY KEY AUTOINCREMENT,workout_id INTEGER NOT"
            + " NULL,at_ms INTEGER NOT NULL,moving_ms INTEGER NOT NULL,meters REAL NOT"
            + " NULL,speed_raw INTEGER NOT NULL,grade_raw INTEGER NOT NULL,hr INTEGER NOT NULL"
            + " DEFAULT 0,grip_hr INTEGER NOT NULL DEFAULT 0,bluetooth_hr INTEGER NOT NULL DEFAULT"
            + " 0,hr_source TEXT NOT NULL DEFAULT 'legacy')");
    d.execSQL("CREATE INDEX samples_workout ON samples(workout_id,id)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    if (old < 1 || old > 3 || next != 4)
      throw new IllegalStateException("No migration for this database version");
    if (old == 1) migrate(d, "db/migrate-1-to-2.sql");
    if (old <= 2) migrate(d, "db/migrate-2-to-3.sql");
    migrate(d, "db/migrate-3-to-4.sql");
  }

  void migrate(SQLiteDatabase d, String path) {
    try (java.io.InputStream in = context.getAssets().open(path);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024];
      int n;
      while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
      for (String sql :
          new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).split(";"))
        if (!sql.trim().isEmpty()) d.execSQL(sql.trim());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot migrate saved workouts", e);
    }
  }

  ContentValues values(WorkoutRecord r) {
    ContentValues v = new ContentValues();
    v.put("id", r.id);
    v.put("profile", r.profile);
    v.put("kind", r.kind);
    v.put("walk_ms", r.walkMs);
    v.put("run_ms", r.runMs);
    v.put("walk_meters", r.walkMeters);
    v.put("run_meters", r.runMeters);
    v.put("start_ms", r.startMs);
    v.put("end_ms", r.endMs);
    v.put("updated_ms", r.updatedMs);
    v.put("moving_ms", r.movingMs);
    v.put("meters", r.meters);
    v.put("elevation_m", r.elevationM);
    v.put("max_kph", r.maxKph);
    v.put("hr_sum", r.hrSum);
    v.put("hr_count", r.hrCount);
    v.put("weight_kg", r.weightKg);
    v.put("active_kcal", r.activeKcal);
    v.put("calorie_model", r.calorieModel);
    v.put("reason", r.reason);
    return v;
  }

  public void save(WorkoutRecord r, List<WorkoutRecord.Point> pending) {
    WorkoutRecord snapshot = r.copy();
    ArrayList<WorkoutRecord.Point> points = new ArrayList<>(pending);
    execute(
        () -> {
          SQLiteDatabase d = getWritableDatabase();
          d.beginTransaction();
          try {
            if (d.insertWithOnConflict(
                    "workouts", null, values(snapshot), SQLiteDatabase.CONFLICT_REPLACE)
                < 0) throw new SQLiteException("Workout summary could not be saved");
            for (WorkoutRecord.Point p : points) {
              ContentValues v = new ContentValues();
              v.put("workout_id", snapshot.id);
              v.put("at_ms", p.atMs);
              v.put("moving_ms", p.movingMs);
              v.put("meters", p.meters);
              v.put("speed_raw", p.speedRaw);
              v.put("grade_raw", p.gradeRaw);
              v.put("hr", p.hr);
              v.put("grip_hr", p.gripHr);
              v.put("bluetooth_hr", p.bluetoothHr);
              v.put("hr_source", p.hrSource);
              d.insertOrThrow("samples", null, v);
            }
            d.setTransactionSuccessful();
          } finally {
            d.endTransaction();
          }
          checkpoint(d, snapshot.endMs > 0);
          warning = "";
          revision.incrementAndGet();
          if (snapshot.endMs > 0) completedRevision.incrementAndGet();
        });
  }

  // WAL/NORMAL keeps transactions atomic. A periodic passive checkpoint also syncs the journal
  // without putting disk flushes on every 1Hz sample or on the UI/controller worker.
  void checkpoint(SQLiteDatabase d, boolean finished) {
    long now = SystemClock.elapsedRealtime();
    if (!finished && lastCheckpointMs != 0 && now - lastCheckpointMs < 60000) return;
    try (Cursor c = d.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null)) {
      if (c.moveToFirst() && c.getInt(0) == 0 && c.getInt(1) == c.getInt(2)) lastCheckpointMs = now;
    } catch (SQLiteException failed) {
      Log.w("ConsoleStore", "Checkpoint deferred; committed workout remains in journal", failed);
    }
  }

  public void recover(String profile, java.util.function.Consumer<WorkoutRecord> complete) {
    execute(
        () -> {
          WorkoutRecord recovered = null;
          SQLiteDatabase d = getWritableDatabase();
          d.beginTransaction();
          try {
            try (Cursor c =
                d.query(
                    "workouts",
                    null,
                    "end_ms=0 AND profile=?",
                    new String[] {profile},
                    null,
                    null,
                    "updated_ms DESC",
                    "1")) {
              if (c.moveToFirst()) recovered = row(c);
            }
            d.execSQL(RECOVER_SQL);
            d.setTransactionSuccessful();
          } finally {
            d.endTransaction();
          }
          if (recovered != null) {
            recovered.endMs = recovered.updatedMs;
            recovered.reason = "Interrupted";
            checkpoint(d, true);
          }
          revision.incrementAndGet();
          completedRevision.incrementAndGet();
          warning = "";
          WorkoutRecord result = recovered;
          new Handler(Looper.getMainLooper()).post(() -> complete.accept(result));
        });
  }

  WorkoutRecord row(Cursor c) {
    WorkoutRecord r = new WorkoutRecord();
    r.id = c.getLong(c.getColumnIndexOrThrow("id"));
    r.profile = c.getString(c.getColumnIndexOrThrow("profile"));
    r.kind = c.getString(c.getColumnIndexOrThrow("kind"));
    r.walkMs = c.getLong(c.getColumnIndexOrThrow("walk_ms"));
    r.runMs = c.getLong(c.getColumnIndexOrThrow("run_ms"));
    r.walkMeters = c.getDouble(c.getColumnIndexOrThrow("walk_meters"));
    r.runMeters = c.getDouble(c.getColumnIndexOrThrow("run_meters"));
    r.startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"));
    r.endMs = c.getLong(c.getColumnIndexOrThrow("end_ms"));
    r.updatedMs = c.getLong(c.getColumnIndexOrThrow("updated_ms"));
    r.movingMs = c.getLong(c.getColumnIndexOrThrow("moving_ms"));
    r.meters = c.getDouble(c.getColumnIndexOrThrow("meters"));
    r.elevationM = c.getDouble(c.getColumnIndexOrThrow("elevation_m"));
    r.maxKph = c.getDouble(c.getColumnIndexOrThrow("max_kph"));
    r.hrSum = c.getInt(c.getColumnIndexOrThrow("hr_sum"));
    r.hrCount = c.getInt(c.getColumnIndexOrThrow("hr_count"));
    r.weightKg = c.getDouble(c.getColumnIndexOrThrow("weight_kg"));
    r.activeKcal = c.getDouble(c.getColumnIndexOrThrow("active_kcal"));
    r.calorieModel = c.getString(c.getColumnIndexOrThrow("calorie_model"));
    r.reason = c.getString(c.getColumnIndexOrThrow("reason"));
    return r;
  }

  public List<WorkoutRecord> list(String profile, long from, long until, String kind) {
    String where = "start_ms>=? AND start_ms<?";
    ArrayList<String> args = new ArrayList<>();
    args.add(Long.toString(from));
    args.add(Long.toString(until));
    if (profile != null) {
      where += " AND profile=?";
      args.add(profile);
    }
    if (kind != null && !kind.equals("All")) {
      where += " AND kind=?";
      args.add(kind);
    }
    ArrayList<WorkoutRecord> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "workouts",
                null,
                where,
                args.toArray(new String[0]),
                null,
                null,
                "start_ms DESC")) {
      while (c.moveToNext()) out.add(row(c));
    }
    return out;
  }

  public WorkoutRecord detail(long id) {
    WorkoutRecord r;
    try (Cursor c =
        getReadableDatabase()
            .query("workouts", null, "id=?", new String[] {Long.toString(id)}, null, null, null)) {
      if (!c.moveToFirst()) return null;
      r = row(c);
    }
    try (Cursor c =
        getReadableDatabase()
            .query(
                "samples",
                null,
                "workout_id=?",
                new String[] {Long.toString(id)},
                null,
                null,
                "id ASC")) {
      while (c.moveToNext())
        r.points.add(
            new WorkoutRecord.Point(
                c.getLong(c.getColumnIndexOrThrow("at_ms")),
                c.getLong(c.getColumnIndexOrThrow("moving_ms")),
                c.getDouble(c.getColumnIndexOrThrow("meters")),
                c.getInt(c.getColumnIndexOrThrow("speed_raw")),
                c.getInt(c.getColumnIndexOrThrow("grade_raw")),
                c.getInt(c.getColumnIndexOrThrow("hr")),
                c.getInt(c.getColumnIndexOrThrow("grip_hr")),
                c.getInt(c.getColumnIndexOrThrow("bluetooth_hr")),
                c.getString(c.getColumnIndexOrThrow("hr_source"))));
    }
    return r;
  }
}

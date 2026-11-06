package org.opentread.console;

import android.app.*;
import android.content.*;
import android.os.*;

/** Controller survives media rendering; no exported commands, overlays, or automatic Start. */
public final class ConsoleService extends Service {
  public ControllerEngine engine;
  public WorkoutRecorder recorder;
  public HeartSensor heart;
  public boolean visible = false, screenOn = true;
  public final IdlePolicy idle = new IdlePolicy();
  private HeartReading lastHeart = HeartReading.NONE;
  final Binder binder = new LocalBinder();
  final Handler handler = new Handler(Looper.getMainLooper());

  public class LocalBinder extends Binder {
    public ConsoleService service() {
      return ConsoleService.this;
    }
  }

  final Runnable tick =
      new Runnable() {
        public void run() {
          if (engine.motionPossible && (!visible || !screenOn || !engine.fresh()))
            engine.userStop();
          recorder.observe(
              System.currentTimeMillis(),
              SystemClock.elapsedRealtime(),
              engine.mode,
              engine.speedRaw,
              engine.inclineRaw,
              heartReading(),
              engine.fresh() && !engine.pausing);
          boolean stopped =
              engine.canReset()
                  || (!engine.active
                      && !engine.motionPossible
                      && (engine.mode == 1 || engine.mode == 3 || engine.mode == 8)
                      && engine.speedRaw >= 0
                      && engine.speedRaw <= 5);
          boolean wasParked = idle.parked();
          boolean parked =
              idle.update(SystemClock.elapsedRealtime(), engine.motionPossible, stopped);
          engine.parked = parked;
          if (parked && !wasParked) {
            recorder.finish(System.currentTimeMillis(), "Saved after inactivity");
            heart.close();
          }
          handler.postDelayed(
              this,
              (!visible || !screenOn) && !engine.motionPossible
                  ? 5000
                  : parked && !engine.motionPossible ? 1000 : 200);
        }
      };
  final BroadcastReceiver screen =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
          apply();
        }
      };

  public void onCreate() {
    super.onCreate();
    NotificationManager n = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    n.createNotificationChannel(
        new NotificationChannel(
            "treadmill", "Treadmill controls", NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, ConsoleService.class).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    startForeground(
        1,
        new Notification.Builder(this, "treadmill")
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Console")
            .setContentText("Treadmill controls · tap to return")
            .setContentIntent(open)
            .addAction(new Notification.Action.Builder(null, "STOP", stop).build())
            .setOngoing(true)
            .build());
    recorder = new WorkoutRecorder(getApplicationContext());
    heart = new HeartSensor(getApplicationContext());
    engine = new ControllerEngine(getApplicationContext(), null);
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(screen, filter);
    handler.post(tick);
  }

  public void show() {
    visible = true;
    userInteraction();
    apply();
  }

  public void hide() {
    visible = false;
    apply();
    recorder.flushNow(System.currentTimeMillis(), SystemClock.elapsedRealtime());
  }

  void apply() {
    engine.controlsVisible(visible && screenOn);
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  public void userInteraction() {
    idle.touch(SystemClock.elapsedRealtime());
    engine.parked = false;
  }

  public HeartReading heartReading() {
    int grip = engine.gripBpm(), ble = heart.bpm();
    if (!HeartReading.valid(grip)) grip = 0;
    if (!HeartReading.valid(ble)) ble = 0;
    if (grip != lastHeart.handles || ble != lastHeart.bluetooth)
      lastHeart = grip == 0 && ble == 0 ? HeartReading.NONE : new HeartReading(grip, ble);
    return lastHeart;
  }

  public IBinder onBind(Intent i) {
    return binder;
  }

  public boolean onUnbind(Intent i) {
    hide();
    return true;
  }

  public int onStartCommand(Intent i, int flags, int id) {
    if (i != null && "STOP".equals(i.getAction())) engine.userStop();
    return START_NOT_STICKY;
  }

  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    engine.shutdown();
    recorder.flushNow(System.currentTimeMillis(), SystemClock.elapsedRealtime());
    recorder.store.shutdown();
    heart.close();
    try {
      unregisterReceiver(screen);
    } catch (Exception ignored) {
    }
    super.onDestroy();
  }
}

package org.opentread.console;

import android.app.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.mozilla.geckoview.*;

/** One embedded browser. Its web content has no reference or bridge to the controller. */
public final class EmbeddedVideo extends LinearLayout {
  static GeckoRuntime runtime;
  final Activity activity;
  final Handler handler = new Handler();
  GeckoSession session;
  GeckoView web;
  org.mozilla.geckoview.MediaSession media;
  LinearLayout toolbar;
  TextView nowPlaying;
  Button previous, playPause, next;
  long mediaFeatures;
  boolean playing;
  final Runnable phoneAudio;
  TextView hint;
  EditText search;
  boolean canBack = false, embedded = true, watch = false;
  boolean closed, failed;
  boolean active;
  AlertDialog permissionDialog, providerDialog;
  int navigationGeneration;
  String current = "", videoId = "";
  String site = "youtube";

  public EmbeddedVideo(Activity a, Runnable phoneAudio) {
    super(a);
    activity = a;
    this.phoneAudio = phoneAudio;
    setOrientation(VERTICAL);
    setBackgroundColor(0xff0b0c0c);
    toolbar = new LinearLayout(a);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setPadding(8, 2, 8, 2);
    addView(toolbar, new LayoutParams(-1, 48));
    button(
        "‹",
        () -> {
          if (watch) {
            session.exitFullScreen();
            watch = false;
            toolbar.setVisibility(VISIBLE);
          } else if (canBack) session.goBack();
        });
    search = new EditText(a);
    search.setTextColor(Color.WHITE);
    search.setSingleLine(true);
    search.setTextSize(14);
    search.setHintTextColor(0xff999999);
    search.setHint("Search YouTube or paste a link");
    toolbar.addView(search, new LayoutParams(0, -1, 1));
    button("Go", () -> search());
    button(
        "Browse",
        () -> {
          if (!"youtube".equals(site)) {
            session.loadUri(MediaPolicy.home(site));
          } else if (!videoId.isEmpty()) {
            embedded = false;
            session.loadUri("https://m.youtube.com/watch?v=" + videoId);
          } else session.loadUri("https://m.youtube.com/");
        });
    button("↻", () -> session.reload());
    LinearLayout player = new LinearLayout(a);
    player.setGravity(Gravity.CENTER_VERTICAL);
    player.setPadding(10, 0, 8, 0);
    addView(player, new LayoutParams(-1, 42));
    Button sources = nativeButton(player, "Sources ▾", 100, () -> chooseProvider());
    sources.setContentDescription("Choose YouTube, YouTube Music, Spotify or phone audio");
    nowPlaying = new TextView(a);
    nowPlaying.setTextColor(Color.WHITE);
    nowPlaying.setTextSize(13);
    nowPlaying.setSingleLine(true);
    nowPlaying.setEllipsize(android.text.TextUtils.TruncateAt.END);
    nowPlaying.setText("Choose music or a video");
    player.addView(nowPlaying, new LayoutParams(0, -2, 1));
    previous = playerButton(player, "‹‹", () -> media.previousTrack());
    previous.setContentDescription("Previous track");
    playPause =
        playerButton(
            player,
            "Play",
            () -> {
              if (playing) media.pause();
              else media.play();
            });
    next = playerButton(player, "››", () -> media.nextTrack());
    next.setContentDescription("Next track");
    updatePlayer();
    hint = new TextView(a);
    hint.setTextColor(0xffb2b3ae);
    hint.setTextSize(12);
    hint.setPadding(12, 0, 12, 0);
    hint.setText("Choose a video. Use its fullscreen button for a clean player inside this frame.");
    addView(hint, new LayoutParams(-1, 24));
    web = new GeckoView(a);
    web.coverUntilFirstPaint(0xff101111);
    addView(web, new LayoutParams(-1, 0, 1));
    try {
      if (runtime == null)
        runtime =
            GeckoRuntime.create(
                a.getApplicationContext(),
                new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .debugLogging(false)
                    .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
                    .build());
      session =
          new GeckoSession(
              new GeckoSessionSettings.Builder()
                  .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                  .usePrivateMode(false)
                  .suspendMediaWhenInactive(true)
                  .build());
      session.setNavigationDelegate(
          new GeckoSession.NavigationDelegate() {
            public void onCanGoBack(GeckoSession s, boolean b) {
              canBack = b;
            }

            public void onLocationChange(
                GeckoSession s,
                String url,
                List<GeckoSession.PermissionDelegate.ContentPermission> p,
                Boolean b) {
              if (!closed) current = url;
            }

            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest r) {
              if (closed) return GeckoResult.fromValue(AllowOrDeny.DENY);
              Uri u = Uri.parse(r.uri);
              String scheme = u.getScheme();
              if (!"https".equals(scheme)
                  && !"about".equals(scheme)
                  && !"blob".equals(scheme)
                  && !"data".equals(scheme)) return GeckoResult.fromValue(AllowOrDeny.DENY);
              String id = extractId(u);
              if ("youtube".equals(site) && embedded && !id.isEmpty()) {
                int expected = navigationGeneration;
                handler.post(
                    () -> {
                      if (expected == navigationGeneration) embed(id);
                    });
                return GeckoResult.fromValue(AllowOrDeny.DENY);
              }
              return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
              if (uri.startsWith("https://"))
                handler.post(
                    () -> {
                      if (!closed && session != null) session.loadUri(uri);
                    });
              return GeckoResult.fromValue(null);
            }

            public GeckoResult<String> onLoadError(GeckoSession s, String u, WebRequestError e) {
              hint.setVisibility(VISIBLE);
              hint.setText("Page could not load. Check Wi-Fi and tap ↻ to retry.");
              return null;
            }
          });
      session.setContentDelegate(
          new GeckoSession.ContentDelegate() {
            public void onFullScreen(GeckoSession s, boolean full) {
              if (closed) return;
              watch = full;
              toolbar.setVisibility(full ? GONE : VISIBLE);
              hint.setVisibility(full ? GONE : VISIBLE);
            }

            public void onCrash(GeckoSession s) {
              if (closed) return;
              failed = true;
              hint.setVisibility(VISIBLE);
              active = false;
              resetPlayer();
              hint.setText("Media stopped. Close and choose a source to reload it.");
            }

            public void onKill(GeckoSession s) {
              onCrash(s);
            }
          });
      session.setMediaSessionDelegate(
          new org.mozilla.geckoview.MediaSession.Delegate() {
            public void onActivated(GeckoSession s, org.mozilla.geckoview.MediaSession m) {
              if (!closed) {
                media = m;
                playing = false;
                mediaFeatures = 0;
                updatePlayer();
              }
            }

            public void onDeactivated(GeckoSession s, org.mozilla.geckoview.MediaSession m) {
              if (media == m) resetPlayer();
            }

            public void onMetadata(
                GeckoSession s,
                org.mozilla.geckoview.MediaSession m,
                org.mozilla.geckoview.MediaSession.Metadata metadata) {
              if (closed || media != m) return;
              String title = metadata.title == null ? "" : metadata.title;
              String artist = metadata.artist == null ? "" : metadata.artist;
              nowPlaying.setText(
                  title.isEmpty()
                      ? MediaPolicy.title(site)
                      : title + (artist.isEmpty() ? "" : " · " + artist));
            }

            public void onFeatures(GeckoSession s, org.mozilla.geckoview.MediaSession m, long f) {
              if (!closed && media == m) {
                mediaFeatures = f;
                updatePlayer();
              }
            }

            public void onPlay(GeckoSession s, org.mozilla.geckoview.MediaSession m) {
              if (!closed && media == m) {
                playing = true;
                updatePlayer();
              }
            }

            public void onPause(GeckoSession s, org.mozilla.geckoview.MediaSession m) {
              if (!closed && media == m) {
                playing = false;
                updatePlayer();
              }
            }

            public void onStop(GeckoSession s, org.mozilla.geckoview.MediaSession m) {
              onPause(s, m);
            }
          });
      session.setPermissionDelegate(
          new GeckoSession.PermissionDelegate() {
            public GeckoResult<Integer> onContentPermissionRequest(
                GeckoSession s, ContentPermission p) {
              if (closed || activity.isFinishing() || activity.isDestroyed())
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
              if (p.permission == PERMISSION_AUTOPLAY_INAUDIBLE)
                return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
              if (p.permission == PERMISSION_AUTOPLAY_AUDIBLE) {
                String host = Uri.parse(p.uri).getHost();
                boolean allow = MediaPolicy.audible(active, host);
                Log.i("ConsoleMedia", "AUDIBLE_AUTOPLAY host=" + host + " allowed=" + allow);
                return GeckoResult.fromValue(
                    allow ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
              }
              if (p.permission == PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
                Log.i("ConsoleMedia", "DRM_PERMISSION_REQUEST host=" + Uri.parse(p.uri).getHost());
                GeckoResult<Integer> result = new GeckoResult<>();
                if (permissionDialog != null) permissionDialog.dismiss();
                java.util.concurrent.atomic.AtomicBoolean answered =
                    new java.util.concurrent.atomic.AtomicBoolean();
                java.util.function.Consumer<Integer> answer =
                    value -> {
                      if (answered.compareAndSet(false, true)) {
                        Log.i(
                            "ConsoleMedia",
                            "DRM_PERMISSION_REPLY allowed="
                                + (value == ContentPermission.VALUE_ALLOW));
                        result.complete(value);
                      }
                    };
                permissionDialog =
                    new AlertDialog.Builder(activity)
                        .setTitle("Protected video")
                        .setMessage(
                            "Allow protected playback for " + Uri.parse(p.uri).getHost() + "?")
                        .setPositiveButton(
                            "Allow", (d, w) -> answer.accept(ContentPermission.VALUE_ALLOW))
                        .setNegativeButton(
                            "Cancel", (d, w) -> answer.accept(ContentPermission.VALUE_DENY))
                        .create();
                permissionDialog.setOnDismissListener(
                    d -> answer.accept(ContentPermission.VALUE_DENY));
                permissionDialog.show();
                return result;
              }
              return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
          });
      session.open(runtime);
      web.setSession(session);
    } catch (Exception e) {
      failed = true;
      hint.setText("Video engine unavailable. Treadmill controls remain separate.");
    }
  }

  void chooseProvider() {
    if (providerDialog != null && providerDialog.isShowing()) return;
    Log.i("ConsoleMedia", "SOURCES_OPEN fullscreen=" + watch);
    providerDialog =
        new AlertDialog.Builder(activity)
            .setTitle("Watch or listen")
            .setItems(
                new String[] {"YouTube", "YouTube Music", "Spotify", "Phone audio · Bluetooth"},
                (d, n) -> {
                  if (closed) return;
                  if (n == 3) phoneAudio.run();
                  else open(new String[] {"youtube", "music", "spotify"}[n]);
                })
            .create();
    providerDialog.show();
  }

  Button playerButton(LinearLayout row, String label, Runnable action) {
    return nativeButton(
        row,
        label,
        66,
        () -> {
          if (media == null) return;
          action.run();
        });
  }

  Button nativeButton(LinearLayout row, String label, int width, Runnable action) {
    Button b = new Button(activity);
    b.setText(label);
    b.setTextSize(12);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(8, 0, 8, 0);
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff252725));
    row.addView(b, new LayoutParams(width, -1));
    b.setOnClickListener(
        v -> {
          if (!active || closed || failed || session == null || !b.isEnabled()) return;
          try {
            action.run();
          } catch (RuntimeException stopped) {
            resetPlayer();
          }
        });
    return b;
  }

  void updatePlayer() {
    boolean usable = active && !closed && !failed && media != null;
    previous.setEnabled(
        usable && (mediaFeatures & org.mozilla.geckoview.MediaSession.Feature.PREVIOUS_TRACK) != 0);
    next.setEnabled(
        usable && (mediaFeatures & org.mozilla.geckoview.MediaSession.Feature.NEXT_TRACK) != 0);
    playPause.setText(playing ? "Pause" : "Play");
    playPause.setEnabled(
        usable
            && (mediaFeatures
                    & (playing
                        ? org.mozilla.geckoview.MediaSession.Feature.PAUSE
                        : org.mozilla.geckoview.MediaSession.Feature.PLAY))
                != 0);
    for (Button b : new Button[] {previous, playPause, next}) b.setAlpha(b.isEnabled() ? 1 : .4f);
  }

  void resetPlayer() {
    media = null;
    playing = false;
    mediaFeatures = 0;
    nowPlaying.setText(MediaPolicy.title(site) + " · choose something to play");
    updatePlayer();
  }

  void button(String text, Runnable run) {
    nativeButton(toolbar, text, -2, run);
  }

  static String extractId(Uri u) {
    String host = u.getHost();
    if (host == null) return "";
    String id = "";
    if (host.equals("youtu.be")) {
      id = u.getLastPathSegment();
    } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
      if ("/watch".equals(u.getPath())) id = u.getQueryParameter("v");
      else if (u.getPath() != null && u.getPath().startsWith("/shorts/"))
        id = u.getLastPathSegment();
    }
    return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : "";
  }

  void embed(String id) {
    if (session == null || closed || failed || !"youtube".equals(site)) return;
    videoId = id;
    watch = false;
    toolbar.setVisibility(VISIBLE);
    hint.setVisibility(VISIBLE);
    hint.setText(
        "Embedded player · If a video blocks embedding, choose Browse to open it on YouTube.");
    String origin = "https://" + activity.getPackageName();
    HashMap<String, String> headers = new HashMap<>();
    headers.put("Referer", origin + "/");
    session.load(
        new GeckoSession.Loader()
            .uri(
                "https://www.youtube.com/embed/"
                    + id
                    + "?autoplay=1&playsinline=1&origin="
                    + Uri.encode(origin))
            .referrer(origin + "/")
            .additionalHeaders(headers));
  }

  public void open(String site) {
    if (session == null) return;
    navigationGeneration++;
    pauseVideo();
    this.site = MediaPolicy.provider(site);
    resetPlayer();
    videoId = "";
    embedded = this.site.equals("youtube");
    session.exitFullScreen();
    watch = false;
    toolbar.setVisibility(VISIBLE);
    hint.setVisibility(VISIBLE);
    session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
    search.setText("");
    search.setHint("Search " + MediaPolicy.title(this.site) + " or paste a link");
    hint.setText(
        embedded
            ? "Search or choose a video. It opens as an embedded player."
            : "Sign in to listen. Player controls appear when supported by the site.");
    session.loadUri(MediaPolicy.home(this.site));
  }

  public void restore(String url, String site) {
    if (!MediaPolicy.provider(site).equals(site)) {
      open("youtube");
      return;
    }
    this.site = site;
    embedded = "youtube".equals(site);
    search.setHint("Search " + MediaPolicy.title(site) + " or paste a link");
    if (session == null || url == null || !url.startsWith("https://")) {
      open(site);
      return;
    }
    session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
    Uri u = Uri.parse(url);
    String host = u.getHost(), path = u.getPath();
    if (embedded
        && host != null
        && (host.equals("youtube.com") || host.endsWith(".youtube.com"))
        && path != null
        && path.startsWith("/embed/")) {
      String id = u.getLastPathSegment();
      if (id != null && id.matches("[A-Za-z0-9_-]{11}")) {
        embed(id);
        return;
      }
    }
    session.loadUri(url);
  }

  void search() {
    String q = search.getText().toString().trim();
    if (q.isEmpty()) return;
    navigationGeneration++;
    pauseVideo();
    resetPlayer();
    embedded = "youtube".equals(site);
    session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
    Uri u = Uri.parse(q);
    String id = extractId(u);
    if (embedded && !id.isEmpty()) embed(id);
    else if ("https".equals(u.getScheme())) session.loadUri(q);
    else session.loadUri(MediaPolicy.searchBase(site) + Uri.encode(q));
    ((android.view.inputmethod.InputMethodManager)
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(search.getWindowToken(), 0);
    search.clearFocus();
  }

  public void visible(boolean b) {
    active = b && !closed && !failed;
    updatePlayer();
    if (session == null || closed || failed) return;
    try {
      if (!b) {
        pauseVideo();
        if (providerDialog != null) providerDialog.dismiss();
        if (permissionDialog != null) permissionDialog.dismiss();
      }
      session.setActive(b);
      session.setFocused(b);
    } catch (RuntimeException stopped) {
      failed = true;
    }
  }

  public void pauseVideo() {
    try {
      if (media != null) media.pause();
    } catch (RuntimeException stopped) {
      media = null;
    }
  }

  public void close() {
    active = false;
    updatePlayer();
    if (closed) return;
    closed = true;
    web.setKeepScreenOn(false);
    handler.removeCallbacksAndMessages(null);
    if (providerDialog != null) {
      providerDialog.dismiss();
      providerDialog = null;
    }
    if (permissionDialog != null) {
      permissionDialog.dismiss();
      permissionDialog = null;
    }
    if (session != null) {
      pauseVideo();
      try {
        web.releaseSession();
      } catch (RuntimeException alreadyClosed) {
      }
      try {
        session.close();
      } catch (RuntimeException alreadyClosed) {
      }
      session = null;
      media = null;
    }
  }
}

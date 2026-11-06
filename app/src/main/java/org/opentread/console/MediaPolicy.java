package org.opentread.console;

/** Provider choices and playback policy; no networking, credentials or controller access. */
public final class MediaPolicy {
  public static String provider(String key) {
    return "music".equals(key) || "spotify".equals(key) ? key : "youtube";
  }

  public static String title(String key) {
    return "music".equals(key) ? "YouTube Music" : "spotify".equals(key) ? "Spotify" : "YouTube";
  }

  public static String home(String key) {
    return "music".equals(key)
        ? "https://music.youtube.com/"
        : "spotify".equals(key) ? "https://open.spotify.com/" : "https://m.youtube.com/";
  }

  public static String searchBase(String key) {
    return "music".equals(key)
        ? "https://music.youtube.com/search?q="
        : "spotify".equals(key)
            ? "https://open.spotify.com/search/"
            : "https://m.youtube.com/results?search_query=";
  }

  public static boolean audible(boolean active, String host) {
    if (!active || host == null) return false;
    String h = host.toLowerCase(java.util.Locale.ROOT);
    return h.equals("open.spotify.com")
        || h.equals("youtube.com")
        || h.endsWith(".youtube.com")
        || h.equals("youtube-nocookie.com")
        || h.endsWith(".youtube-nocookie.com");
  }
}

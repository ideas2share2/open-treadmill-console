package org.opentread.console;

import android.content.Context;
import android.content.SharedPreferences;

/** Small local preferences. Read on screen creation or workout start, never per USB poll. */
public final class ProfileSettings {
  final SharedPreferences prefs;

  public ProfileSettings(Context c) {
    prefs = c.getSharedPreferences("console-profiles", 0);
  }

  public ProfileData get(String id) {
    int i = ProfileData.index(id);
    if (i < 0) throw new IllegalArgumentException("Unknown profile slot");
    String prefix = "slot." + id + ".";
    try {
      return new ProfileData(
          id,
          prefs.getString(prefix + "name", ProfileData.DEFAULT_NAMES[i]),
          Integer.parseInt(prefs.getString(prefix + "age", "0")),
          prefs.getString(prefix + "sex", ""),
          Double.parseDouble(prefs.getString(prefix + "weightLb", "0")),
          Double.parseDouble(prefs.getString(prefix + "heightIn", "0")));
    } catch (IllegalArgumentException | ClassCastException invalid) {
      return new ProfileData(id, ProfileData.DEFAULT_NAMES[i], 0, "", 0, 0);
    }
  }

  public void save(ProfileData p) {
    for (String id : ProfileData.IDS)
      if (!id.equals(p.id) && get(id).name.equalsIgnoreCase(p.name))
        throw new IllegalArgumentException("That name is already in use");
    String prefix = "slot." + p.id + ".";
    prefs
        .edit()
        .putString(prefix + "name", p.name)
        .putString(prefix + "age", Integer.toString(p.age))
        .putString(prefix + "sex", p.sex)
        .putString(prefix + "weightLb", Double.toString(p.weightLb))
        .putString(prefix + "heightIn", Double.toString(p.heightIn))
        .apply();
  }
}

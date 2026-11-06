package org.opentread.console;

/** Fixed slot IDs own history; editable names never become database keys. */
public final class ProfileData {
  public static final String[] IDS = {"slot-1", "slot-2", "guest", "slot-4"};
  public static final String[] DEFAULT_NAMES = {"Profile 1", "Profile 2", "Guest", "Profile 4"};
  public final String id, name, sex;
  public final int age;
  public final double weightLb, heightIn;

  public ProfileData(
      String id, String name, int age, String sex, double weightLb, double heightIn) {
    if (index(id) < 0) throw new IllegalArgumentException("Unknown profile slot");
    name = name == null ? "" : name.trim();
    if (name.isEmpty() || name.length() > 16 || name.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Use a name of 1–16 characters");
    if (age < 0 || age > 120) throw new IllegalArgumentException("Age must be 1–120, or blank");
    if (!"".equals(sex) && !"Female".equals(sex) && !"Male".equals(sex))
      throw new IllegalArgumentException("Choose a listed sex, or leave it unspecified");
    if (!Double.isFinite(weightLb) || (weightLb != 0 && (weightLb < 20 || weightLb > 660)))
      throw new IllegalArgumentException("Weight must be 20–660 lb, or blank");
    if (!Double.isFinite(heightIn) || (heightIn != 0 && (heightIn < 12 || heightIn > 96)))
      throw new IllegalArgumentException("Height must be 12–96 inches, or blank");
    this.id = id;
    this.name = name;
    this.age = age;
    this.sex = sex;
    this.weightLb = weightLb;
    this.heightIn = heightIn;
  }

  public static int index(String id) {
    for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
    return -1;
  }

  public double calorieWeightKg() {
    return weightLb > 0 && (age == 0 || age >= 18) ? weightLb * .45359237 : 0;
  }
}

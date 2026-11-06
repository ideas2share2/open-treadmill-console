package android.content;
public class SharedPreferences {
 private final java.util.Map<String,String> values=new java.util.HashMap<>();
 public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}
 public class Editor { public Editor putString(String key,String value){values.put(key,value);return this;} public void apply(){} }
}

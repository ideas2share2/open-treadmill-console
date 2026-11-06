package org.opentread.console;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.MetricAffectingSpan;
import android.view.*;
import android.widget.*;

public final class Ui {
  public static final int BG = 0xff0d0f0e,
      PANEL = 0xff111412,
      TEXT = 0xfff5f4ee,
      MUTED = 0xffb6b7b0,
      LINE = 0xff454942,
      LIME = ProfileTheme.FIRST,
      RED = 0xffff303a;
  final java.util.WeakHashMap<TextView, String> lastText = new java.util.WeakHashMap<>();
  public final int accent, selection;
  final Context c;
  public final float scale;
  final Typeface regular, bold, icons;

  public Ui(Context c) {
    this.c = c;
    accent =
        ProfileTheme.accent(
            c.getSharedPreferences("console-profiles", 0).getString("profile", "slot-1"));
    selection =
        0xff000000
            | ((((accent >> 16) & 255) + 3 * ((BG >> 16) & 255)) / 4 << 16)
            | ((((accent >> 8) & 255) + 3 * ((BG >> 8) & 255)) / 4 << 8)
            | (((accent & 255) + 3 * (BG & 255)) / 4);
    scale = c.getResources().getDisplayMetrics().widthPixels / 1717f;
    regular = Typeface.createFromAsset(c.getAssets(), "fonts/Inter.ttf");
    bold = Typeface.create(regular, 700, false);
    icons = Typeface.createFromAsset(c.getAssets(), "fonts/MaterialIconsOutlined.otf");
  }

  public int u(float x) {
    return Math.round(x * scale);
  }

  public TextView text(String s, float size, int color, boolean strong) {
    TextView t = new TextView(c);
    t.setText(display(s));
    lastText.put(t, s);
    t.setTextColor(color);
    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(size));
    t.setTypeface(strong ? bold : regular);
    t.setIncludeFontPadding(false);
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  public TextView icon(String glyph, int size, int color) {
    TextView t = text(glyph, size, color, false);
    t.setTypeface(icons);
    t.setGravity(Gravity.CENTER);
    return t;
  }

  public TextView button(String s, int size, int fill, int color, Runnable action) {
    TextView t = text(s, size, color, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(u(10), u(6), u(10), u(6));
    t.setMinHeight(u(44));
    t.setBackground(shape(fill, fill == 0 ? LINE : fill, u(13)));
    t.setOnClickListener(v -> action.run());
    t.setFocusable(true);
    t.setContentDescription(s);
    return t;
  }

  public android.graphics.drawable.Drawable shape(int fill, int stroke, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(radius);
    d.setStroke(u(1), stroke);
    return d;
  }

  public LinearLayout col() {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public LinearLayout row() {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public void pad(View l, int x, int y) {
    l.setPadding(u(x), u(y), u(x), u(y));
  }

  public void line(LinearLayout l) {
    View v = new View(c);
    v.setBackgroundColor(LINE);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, u(1));
    p.topMargin = u(15);
    p.bottomMargin = u(15);
    l.addView(v, p);
  }

  public void vline(LinearLayout l) {
    View v = new View(c);
    v.setBackgroundColor(LINE);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(u(1), -1);
    p.leftMargin = u(14);
    p.rightMargin = u(14);
    l.addView(v, p);
  }

  public void addWeight(LinearLayout l, View v, float w) {
    l.addView(v, new LinearLayout.LayoutParams(0, -1, w));
  }

  public void set(TextView v, String s) {
    if (s.equals(lastText.get(v))) return;
    lastText.put(v, s);
    v.setText(display(s));
    if (v.isClickable()) v.setContentDescription(s);
  }

  CharSequence display(String source) {
    String marks = "♡▶Ⅱ■‹›↻▾−+";
    String glyphs = "\ue87e\ue037\ue034\ue047\ue5cb\ue5cc\ue5d5\ue5c5\ue15b\ue145";
    boolean hasIcon = false;
    for (int i = 0; i < source.length(); i++)
      if (marks.indexOf(source.charAt(i)) >= 0) {
        hasIcon = true;
        break;
      }
    if (!hasIcon) return source;
    SpannableStringBuilder out = new SpannableStringBuilder(source);
    for (int i = 0; i < source.length(); i++) {
      int n = marks.indexOf(source.charAt(i));
      if (n >= 0) {
        out.replace(i, i + 1, glyphs.substring(n, n + 1));
        out.setSpan(
            new MetricAffectingSpan() {
              public void updateDrawState(TextPaint p) {
                p.setTypeface(icons);
              }

              public void updateMeasureState(TextPaint p) {
                p.setTypeface(icons);
              }
            },
            i,
            i + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return out;
  }

  public Drawable tab(boolean selected) {
    return new Drawable() {
      final Paint paint = new Paint();

      public void draw(Canvas c) {
        if (selected) {
          paint.setColor(accent);
          android.graphics.Rect b = getBounds();
          c.drawRect(b.left + u(20), b.bottom - u(5), b.right - u(20), b.bottom, paint);
        }
      }

      public void setAlpha(int a) {}

      public void setColorFilter(ColorFilter f) {}

      public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
      }
    };
  }
}

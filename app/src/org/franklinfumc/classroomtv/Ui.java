package org.franklinfumc.classroomtv;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xB8FFFFFF;
    // Inter, bundled in assets/fonts (SIL Open Font License). Falls back to the system font.
    static Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static Typeface SEMIBOLD = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static boolean loaded;

    private Ui() {}

    static void init(Context c) {
        if (loaded) return;
        loaded = true;
        REGULAR = font(c, "Inter-Regular.otf", REGULAR);
        MEDIUM = font(c, "Inter-Medium.otf", MEDIUM);
        SEMIBOLD = font(c, "Inter-SemiBold.otf", SEMIBOLD);
        BOLD = font(c, "Inter-Bold.otf", BOLD);
    }

    private static Typeface font(Context c, String file, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Text sized in pixels, for the screen-width based layout on the home screen. */
    static TextView px(Context c, float px, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Switches a run of text to another typeface (used for the bold words in a sentence). */
    static final class FontSpan extends MetricAffectingSpan {
        private final Typeface face;

        FontSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint p) {
            apply(p);
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            apply(p);
        }

        private void apply(Paint p) {
            p.setTypeface(face);
        }
    }
}

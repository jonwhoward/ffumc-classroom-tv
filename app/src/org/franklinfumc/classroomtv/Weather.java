package org.franklinfumc.classroomtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Current conditions from Open-Meteo (free, no account or key). This is the app's only network
 * request; nothing about the TV or the room is sent.
 */
final class Weather {
    private Weather() {}

    static final class Now {
        final double temp, high, low;
        final String text;
        final int icon;

        Now(double temp, double high, double low, String text, int icon) {
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.text = text;
            this.icon = icon;
        }
    }

    /** Blocking; call off the main thread. Returns null when the weather can't be reached. */
    static Now fetch(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day"
                            + "&daily=temperature_2m_max,temperature_2m_min"
                            + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1",
                    lat, lon));
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            JSONObject j = new JSONObject(read(c.getInputStream()));
            JSONObject cur = j.getJSONObject("current");
            JSONObject day = j.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            boolean isDay = cur.optInt("is_day", 1) == 1;
            return new Now(cur.getDouble("temperature_2m"),
                    day.getJSONArray("temperature_2m_max").getDouble(0),
                    day.getJSONArray("temperature_2m_min").getDouble(0),
                    text(code), icon(code, isDay));
        } catch (Exception e) {
            Log.w("ffumc", "weather unavailable", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    static String text(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Cloudy";
    }

    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? Icon.SUN : Icon.MOON;
        if (code == 2) return day ? Icon.PARTLY : Icon.MOON_CLOUD;
        if (code == 45 || code == 48) return Icon.FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return Icon.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Icon.SNOW;
        if (code >= 95) return Icon.STORM;
        return Icon.CLOUD;
    }

    /** Simple drawn weather icons in the style of the approved preview. */
    static final class Icon extends View {
        static final int SUN = 0, PARTLY = 1, CLOUD = 2, RAIN = 3, SNOW = 4, STORM = 5, FOG = 6,
                MOON = 7, MOON_CLOUD = 8;
        private static final int SUN_C = 0xFFFFD25A, CLOUD_C = 0xFFFFFFFF, RAIN_C = 0xFF7CC4FF;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int kind = PARTLY;

        Icon(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(int k) {
            kind = k;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 64f; // drawn on a 64-unit grid
            c.save();
            c.translate((getWidth() - 64 * s) / 2f, (getHeight() - 64 * s) / 2f);
            c.scale(s, s);
            line.setStrokeWidth(3f);
            switch (kind) {
                case SUN:
                    sun(c, 32, 32, 13, true);
                    break;
                case PARTLY:
                    sun(c, 24, 22, 11, true);
                    cloud(c, 0, 4);
                    break;
                case MOON:
                    moon(c, 32, 32, 15);
                    break;
                case MOON_CLOUD:
                    moon(c, 24, 22, 12);
                    cloud(c, 0, 4);
                    break;
                case RAIN:
                    cloud(c, 0, -6);
                    drops(c);
                    break;
                case SNOW:
                    cloud(c, 0, -6);
                    fill.setColor(CLOUD_C);
                    c.drawCircle(21, 50, 2.6f, fill);
                    c.drawCircle(32, 54, 2.6f, fill);
                    c.drawCircle(43, 50, 2.6f, fill);
                    break;
                case STORM:
                    cloud(c, 0, -6);
                    fill.setColor(SUN_C);
                    Path bolt = new Path();
                    bolt.moveTo(34, 40);
                    bolt.lineTo(26, 52);
                    bolt.lineTo(32, 52);
                    bolt.lineTo(28, 62);
                    bolt.lineTo(40, 48);
                    bolt.lineTo(33, 48);
                    bolt.close();
                    c.drawPath(bolt, fill);
                    break;
                case FOG:
                    cloud(c, 0, -8);
                    line.setColor(0xCCFFFFFF);
                    c.drawLine(14, 48, 50, 48, line);
                    c.drawLine(18, 55, 46, 55, line);
                    break;
                default:
                    cloud(c, 0, 0);
                    break;
            }
            c.restore();
        }

        private void sun(Canvas c, float x, float y, float r, boolean rays) {
            fill.setColor(SUN_C);
            c.drawCircle(x, y, r, fill);
            if (!rays) return;
            line.setColor(SUN_C);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float r1 = r + 4, r2 = r + 8;
                c.drawLine(x + (float) Math.cos(a) * r1, y + (float) Math.sin(a) * r1,
                        x + (float) Math.cos(a) * r2, y + (float) Math.sin(a) * r2, line);
            }
        }

        private void moon(Canvas c, float x, float y, float r) {
            Path moon = new Path();
            moon.addCircle(x, y, r, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(x + r * 0.55f, y - r * 0.35f, r * 0.85f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            fill.setColor(0xFFF4E7B8);
            c.drawPath(moon, fill);
        }

        /** A rounded cloud about 40 units wide, nudged by dx/dy from its usual place. */
        private void cloud(Canvas c, float dx, float dy) {
            fill.setColor(CLOUD_C);
            c.drawRoundRect(new RectF(12 + dx, 36 + dy, 54 + dx, 50 + dy), 7, 7, fill);
            c.drawCircle(24 + dx, 37 + dy, 9, fill);
            c.drawCircle(36 + dx, 31 + dy, 12, fill);
            c.drawCircle(46 + dx, 40 + dy, 8, fill);
        }

        private void drops(Canvas c) {
            line.setColor(RAIN_C);
            c.drawLine(22, 48, 19, 56, line);
            c.drawLine(32, 48, 29, 56, line);
            c.drawLine(42, 48, 39, 56, line);
        }
    }
}

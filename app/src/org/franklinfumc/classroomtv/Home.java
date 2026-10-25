package org.franklinfumc.classroomtv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.view.WindowManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The classroom home screen (design v2, Oct 2026): a seasonal background, frosted-glass cards,
 * logo, clock, room, greeting, one button per HDMI input, current weather, the guest Wi-Fi card
 * and a help line. Every size is a fraction of the screen width ("u" = 1%), matching the approved
 * browser preview (tv/design-preview.html) on any resolution.
 *
 * The glass is not a live blur: Fire OS 8 (Android 11) has no backdrop blur, but the background
 * is a still, already-blurred image, so a translucent panel over it looks the same.
 */
public final class Home extends Activity {
    static final int NAVY = 0xFF04192B;
    static final String EXTRA_WAKE = "wake";
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xC7FFFFFF;
    private static final int RED = 0x7A2426, BLUE = 0x1F4A7C; // rgb only; alpha added per use
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final long ESCAPE_MS = 2500;
    private static final long WEATHER_MS = 30 * 60 * 1000L;
    private static final long WEATHER_RETRY_MS = 2 * 60 * 1000L;

    private final Setup setup = new Setup();
    private final List<View> tiles = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float u; // 1% of the screen width
    private ImageView background, qr;
    private TextView time, date, room, greet, help, wifiName, wifiPass;
    private TextView temp, cond, hilo, place;
    private Weather.Icon icon;
    private LinearLayout wifiCard, row;
    private int menuPresses, season = -1;
    private long firstMenu, weatherAt;
    private boolean resumed;

    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final Runnable weatherLoop = new Runnable() {
        @Override
        public void run() {
            loadWeather();
            ui.postDelayed(this, WEATHER_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        receive(getIntent());
        wakeIfAsked(getIntent());
        setup.refresh(this);
        setContentView(build());
        buildTiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        if (setup.refresh(this)) buildTiles();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clock, f);
        tick();
        scheduleWake();
        long wait = Math.max(0, WEATHER_MS - (SystemClock.elapsedRealtime() - weatherAt));
        if (weatherAt == 0) wait = 0;
        ui.postDelayed(weatherLoop, wait);
        focusFirst();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(weatherLoop);
        unregisterReceiver(clock);
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Guard.shown();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (receive(intent) && setup.refresh(this)) buildTiles();
        wakeIfAsked(intent);
        focusFirst();
    }

    /** The Sunday-morning alarm launches this screen with EXTRA_WAKE: turn the screen on once. */
    private void wakeIfAsked(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_WAKE, false)) return;
        intent.removeExtra(EXTRA_WAKE);
        Guard.wakeNext = false;
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        ui.postDelayed(() -> setTurnScreenOn(false), 15000);
    }

    /** Keeps the screen on during class hours; the rest of the week the TV sleeps as usual. */
    private void stayAwake(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Sets an alarm for the start of the next class hours, to wake the TV to this screen. */
    private void scheduleWake() {
        long at = setup.nextClassHours(Calendar.getInstance());
        AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent wake = PendingIntent.getBroadcast(this, 2, new Intent(this, Wake.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.cancel(wake);
        if (at > 0) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake);
    }

    /** Stores a settings file sent by install.sh with the intent that opened this screen. */
    private boolean receive(Intent intent) {
        String conf = intent == null ? null : intent.getStringExtra("conf64");
        if (conf == null || !Setup.store(this, conf)) return false;
        setup.reset();
        return true;
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    /** Pressing Menu three times quickly opens the TV's settings, for staff. Home comes back here. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.uptimeMillis();
            if (now - firstMenu > ESCAPE_MS) {
                firstMenu = now;
                menuPresses = 0;
            }
            if (++menuPresses >= 3) {
                menuPresses = 0;
                openSettings();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, so the TV
     * settings entry point comes first (from dumbtv's Home.openSettings).
     */
    private void openSettings() {
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS");
        Intent i = tv.resolveActivity(getPackageManager()) != null ? tv
                : new Intent(android.provider.Settings.ACTION_SETTINGS);
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        root.setClipChildren(false);
        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout screen = column();
        screen.setClipChildren(false);
        screen.setClipToPadding(false);
        screen.setPadding(px(4f), px(2.8f), px(4f), px(2f));
        root.addView(screen, new FrameLayout.LayoutParams(MATCH, MATCH));

        screen.addView(topRow(), new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setClipChildren(false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        mlp.topMargin = px(1.6f);
        middle.addView(leftColumn(), new LinearLayout.LayoutParams(0, MATCH, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(px(33f), MATCH);
        rlp.leftMargin = px(3f);
        middle.addView(rightColumn(), rlp);
        screen.addView(middle, mlp);

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(MATCH, WRAP);
        flp.topMargin = px(1.6f);
        screen.addView(footer(), flp);
        return root;
    }

    private View topRow() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setContentDescription("Franklin First United Methodist Church");
        top.addView(logo, new LinearLayout.LayoutParams(px(25), WRAP));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout box = column();
        box.setGravity(Gravity.END);
        time = Ui.px(this, u * 5.6f, WHITE, Ui.BOLD);
        time.setLetterSpacing(-0.03f);
        date = Ui.px(this, u * 1.8f, SOFT, Ui.MEDIUM);
        box.addView(time, new LinearLayout.LayoutParams(WRAP, WRAP));
        box.addView(date, margins(0, 0.6f));
        top.addView(box, new LinearLayout.LayoutParams(WRAP, WRAP));
        return top;
    }

    private View leftColumn() {
        LinearLayout left = column();
        left.setClipChildren(false);
        room = Ui.px(this, u * 1.8f, SOFT, Ui.BOLD);
        room.setLetterSpacing(0.16f);
        room.setAllCaps(true);
        greet = Ui.px(this, u * 5f, WHITE, Ui.BOLD);
        greet.setLetterSpacing(-0.02f);
        greet.setLineSpacing(0, 1.0f);
        TextView sub = Ui.px(this, u * 2.2f, SOFT, Ui.MEDIUM);
        sub.setText("Pick what you want on the TV.");
        left.addView(room, new LinearLayout.LayoutParams(WRAP, WRAP));
        left.addView(greet, margins(0, 0.8f));
        left.addView(sub, margins(0, 1f));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        tlp.topMargin = px(2.4f);
        left.addView(row, tlp);
        return left;
    }

    private View rightColumn() {
        LinearLayout right = column();
        // Weather
        LinearLayout weather = column();
        weather.setBackground(glass());
        weather.setPadding(px(1.8f), px(1.4f), px(1.8f), px(1.4f));
        place = label("Franklin, TN");
        weather.addView(place, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        icon = new Weather.Icon(this);
        now.addView(icon, new LinearLayout.LayoutParams(px(8f), px(8f)));
        LinearLayout nums = column();
        temp = Ui.px(this, u * 6.4f, WHITE, Ui.BOLD);
        temp.setLetterSpacing(-0.03f);
        temp.setText("--°");
        cond = Ui.px(this, u * 2f, WHITE, Ui.SEMIBOLD);
        cond.setText("Loading weather");
        hilo = Ui.px(this, u * 1.7f, SOFT, Ui.REGULAR);
        nums.addView(temp, new LinearLayout.LayoutParams(WRAP, WRAP));
        nums.addView(cond, margins(0, 0.5f));
        nums.addView(hilo, margins(0, 0.4f));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        nlp.leftMargin = px(2f);
        now.addView(nums, nlp);
        weather.addView(now, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        right.addView(weather, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // Guest Wi-Fi
        wifiCard = new LinearLayout(this);
        wifiCard.setOrientation(LinearLayout.HORIZONTAL);
        wifiCard.setGravity(Gravity.CENTER_VERTICAL);
        wifiCard.setBackground(glass());
        wifiCard.setPadding(px(1.4f), px(1.1f), px(1.4f), px(1.1f));
        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_XY);
        qr.setBackground(Ui.round(WHITE, u * 0.8f));
        qr.setPadding(px(0.5f), px(0.5f), px(0.5f), px(0.5f));
        qr.setContentDescription("Scan to join the guest Wi-Fi");
        wifiCard.addView(qr, new LinearLayout.LayoutParams(px(8f), px(8f)));
        LinearLayout wt = column();
        wifiName = Ui.px(this, u * 2f, WHITE, Ui.BOLD);
        wifiPass = Ui.px(this, u * 1.6f, SOFT, Ui.REGULAR);
        wt.addView(label("Guest Wi-Fi"), new LinearLayout.LayoutParams(WRAP, WRAP));
        wt.addView(wifiName, margins(0, 0.6f));
        wt.addView(wifiPass, margins(0, 0.4f));
        LinearLayout.LayoutParams wtl = new LinearLayout.LayoutParams(WRAP, WRAP);
        wtl.leftMargin = px(1.6f);
        wifiCard.addView(wt, wtl);
        LinearLayout.LayoutParams wcl = new LinearLayout.LayoutParams(MATCH, WRAP);
        wcl.topMargin = px(1.4f);
        right.addView(wifiCard, wcl);
        return right;
    }

    private View footer() {
        LinearLayout box = column();
        View rule = new View(this);
        rule.setBackgroundColor(0x24FFFFFF);
        box.addView(rule, new LinearLayout.LayoutParams(MATCH, Math.max(1, px(0.07f))));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, px(1.1f), 0, 0);
        bottom.addView(new Dpad(this), new LinearLayout.LayoutParams(px(2.4f), px(2.4f)));
        TextView hint = Ui.px(this, u * 1.55f, SOFT, Ui.MEDIUM);
        hint.setText(bold("Use the arrows to pick. Press the ", "middle button", " to choose."));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        hlp.leftMargin = px(1f);
        bottom.addView(hint, hlp);
        help = Ui.px(this, u * 1.55f, SOFT, Ui.MEDIUM);
        help.setGravity(Gravity.END);
        help.setLineSpacing(u * 0.3f, 1f);
        bottom.addView(help, new LinearLayout.LayoutParams(WRAP, WRAP));
        box.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        return box;
    }

    /** Translucent white panel with a light edge: frosted glass over the blurred background. */
    private Drawable glass() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x29FFFFFF, 0x10FFFFFF});
        d.setCornerRadius(u * 1.8f);
        d.setStroke(Math.max(1, px(0.07f)), 0x3DFFFFFF);
        return d;
    }

    private TextView label(String s) {
        TextView t = Ui.px(this, u * 1.15f, SOFT, Ui.BOLD);
        t.setLetterSpacing(0.14f);
        t.setAllCaps(true);
        t.setText(s);
        return t;
    }

    // ---------------------------------------------------------------- buttons

    /** One tile per configured button whose HDMI port exists on this TV. */
    private void buildTiles() {
        row.removeAllViews();
        tiles.clear();
        List<TvInputInfo> hdmi = hdmiInputs();
        boolean first = true;
        int n = 0;
        for (Setup.Button b : setup.buttons) {
            TvInputInfo in = port(hdmi, b.port);
            int rgb = n++ == 0 ? RED : BLUE;
            if ((b.color & 0xFFFFFF) != 0) rgb = b.color & 0xFFFFFF;
            View t = tile(b, in, rgb);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
            if (!first) lp.leftMargin = px(2f);
            first = false;
            row.addView(t, lp);
            tiles.add(t);
        }
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            t.setNextFocusLeftId(tiles.get((i + tiles.size() - 1) % tiles.size()).getId());
            t.setNextFocusRightId(tiles.get((i + 1) % tiles.size()).getId());
            t.setNextFocusUpId(t.getId());
            t.setNextFocusDownId(t.getId());
        }
        String r = setup.room;
        room.setText(r.isEmpty() ? "Franklin First" : "Room " + r);
        showWifi();
        tick();
    }

    private View tile(Setup.Button b, TvInputInfo in, int rgb) {
        LinearLayout t = column();
        t.setId(View.generateViewId());
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        t.setMinimumHeight(px(11f));
        t.setPadding(px(2f), px(1.6f), px(2f), px(1.6f));
        t.setBackground(tileBackground(rgb, false));

        TextView port = Ui.px(this, u * 1.25f, WHITE, Ui.BOLD);
        port.setText("HDMI " + b.port);
        port.setLetterSpacing(0.12f);
        port.setPadding(px(0.9f), px(0.4f), px(0.9f), px(0.4f));
        port.setBackground(Ui.round(0x40000000, u * 5f));
        t.addView(port, new LinearLayout.LayoutParams(WRAP, WRAP));
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView name = Ui.px(this, u * 3.1f, WHITE, Ui.BOLD);
        name.setText(b.name);
        name.setSingleLine(true);
        name.setLetterSpacing(-0.01f);
        t.addView(name, margins(0, 0.5f));
        TextView what = Ui.px(this, u * 1.55f, 0xE6FFFFFF, Ui.MEDIUM);
        what.setText(b.what);
        what.setLineSpacing(0, 1.1f);
        t.addView(what, margins(0, 0.5f));

        t.setOnFocusChangeListener((v, has) -> {
            v.setBackground(tileBackground(rgb, has));
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f)
                    .translationZ(has ? px(1f) : 0).setDuration(160).start();
        });
        t.setOnClickListener(v -> open(b, in));
        return t;
    }

    private Drawable tileBackground(int rgb, boolean focused) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xC7000000 | rgb, 0x85000000 | rgb});
        d.setCornerRadius(u * 1.8f);
        if (focused) d.setStroke(px(0.5f), WHITE);
        else d.setStroke(Math.max(1, px(0.07f)), 0x42FFFFFF);
        return d;
    }

    private void open(Setup.Button b, TvInputInfo in) {
        if (in == null) {
            Toast.makeText(this, "This TV has no HDMI " + b.port, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(Input.intent(this, in.getId(), b.name));
    }

    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tv == null) return out;
        for (TvInputInfo in : tv.getTvInputList()) {
            if (in.isPassthroughInput() && in.getType() == TvInputInfo.TYPE_HDMI) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out;
    }

    /** The HDMI input whose label ends in {@code port} ("HDMI 2"), else the port-th HDMI input. */
    private TvInputInfo port(List<TvInputInfo> hdmi, int port) {
        for (TvInputInfo in : hdmi) {
            CharSequence label = in.loadLabel(this);
            String digits = label == null ? "" : label.toString().replaceAll("[^0-9]", "");
            if (digits.equals(String.valueOf(port))) return in;
        }
        return port >= 1 && port <= hdmi.size() ? hdmi.get(port - 1) : null;
    }

    // ---------------------------------------------------------------- live parts

    private void tick() {
        if (time == null) return;
        Calendar now = Calendar.getInstance();
        showSeason(now);
        stayAwake(setup.classHours(now));
        int h = now.get(Calendar.HOUR_OF_DAY);
        String hm = new SimpleDateFormat("h:mm", Locale.US).format(now.getTime());
        SpannableStringBuilder t = new SpannableStringBuilder(hm);
        int s = t.length();
        t.append(h < 12 ? " AM" : " PM");
        t.setSpan(new RelativeSizeSpan(0.46f), s, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(t);
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now.getTime()));
        String part = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String cls = setup.classNow(now);
        greet.setText(cls == null ? part + "." : part + ", " + cls + "!");
        if (setup.deskOpen(now)) {
            SpannableStringBuilder line = new SpannableStringBuilder(bold("Need help? Ask in the ", setup.desk, "."));
            line.append("\n").append(bold("Or email ", setup.email, ""));
            help.setText(line);
        } else {
            help.setText(bold("Need help? Email ", setup.email, ""));
        }
    }

    /** Autumn Sep-Nov, winter Dec-Feb, spring Mar-May, summer Jun-Aug. */
    private void showSeason(Calendar now) {
        int m = now.get(Calendar.MONTH);
        int s = m >= Calendar.SEPTEMBER && m <= Calendar.NOVEMBER ? 0
                : (m == Calendar.DECEMBER || m <= Calendar.FEBRUARY) ? 1
                : m <= Calendar.MAY ? 2 : 3;
        if (s == season) return;
        season = s;
        int[] art = {R.drawable.bg_autumn, R.drawable.bg_winter, R.drawable.bg_spring, R.drawable.bg_summer};
        background.setImageResource(art[s]);
    }

    /** Fills the guest Wi-Fi card from the settings, or hides it when none is set. */
    private void showWifi() {
        if (setup.wifiName.isEmpty()) {
            wifiCard.setVisibility(View.GONE);
            return;
        }
        wifiCard.setVisibility(View.VISIBLE);
        wifiName.setText(setup.wifiName);
        wifiPass.setText(setup.wifiPass.isEmpty() ? "No password"
                : bold("Password ", setup.wifiPass, ""));
        qr.setImageBitmap(Qr.wifi(setup.wifiName, setup.wifiPass, px(7f), 0xFF04192B, WHITE));
    }

    private void loadWeather() {
        weatherAt = SystemClock.elapsedRealtime();
        final double lat = setup.lat, lon = setup.lon;
        new Thread(() -> {
            final Weather.Now w = Weather.fetch(lat, lon);
            runOnUiThread(() -> showWeather(w));
        }, "weather").start();
    }

    private void showWeather(Weather.Now w) {
        if (isFinishing() || temp == null) return;
        place.setText(setup.place);
        if (w == null) {
            // Try again in two minutes instead of waiting for the next half-hour check.
            ui.removeCallbacks(weatherLoop);
            if (resumed) ui.postDelayed(weatherLoop, WEATHER_RETRY_MS);
            if (temp.getText().toString().startsWith("--")) {
                cond.setText("Weather unavailable");
                hilo.setText("");
                icon.set(Weather.Icon.CLOUD);
            }
            return; // keep the last good reading otherwise
        }
        temp.setText(Math.round(w.temp) + "°");
        cond.setText(w.text);
        hilo.setText("High " + Math.round(w.high) + "° · Low " + Math.round(w.low) + "°");
        icon.set(w.icon);
    }

    // ---------------------------------------------------------------- helpers

    private void focusFirst() {
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams margins(float left, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        return lp;
    }

    private CharSequence bold(String before, String strong, String after) {
        SpannableStringBuilder b = new SpannableStringBuilder(before);
        int s = b.length();
        b.append(strong);
        b.setSpan(new Ui.FontSpan(Ui.BOLD), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(WHITE), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append(after);
        return b;
    }

    private int px(float units) {
        return Math.round(units * u);
    }

    /** The round remote button drawn next to the instructions. */
    private static final class Dpad extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dpad(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(SOFT);
            dot.setColor(WHITE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), r = w / 2f;
            ring.setStrokeWidth(w * 0.115f);
            c.drawCircle(r, r, r - ring.getStrokeWidth() / 2f, ring);
            c.drawCircle(r, r, w * 0.17f, dot);
        }
    }
}

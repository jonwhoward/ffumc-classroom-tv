package org.franklinfumc.classroomtv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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
 * The classroom home screen: logo, clock, room, greeting, one big button per HDMI input, and a
 * help line. Every size is a fraction of the screen width, so it matches the approved mockup on
 * any resolution.
 */
public final class Home extends Activity {
    static final int NAVY = 0xFF002A42;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xB8FFFFFF;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final long ESCAPE_MS = 2500;

    private final Setup setup = new Setup();
    private final List<View> tiles = new ArrayList<>();
    private float u; // 1% of the screen width
    private TextView time, date, room, greet, help;
    private LinearLayout row;
    private int menuPresses;
    private long firstMenu;

    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        receive(getIntent());
        setup.refresh(this);
        setContentView(build());
        buildTiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Guard.arrived(this);
        if (setup.refresh(this)) buildTiles();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clock, f);
        tick();
        focusFirst();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        focusFirst();
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

    /** Pressing Menu three times quickly opens Amazon's home, for staff who need TV settings. */
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
                Guard.amazon(this);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(background());
        root.setClipChildren(false);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setClipChildren(false);
        screen.setPadding(px(4.2f), px(3.4f), px(4.2f), px(2.6f));
        root.addView(screen, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Top: logo left, clock right
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setContentDescription("Franklin First United Methodist Church");
        top.addView(logo, new LinearLayout.LayoutParams(px(27), WRAP));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        time = Ui.px(this, u * 5.4f, WHITE, Ui.MEDIUM);
        time.setLetterSpacing(-0.02f);
        date = Ui.px(this, u * 1.7f, SOFT, Ui.REGULAR);
        clockBox.addView(time, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dlp.topMargin = px(0.7f);
        clockBox.addView(date, dlp);
        top.addView(clockBox, new LinearLayout.LayoutParams(WRAP, WRAP));
        screen.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Middle: room, greeting, buttons
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        middle.setClipChildren(false);
        room = Ui.px(this, u * 1.5f, SOFT, Ui.MEDIUM);
        room.setLetterSpacing(0.14f);
        room.setAllCaps(true);
        greet = Ui.px(this, u * 4.6f, WHITE, Ui.BOLD);
        TextView sub = Ui.px(this, u * 2f, SOFT, Ui.REGULAR);
        sub.setText("Pick what you want on the TV.");
        middle.addView(room, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(WRAP, WRAP);
        glp.topMargin = px(0.6f);
        middle.addView(greet, glp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
        slp.topMargin = px(0.6f);
        middle.addView(sub, slp);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = px(2.6f);
        middle.addView(row, rlp);
        screen.addView(middle, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // Bottom: rule, remote hint, help line
        View rule = new View(this);
        rule.setBackgroundColor(0x24FFFFFF);
        screen.addView(rule, new LinearLayout.LayoutParams(MATCH, Math.max(1, px(0.08f))));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, px(1.6f), 0, 0);
        bottom.addView(new Dpad(this), new LinearLayout.LayoutParams(px(2.6f), px(2.6f)));
        TextView hint = Ui.px(this, u * 1.6f, SOFT, Ui.REGULAR);
        hint.setText(bold("Use the arrows to pick. Press the ", "middle button", " to choose."));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        hlp.leftMargin = px(1f);
        bottom.addView(hint, hlp);
        help = Ui.px(this, u * 1.6f, SOFT, Ui.REGULAR);
        bottom.addView(help, new LinearLayout.LayoutParams(WRAP, WRAP));
        screen.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        return root;
    }

    private Drawable background() {
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(1f, 0f);
        glow.setGradientRadius(u * 110f);
        glow.setColors(new int[] {0xF218375D, 0x00002A42});
        return new LayerDrawable(new Drawable[] {new ColorDrawable(NAVY), glow});
    }

    /** One tile per configured button whose HDMI port exists on this TV. */
    private void buildTiles() {
        row.removeAllViews();
        tiles.clear();
        List<TvInputInfo> hdmi = hdmiInputs();
        boolean first = true;
        for (Setup.Button b : setup.buttons) {
            TvInputInfo in = port(hdmi, b.port);
            View t = tile(b, in);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            if (!first) lp.leftMargin = px(2.6f);
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
        help.setText(bold("Need help? Church office ", setup.phone, ""));
        tick();
    }

    private View tile(Setup.Button b, TvInputInfo in) {
        LinearLayout t = new LinearLayout(this);
        t.setId(View.generateViewId());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        t.setMinimumHeight(px(17f));
        t.setPadding(px(2.4f), px(2.2f), px(2.4f), px(2.2f));
        t.setBackground(tileBackground(b.color, false));

        TextView port = Ui.px(this, u * 1.3f, WHITE, Ui.MEDIUM);
        port.setText("HDMI " + b.port);
        port.setLetterSpacing(0.1f);
        port.setPadding(px(1f), px(0.45f), px(1f), px(0.45f));
        port.setBackground(Ui.round(0x38000000, u * 5f));
        t.addView(port, new LinearLayout.LayoutParams(WRAP, WRAP));
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView name = Ui.px(this, u * 4f, WHITE, Ui.BOLD);
        name.setText(b.name);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        nlp.topMargin = px(2.2f);
        t.addView(name, nlp);
        TextView what = Ui.px(this, u * 1.55f, 0xD9FFFFFF, Ui.REGULAR);
        what.setText(b.what);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        wlp.topMargin = px(1f);
        t.addView(what, wlp);

        t.setOnFocusChangeListener((v, has) -> {
            v.setBackground(tileBackground(b.color, has));
            v.animate().scaleX(has ? 1.045f : 1f).scaleY(has ? 1.045f : 1f)
                    .translationZ(has ? px(1f) : 0).setDuration(160).start();
        });
        t.setOnClickListener(v -> open(b, in));
        return t;
    }

    private Drawable tileBackground(int color, boolean focused) {
        GradientDrawable d = Ui.round(color, u * 1.6f);
        if (focused) d.setStroke(px(0.55f), WHITE);
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

    private void tick() {
        if (time == null) return;
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        String hm = new SimpleDateFormat("h:mm", Locale.US).format(now.getTime());
        SpannableStringBuilder t = new SpannableStringBuilder(hm);
        int s = t.length();
        t.append(h < 12 ? " AM" : " PM");
        t.setSpan(new RelativeSizeSpan(0.48f), s, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(t);
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now.getTime()));
        String part = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String cls = setup.classNow(now);
        greet.setText(cls == null ? part + "." : part + ", " + cls + "!");
    }

    private void focusFirst() {
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private CharSequence bold(String before, String strong, String after) {
        SpannableStringBuilder b = new SpannableStringBuilder(before);
        int s = b.length();
        b.append(strong);
        b.setSpan(new StyleSpan(Typeface.BOLD), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

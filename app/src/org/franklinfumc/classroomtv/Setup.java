package org.franklinfumc.classroomtv;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Everything that differs between rooms or changes during the year, read from one text file in the
 * app's private storage. install.sh delivers it inside the intent that opens the home screen
 * (extra "conf64", base64), because Fire OS 8 lets no other app or adb write into this app's folders.
 *
 *   room=B144
 *   email=tech@franklinfumc.org
 *   button=1|iPad or Laptop|Lessons, videos, slides and Zoom|#7A2426
 *   button=2|DVD Player|Plays DVDs and Blu-ray discs|#1F4A7C
 *   class=B144|Sun|08:30|Voyagers
 *
 * A button's first field is its HDMI port number. When the file is missing, the defaults below apply.
 */
final class Setup {
    static final String FILE = "classroom.conf";

    static final class Button {
        final int port;
        final String name, what;
        final int color;

        Button(int port, String name, String what, int color) {
            this.port = port;
            this.name = name;
            this.what = what;
            this.color = color;
        }
    }

    static final class Meeting {
        final String room, name;
        final int day, start; // day is Calendar.SUNDAY..SATURDAY, start is minutes after midnight

        Meeting(String room, int day, int start, String name) {
            this.room = room;
            this.day = day;
            this.start = start;
            this.name = name;
        }
    }

    String room = "";
    String email = "tech@franklinfumc.org";
    final List<Button> buttons = new ArrayList<>();
    final List<Meeting> classes = new ArrayList<>();
    private long stamp = -1;

    private static final String[] DEFAULTS = {
        "button=1|iPad or Laptop|Lessons, videos, slides and Zoom|#7A2426",
        "button=2|DVD Player|Plays DVDs and Blu-ray discs|#1F4A7C",
        "class=B144|Sun|08:30|Voyagers",
        "class=B144|Sun|10:00|Women in Christ",
        "class=B132-133|Sun|10:00|Common Ground",
        "class=A113|Sun|10:00|Thirty-Somethings",
        "class=A114|Sun|10:00|Koinonia",
        "class=A115|Sun|10:00|New Horizons",
    };

    /** Rereads the file when it changed since the last call. Returns true when anything was reloaded. */
    boolean refresh(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        long now = f.isFile() ? f.lastModified() + f.length() : 0;
        if (now == stamp) return false;
        stamp = now;
        room = "";
        email = "tech@franklinfumc.org";
        buttons.clear();
        classes.clear();
        List<String> lines = new ArrayList<>();
        if (now != 0) {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            } catch (IOException e) {
                Log.w("ffumc", "could not read " + f, e);
            }
        }
        boolean anyButton = false, anyClass = false;
        for (String line : lines) {
            String t = line.trim();
            anyButton |= t.startsWith("button=");
            anyClass |= t.startsWith("class=");
        }
        if (!anyButton || !anyClass) {
            for (String d : DEFAULTS) {
                if (!anyButton && d.startsWith("button=")) lines.add(d);
                if (!anyClass && d.startsWith("class=")) lines.add(d);
            }
        }
        for (String line : lines) parse(line.trim());
        return true;
    }

    private void parse(String line) {
        if (line.isEmpty() || line.startsWith("#")) return;
        int eq = line.indexOf('=');
        if (eq < 0) return;
        String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
        String[] f = value.split("\\|", -1);
        try {
            switch (key) {
                case "room":
                    room = value;
                    break;
                case "email":
                    email = value;
                    break;
                case "button":
                    if (f.length >= 3) {
                        int color = f.length >= 4 && !f[3].trim().isEmpty() ? Color.parseColor(f[3].trim()) : 0xFF1F4A7C;
                        buttons.add(new Button(Integer.parseInt(f[0].trim()), f[1].trim(), f[2].trim(), color));
                    }
                    break;
                case "class":
                    if (f.length >= 4) {
                        String[] hm = f[2].trim().split(":");
                        int start = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
                        classes.add(new Meeting(f[0].trim(), day(f[1].trim()), start, f[3].trim()));
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            Log.w("ffumc", "skipped line: " + line, e);
        }
    }

    /** Saves a settings file handed over by install.sh. Returns true when it was stored. */
    static boolean store(Context c, String base64) {
        try {
            byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            File tmp = new File(c.getFilesDir(), FILE + ".new");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                out.write(data);
            }
            return tmp.renameTo(new File(c.getFilesDir(), FILE));
        } catch (IOException | IllegalArgumentException e) {
            Log.w("ffumc", "could not store settings", e);
            return false;
        }
    }

    /** Forgets what was loaded, so the next refresh rereads the file. */
    void reset() {
        stamp = -1;
    }

    private static int day(String s) {
        String d = s.toLowerCase();
        if (d.startsWith("mon")) return Calendar.MONDAY;
        if (d.startsWith("tue")) return Calendar.TUESDAY;
        if (d.startsWith("wed")) return Calendar.WEDNESDAY;
        if (d.startsWith("thu")) return Calendar.THURSDAY;
        if (d.startsWith("fri")) return Calendar.FRIDAY;
        if (d.startsWith("sat")) return Calendar.SATURDAY;
        return Calendar.SUNDAY;
    }

    /**
     * The class meeting in this room right now, or null. A class shows from 10 minutes before it
     * starts (an hour for the first time slot of that day) until 10 minutes before the room's next
     * class, or two hours after it starts.
     */
    String classNow(Calendar now) {
        if (room.isEmpty()) return null;
        int day = now.get(Calendar.DAY_OF_WEEK);
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int firstSlot = Integer.MAX_VALUE;
        List<Meeting> here = new ArrayList<>();
        for (Meeting k : classes) {
            if (k.day != day) continue;
            firstSlot = Math.min(firstSlot, k.start);
            if (k.room.equalsIgnoreCase(room)) here.add(k);
        }
        java.util.Collections.sort(here, (a, b) -> Integer.compare(a.start, b.start));
        for (int i = 0; i < here.size(); i++) {
            Meeting k = here.get(i);
            int from = k.start - (k.start == firstSlot ? 60 : 10);
            int to = i + 1 < here.size() ? here.get(i + 1).start - 10 : k.start + 120;
            if (mins >= from && mins < to) return k.name;
        }
        return null;
    }
}

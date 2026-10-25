package org.franklinfumc.classroomtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by the alarm Home sets for the start of class hours: wakes the TV to the home screen. */
public final class Wake extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Guard.wake(c);
    }
}

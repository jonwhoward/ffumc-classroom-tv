package org.franklinfumc.classroomtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * Fired by the alarm Home sets for the start of class hours: turns the TV on and shows the home
 * screen. Fire OS ignored the activity's setTurnScreenOn when our screen was already on top, so this
 * asks the power manager directly with a wake lock that wakes the screen.
 */
public final class Wake extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent i) {
        Log.i("ffumc", "wake alarm fired");
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "ffumc:wake");
            lock.acquire(10000);
            Log.i("ffumc", "wake lock taken; screen interactive=" + pm.isInteractive());
        } catch (Exception e) {
            Log.w("ffumc", "wake lock failed", e);
        }
        Guard.wake(c);
    }
}

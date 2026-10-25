package tv.bettertv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the home keeper, and the Home button if Fire OS dropped it, back after a reboot or an update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Keeper.start(context);
        Guard.sync(context);
    }
}

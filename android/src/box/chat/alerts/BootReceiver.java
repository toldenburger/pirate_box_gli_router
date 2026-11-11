package box.chat.alerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the listener after a phone restart (if alerts are switched on). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Prefs.enabled(context)) {
            context.startForegroundService(new Intent(context, ListenerService.class));
        }
    }
}

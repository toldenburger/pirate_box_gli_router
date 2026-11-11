package box.chat.alerts;

import android.content.Context;
import android.content.SharedPreferences;

/** Small settings store. The box address is fixed: it must match res/xml/network_security_config.xml. */
final class Prefs {
    static final String BASE = "http://192.168.60.1";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("box", Context.MODE_PRIVATE);
    }

    /** Your nickname: no alerts for your own messages, and the name used when you send from the app. */
    static String name(Context c) {
        return sp(c).getString("name", "owner");
    }

    static void setName(Context c, String v) {
        sp(c).edit().putString("name", v).apply();
    }

    /** Whether the background listener should run (also after a reboot). */
    static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", true);
    }

    static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("enabled", v).apply();
    }

    /** Newest message id already alerted/seen; -1 = never connected (history is never alerted). */
    static int lastSeen(Context c) {
        return sp(c).getInt("last_seen", -1);
    }

    static void setLastSeen(Context c, int v) {
        sp(c).edit().putInt("last_seen", v).apply();
    }

    private Prefs() {}
}

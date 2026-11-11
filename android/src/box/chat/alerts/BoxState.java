package box.chat.alerts;

import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Shared state between the background service and the screen. */
final class BoxState {
    static volatile Network network;     // the box's Wi-Fi, null when not connected
    static volatile boolean connected;
    /** One line of diagnosis shown on screen: what the listener is doing / the last error. */
    static volatile String detail = "service has not started yet";

    static void detail(String s) {
        detail = s;
        changed();
    }

    private static final List<ChatParser.Msg> MSGS = new ArrayList<>();
    private static volatile Runnable listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void setListener(Runnable r) {
        listener = r;
    }

    static void connected(Network n) {
        boolean changed = !connected || network == null || !network.equals(n);
        network = n;
        connected = true;
        if (changed) changed();
    }

    static void disconnected() {
        boolean changed = connected;
        network = null;
        connected = false;
        if (changed) changed();
    }

    static synchronized void setMessages(List<ChatParser.Msg> list) {
        MSGS.clear();
        MSGS.addAll(list);
        changed();
    }

    static synchronized void add(ChatParser.Msg m) {
        MSGS.add(m);
        while (MSGS.size() > 200) MSGS.remove(0);
        changed();
    }

    static synchronized List<ChatParser.Msg> snapshot() {
        return new ArrayList<>(MSGS);
    }

    private static void changed() {
        Runnable r = listener;
        if (r != null) MAIN.post(r);
    }

    private BoxState() {}
}

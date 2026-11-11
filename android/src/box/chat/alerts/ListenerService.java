package box.chat.alerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service. Asks Android for a Wi-Fi network (no internet needed), and when one shows up
 * it checks whether it is the Pirate Box. If so it polls the chat every 5 seconds over THAT network
 * (mobile data stays the default for everything else) and raises a notification for new messages.
 */
public class ListenerService extends Service {
    private static final String CH_STATUS = "status";
    private static final String CH_MSG = "messages";
    private static final int ID_FG = 1;
    private static final int POLL_MS = 5000;

    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Thread poller;
    private Network pollerNet;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_STATUS, "Listener status", NotificationManager.IMPORTANCE_MIN));
        nm.createNotificationChannel(new NotificationChannel(CH_MSG, "Chat messages", NotificationManager.IMPORTANCE_HIGH));

        Notification n = ongoing("Waiting for the Pirate Box Wi-Fi");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_FG, n);
        }

        BoxState.detail("Service running, waiting for a Wi-Fi network");
        cm = getSystemService(ConnectivityManager.class);
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                BoxState.detail("A Wi-Fi network is available (" + network + "), checking if it is the box");
                startPolling(network);
            }

            @Override
            public void onLost(Network network) {
                stopPolling(network);
                BoxState.detail("Wi-Fi lost (" + network + "), waiting for a Wi-Fi network");
            }
        };
        cm.requestNetwork(req, callback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // already unregistered
        }
        synchronized (this) {
            stopPollingLocked();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void startPolling(final Network n) {
        stopPollingLocked();
        pollerNet = n;
        poller = new Thread(new Runnable() {
            @Override
            public void run() {
                pollLoop(n);
            }
        }, "box-poller");
        poller.start();
    }

    private synchronized void stopPolling(Network n) {
        if (n.equals(pollerNet)) stopPollingLocked();
    }

    private void stopPollingLocked() {
        if (poller != null) poller.interrupt();
        poller = null;
        pollerNet = null;
        BoxState.disconnected();
        updateOngoing("Waiting for the Pirate Box Wi-Fi");
    }

    private void pollLoop(Network n) {
        int lastSeen = Prefs.lastSeen(this);
        boolean synced = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int since = synced ? lastSeen : 0;
                String body = BoxClient.get(n, Prefs.BASE + "/cgi-bin/chat?since=" + since, 4000);
                ChatParser.Result r = ChatParser.parse(body);
                if (!r.ok) {
                    // some other Wi-Fi, not the box: look again later
                    BoxState.disconnected();
                    BoxState.detail("Reached an address but the reply is not the box: "
                            + body.substring(0, Math.min(60, body.length())).replace('\n', ' '));
                    Thread.sleep(15000);
                    continue;
                }
                BoxState.connected(n);
                BoxState.detail("Connected. Last check " + java.text.DateFormat.getTimeInstance().format(new java.util.Date()));
                if (!synced) {
                    BoxState.setMessages(r.msgs);
                    // messages that arrived while we were away get an alert; the very first run alerts nothing
                    for (ChatParser.Msg m : r.msgs) {
                        if (lastSeen >= 0 && m.id > lastSeen) alert(m);
                    }
                    lastSeen = r.last;
                    Prefs.setLastSeen(this, lastSeen);
                    synced = true;
                    updateOngoing("Connected to the Pirate Box");
                } else if (r.last < lastSeen) {
                    // the chat was cleared on the box: start over quietly
                    lastSeen = r.last;
                    synced = false;
                } else {
                    for (ChatParser.Msg m : r.msgs) {
                        BoxState.add(m);
                        alert(m);
                        if (m.id > lastSeen) lastSeen = m.id;
                    }
                    Prefs.setLastSeen(this, lastSeen);
                }
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                BoxState.disconnected();
                BoxState.detail("Could not reach the box: " + e);   // e.g. a cleartext/permission/timeout error
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private void alert(ChatParser.Msg m) {
        if (m.name.trim().equalsIgnoreCase(Prefs.name(this).trim())) return;   // your own message
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_MSG)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setContentTitle(m.name)
                .setContentText(m.text)
                .setStyle(new Notification.BigTextStyle().bigText(m.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroup("box_chat")
                .build();
        getSystemService(NotificationManager.class).notify(1000 + m.id, n);
    }

    private Notification ongoing(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setContentTitle("Box Chat")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    private void updateOngoing(String text) {
        getSystemService(NotificationManager.class).notify(ID_FG, ongoing(text));
    }
}

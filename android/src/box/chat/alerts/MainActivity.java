package box.chat.alerts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** One screen: connection status, the chat so far, a box to reply, your nickname, and the alerts switch. */
public class MainActivity extends Activity {
    private TextView status;
    private TextView detailView;
    private TextView log;
    private ScrollView scroll;
    private EditText input;
    private Button toggle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (Prefs.enabled(this)) startForegroundService(new Intent(this, ListenerService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        BoxState.setListener(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
        refresh();
    }

    @Override
    protected void onPause() {
        BoxState.setListener(null);
        super.onPause();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setFitsSystemWindows(true);   // keep clear of the status/navigation bars

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        detailView = new TextView(this);
        detailView.setTextSize(12);
        detailView.setPadding(0, dp(2), 0, dp(4));
        root.addView(detailView);

        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(15);
        log.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Say something...");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        sendRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        Button send = new Button(this);
        send.setText("Send");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        sendRow.addView(send);
        root.addView(sendRow);

        EditText nick = new EditText(this);
        nick.setHint("Your nickname (no alerts for your own messages)");
        nick.setSingleLine(true);
        nick.setText(Prefs.name(this));
        nick.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable e) {
                Prefs.setName(MainActivity.this, e.toString());
            }
        });
        root.addView(nick);

        toggle = new Button(this);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.enabled(MainActivity.this);
                Prefs.setEnabled(MainActivity.this, on);
                Intent svc = new Intent(MainActivity.this, ListenerService.class);
                if (on) startForegroundService(svc); else stopService(svc);
                refresh();
            }
        });
        root.addView(toggle);

        setContentView(root);
    }

    private void refresh() {
        boolean on = Prefs.enabled(this);
        toggle.setText(on ? "Alerts are ON (tap to turn off)" : "Alerts are OFF (tap to turn on)");
        if (!on) {
            status.setText("Alerts are off");
        } else {
            status.setText(BoxState.connected
                    ? "Connected to the Pirate Box"
                    : "Not connected - waiting for the piratebox_open Wi-Fi");
        }
        detailView.setText("Detail: " + BoxState.detail);
        List<ChatParser.Msg> msgs = BoxState.snapshot();
        StringBuilder sb = new StringBuilder();
        for (ChatParser.Msg m : msgs) sb.append(m.name).append(": ").append(m.text).append("\n");
        log.setText(msgs.isEmpty() ? "(no messages yet)" : sb.toString());
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        String nm = Prefs.name(this).trim();
        final String body = (nm.isEmpty() ? "anon" : nm) + "\n" + text;
        input.setText("");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BoxClient.post(BoxState.network, Prefs.BASE + "/cgi-bin/chat", body);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Could not send: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}

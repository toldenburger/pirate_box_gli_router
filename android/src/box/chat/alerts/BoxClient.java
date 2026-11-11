package box.chat.alerts;

import android.net.Network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP to the box over one specific Network. network.openConnection() keeps the traffic on that
 * Wi-Fi even though Android's default network is mobile data (the Wi-Fi has no internet).
 */
final class BoxClient {

    static String get(Network net, String url, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) net.openConnection(new URL(url));
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setUseCaches(false);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    /** Body is "nickname\nmessage", exactly what the chat page sends. */
    static void post(Network net, String url, String body) throws IOException {
        if (net == null) throw new IOException("not connected to the box");
        HttpURLConnection c = (HttpURLConnection) net.openConnection(new URL(url));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(4000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Content-Type", "text/plain");
        c.setFixedLengthStreamingMode(bytes.length);
        try {
            try (OutputStream o = c.getOutputStream()) {
                o.write(bytes);
            }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private BoxClient() {}
}

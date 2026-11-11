package box.chat.alerts;

import java.util.ArrayList;
import java.util.List;

/** Parses the box's /cgi-bin/chat reply: first line "#<last id>", then "id TAB name TAB text" lines. */
final class ChatParser {

    static final class Msg {
        final int id;
        final String name;
        final String text;

        Msg(int id, String name, String text) {
            this.id = id;
            this.name = name;
            this.text = text;
        }
    }

    static final class Result {
        boolean ok;      // false = this is not the box (wrong first line)
        int last = -1;   // newest message id on the box
        final List<Msg> msgs = new ArrayList<>();
    }

    static Result parse(String body) {
        Result r = new Result();
        if (body == null) return r;
        String[] lines = body.split("\n", -1);
        if (lines.length == 0 || !lines[0].startsWith("#")) return r;
        try {
            r.last = Integer.parseInt(lines[0].substring(1).trim());
        } catch (NumberFormatException e) {
            return r;
        }
        r.ok = true;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].replace("\r", "");
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", 3);
            if (f.length < 3) continue;
            try {
                r.msgs.add(new Msg(Integer.parseInt(f[0].trim()), f[1], f[2]));
            } catch (NumberFormatException e) {
                // skip a malformed line
            }
        }
        return r;
    }

    private ChatParser() {}
}

package box.chat.alerts;

/** Runs on a normal JVM: java -cp out box.chat.alerts.ParserTest. Exit code 1 if anything is wrong. */
public class ParserTest {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] a) {
        // exactly what the box sends: "#last" then "id TAB name TAB text"
        ChatParser.Result r = ChatParser.parse("#2\n1\towner\teverybody is welcome to talk and share\n2\tHi\tHi\n");
        check(r.ok && r.last == 2 && r.msgs.size() == 2, "normal reply: 2 messages, last = 2");
        check(r.msgs.get(0).name.equals("owner") && r.msgs.get(0).text.startsWith("everybody"), "first message fields");
        check(r.msgs.get(1).id == 2 && r.msgs.get(1).text.equals("Hi"), "second message fields");

        r = ChatParser.parse("#0\n");
        check(r.ok && r.last == 0 && r.msgs.isEmpty(), "empty chat");

        r = ChatParser.parse("#5\r\n5\tann\ttext with\ta tab inside\r\n");
        check(r.ok && r.msgs.size() == 1 && r.msgs.get(0).text.equals("text with\ta tab inside"), "CRLF and tab inside the text kept");

        check(!ChatParser.parse("<!doctype html><html>").ok, "a web page that is not the box is rejected");
        check(!ChatParser.parse("").ok, "empty reply rejected");
        check(!ChatParser.parse(null).ok, "null rejected");
        check(!ChatParser.parse("#abc\n").ok, "bad header number rejected");

        r = ChatParser.parse("#3\nnot a message\n3\tx\n3\tzed\thello\n");
        check(r.ok && r.msgs.size() == 1 && r.msgs.get(0).name.equals("zed"), "malformed lines skipped");

        r = ChatParser.parse("#1\n1\témilie\tcafé ☕\n");
        check(r.msgs.size() == 1 && r.msgs.get(0).text.equals("café ☕"), "unicode text");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}

package prosefx.core.editor;

import com.atlassian.prosemirror.model.Node;
import java.text.BreakIterator;
import java.util.Locale;

/** Word counts, by Unicode word boundaries, so Tamil, Hindi and English count the same way. */
public final class Stats {

    public static int words(Node doc, int from, int to) {
        return words(doc.textBetween(from, to, "\n", "\n"));
    }

    public static int words(String text) {
        var it = BreakIterator.getWordInstance(Locale.ROOT);
        it.setText(text);
        var n = 0;
        for (int start = it.first(), end = it.next(); end != BreakIterator.DONE; start = end, end = it.next())
            if (hasLetterOrDigit(text, start, end)) n++;
        return n;
    }

    static boolean hasLetterOrDigit(String s, int from, int to) {
        for (var i = from; i < to; i++) if (Character.isLetterOrDigit(s.charAt(i))) return true;
        return false;
    }

    private Stats() {}
}

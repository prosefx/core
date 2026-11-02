package prosefx.core.editor;

import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.*;
import java.util.*;
import java.util.regex.*;

import prosefx.core.pm.Pm;
import static prosefx.core.schema.EditorSchema.HARD_BREAK;

/**
 * Find and replace over the document's text, within each block (a match never spans two paragraphs). Plain by
 * default (ignoring case, anywhere in a word); Options add regular expressions, matching case and whole words.
 * Matches are document ranges; replacing keeps the formatting of the text it replaces.
 */
public final class Find {

    public record Match(int from, int to) {}

    /** regex: the query is a Java regular expression, with ^ and $ at each line of a block. matchCase: upper and lower case differ. wholeWord: the match is not inside a longer word (letters, digits, marks and _ make a word). */
    public record Options(boolean regex, boolean matchCase, boolean wholeWord) {
        public static final Options PLAIN = new Options(false, false, false);
    }

    /** A match with where it is, for a list of results: block is the number of its textblock in the document (from 0), line the text of the line it starts on (a hard break ends a line) and column where it starts in that line. */
    public record Hit(int from, int to, int block, String line, int column) {}

    /** What a range is to be replaced with; in regex mode $1 and the like are already expanded. */
    public record Replacement(int from, int to, String text) {}

    interface Visitor { void at(int block, int base, String text, Matcher m); }

    static final String WORD = "[\\p{L}\\p{N}\\p{M}_]";

    public static List<Match> all(Node doc, String query) { return all(doc, query, Options.PLAIN); }

    public static List<Match> all(Node doc, String query, Options options) {
        var out = new ArrayList<Match>();
        walk(doc, query, options, (block, base, text, m) -> {
            if (m.end() > m.start()) out.add(new Match(base + m.start(), base + m.end()));
        });
        return out;
    }

    public static List<Hit> hits(Node doc, String query, Options options) {
        var out = new ArrayList<Hit>();
        walk(doc, query, options, (block, base, text, m) -> {
            if (m.end() == m.start()) return;
            var from = text.lastIndexOf('\n', m.start() - 1) + 1;
            var to = text.indexOf('\n', m.start());
            out.add(new Hit(base + m.start(), base + m.end(), block, text.substring(from, to < 0 ? text.length() : to), m.start() - from));
        });
        return out;
    }

    /** Why the query cannot be used (a regular expression that does not parse), or null when it can. */
    public static String problem(String query, Options options) {
        if (query == null || query.isEmpty()) return null;
        try {
            pattern(query, options);
            return null;
        } catch (PatternSyntaxException e) {
            return e.getDescription();
        }
    }

    /**
     * What every match is to be replaced with. In regex mode the replacement may refer to groups ($1, ${name}) and
     * \$ is a dollar sign; otherwise it is used as it is. A replacement that refers to a group the pattern lacks
     * throws IllegalArgumentException or IndexOutOfBoundsException.
     */
    public static List<Replacement> replacements(Node doc, String query, Options options, String with) {
        var out = new ArrayList<Replacement>();
        var sb = new StringBuilder();
        var seen = new Matcher[1];
        var appended = new int[1];
        walk(doc, query, options, (block, base, text, m) -> {
            if (m != seen[0]) { seen[0] = m; appended[0] = 0; }
            var replacement = with;
            if (options.regex()) {
                sb.setLength(0);
                m.appendReplacement(sb, with);
                replacement = sb.substring(m.start() - appended[0]);
                appended[0] = m.end();
            }
            if (m.end() > m.start()) out.add(new Replacement(base + m.start(), base + m.end(), replacement));
        });
        return out;
    }

    static Pattern pattern(String query, Options o) {
        var core = o.regex() ? query : Pattern.quote(query);
        var source = o.wholeWord() ? "(?<!" + WORD + ")(?:" + core + ")(?!" + WORD + ")" : core;
        return Pattern.compile(source, (o.matchCase() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) | (o.regex() ? Pattern.MULTILINE : 0));
    }

    static void walk(Node doc, String query, Options options, Visitor visitor) {
        if (query == null || query.isEmpty()) return;
        Pattern p;
        try {
            p = pattern(query, options);
        } catch (PatternSyntaxException e) {
            return;
        }
        var block = new int[1];
        doc.descendants((n, pos, parent, i) -> {
            if (!n.isTextblock()) return true;
            var text = blockText(n);
            var m = p.matcher(text);
            while (m.find()) visitor.at(block[0], pos + 1, text, m);
            block[0]++;
            return false;
        });
    }

    /** One character per position, as drawn: text as is, a hard break as a newline, any other leaf as a placeholder. */
    static String blockText(Node textblock) {
        var sb = new StringBuilder();
        for (var c : textblock.getContent().getContent())
            sb.append(c.isText() ? c.getText() : c.getType() == HARD_BREAK ? "\n" : "￼");
        return sb.toString();
    }

    /** The match at or after pos (or before it, going back), wrapping round the document. */
    public static Match next(List<Match> matches, int pos, boolean forward) {
        if (matches.isEmpty()) return null;
        if (forward) {
            for (var m : matches) if (m.from() >= pos) return m;
            return matches.getFirst();
        }
        for (var k = matches.size() - 1; k >= 0; k--) if (matches.get(k).to() <= pos) return matches.get(k);
        return matches.getLast();
    }

    public static Transaction selectMatch(PMEditorState state, Match m) {
        return state.getTr().setSelection(Pm.text(state.getDoc(), m.from(), m.to())).scrollIntoView();
    }

    /** Replace one match; the caret lands after the replacement. */
    public static Transaction replace(PMEditorState state, Match m, String with) {
        var tr = state.getTr();
        tr.insertText(with, m.from(), m.to());
        return tr.scrollIntoView();
    }

    /** Replace every match in one step, so one undo brings them all back. */
    public static Transaction replaceAll(PMEditorState state, List<Match> matches, String with) {
        var tr = state.getTr();
        for (var k = matches.size() - 1; k >= 0; k--) tr.insertText(with, matches.get(k).from(), matches.get(k).to());
        return tr.scrollIntoView();
    }

    /** Make these replacements in one step, so one undo brings them all back. */
    public static Transaction replaceAll(PMEditorState state, List<Replacement> replacements) {
        var tr = state.getTr();
        for (var k = replacements.size() - 1; k >= 0; k--) {
            var r = replacements.get(k);
            tr.insertText(r.text(), r.from(), r.to());
        }
        return tr.scrollIntoView();
    }

    private Find() {}
}

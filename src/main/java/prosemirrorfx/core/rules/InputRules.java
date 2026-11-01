package prosemirrorfx.core.rules;

import com.atlassian.prosemirror.state.*;
import java.util.*;
import java.util.regex.Pattern;

import static prosemirrorfx.core.pm.Pm.*;
import static prosemirrorfx.core.schema.EditorSchema.*;

/** The markdown-style shortcuts, checked as each character is typed, like prosemirror-inputrules. */
public final class InputRules {

    static final int MAX_MATCH = 500;

    static final Map<Pattern, InputRule> RULES = new LinkedHashMap<>();

    static {
        RULES.put(Pattern.compile("^(#{1,6})\\s$"),
                InputRule.textblockType(HEADING, m -> Map.of("level", m.group(1).length())));
        RULES.put(Pattern.compile("^\\s*([-+*])\\s$"),
                InputRule.wrapping(BULLET_LIST, m -> null, (m, n) -> true));
        RULES.put(Pattern.compile("^(\\d+)\\.\\s$"),
                InputRule.wrapping(ORDERED_LIST, m -> Map.of("order", Integer.parseInt(m.group(1))),
                        (m, n) -> n.getChildCount() + intAttr(n, "order") == Integer.parseInt(m.group(1))));
        RULES.put(Pattern.compile("\\*\\*([^*\\s](?:[^*]*[^*\\s])?)\\*\\*$"), InputRule.mark(STRONG, 2));
        RULES.put(Pattern.compile("(?:^|[^_\\w])_([^_\\s](?:[^_]*[^_\\s])?)_$"), InputRule.mark(EM, 1));
        // `code`: the closing backtick turns what is between the two into an inline code span (``` for a fence has nothing between, so it does not fire)
        RULES.put(Pattern.compile("(?:^|[^`])`([^`]+)`$"), InputRule.mark(CODE, 1));
        // ^^text^^ (Logseq) a highlight, ~~text~~ (GitHub) a strike: the closing pair marks what is between, as ** does
        RULES.put(Pattern.compile("\\^\\^([^^\\s](?:[^^]*[^^\\s])?)\\^\\^$"), InputRule.mark(HIGHLIGHT, 2));
        RULES.put(Pattern.compile("~~([^~\\s](?:[^~]*[^~\\s])?)~~$"), InputRule.mark(STRIKE, 2));
    }

    /** The transaction a rule makes of typing text over from..to, or null when no rule fires. */
    public static Transaction match(PMEditorState state, int from, int to, String text) {
        var pos = state.getDoc().resolve(from);
        // a fenced block is a verbatim paragraph: what is typed in it is code, not Markdown to be turned into formatting
        if (isCode(pos.getParent()) || Boolean.TRUE.equals(attr(pos.getParent(), "verbatim"))) return null;
        var before = pos.getParent().textBetween(Math.max(0, pos.getParentOffset() - MAX_MATCH), pos.getParentOffset(), null, "￼") + text;
        var shift = from - (before.length() - text.length());
        for (var e : RULES.entrySet()) {
            var m = e.getKey().matcher(before);
            if (!m.find() || m.group().length() < text.length()) continue;
            var tr = e.getValue().apply(state, m, i -> shift + i, to);
            if (tr != null) return tr;
        }
        return null;
    }

    private InputRules() {}
}

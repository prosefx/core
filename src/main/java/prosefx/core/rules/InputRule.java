package prosefx.core.rules;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.Map;
import java.util.function.*;
import java.util.regex.*;

import static prosefx.core.pm.Pm.*;

/** One prosemirror-inputrules rule: a pattern over the text before the caret, and what to do when it matches. */
@FunctionalInterface
public interface InputRule {

    /** posOf maps an index in the matched text to a document position; to is where typing happens. */
    Transaction apply(PMEditorState state, Matcher m, IntUnaryOperator posOf, int to);

    static InputRule wrapping(NodeType type, Function<Matcher, Map<String, ?>> attrs, BiPredicate<Matcher, Node> join) {
        return (state, m, posOf, to) -> {
            var start = posOf.applyAsInt(m.start());
            var tr = state.getTr();
            tr.delete(start, to);
            var range = blockRange(tr.getDoc().resolve(start), tr.getDoc().resolve(start));
            var wrapping = range == null ? null : StructureKt.findWrapping(range, type, attrs.apply(m), range);
            if (wrapping == null) return null;
            tr.wrap(range, wrapping);
            var before = tr.getDoc().resolve(start - 1).getNodeBefore();
            if (before != null && before.getType() == type && StructureKt.canJoin(tr.getDoc(), start - 1) && join.test(m, before))
                tr.join(start - 1, 1);
            return tr;
        };
    }

    static InputRule textblockType(NodeType type, Function<Matcher, Map<String, ?>> attrs) {
        return (state, m, posOf, to) -> {
            var start = posOf.applyAsInt(m.start());
            var p = state.getDoc().resolve(start);
            if (!p.node(-1).canReplaceWith(p.index(-1), p.indexAfter(-1), type, null)) return null;
            var tr = state.getTr();
            tr.delete(start, to);
            tr.setBlockType(start, start, type, attrs.apply(m));
            return tr;
        };
    }

    /** Group 1 is the marked text; delim is the delimiter length on each side. */
    static InputRule mark(MarkType type, int delim) {
        return (state, m, posOf, to) -> {
            var start = posOf.applyAsInt(m.start(1) - delim);
            var inner = m.group(1);
            var marks = type.create(null).addToSet(state.getDoc().resolve(start).marks());
            var tr = state.getTr();
            tr.replaceWith(start, to, state.getSchema().text(inner, marks));
            removeStoredMark(tr, type);
            return tr;
        };
    }
}

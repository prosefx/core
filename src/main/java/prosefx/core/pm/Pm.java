package prosefx.core.pm;

import com.atlassian.prosemirror.history.*;
import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.*;
import java.util.function.Predicate;
import kotlin.Unit;

/** Java face of prosemirror-kotlin: one place for the Kotlin default arguments Java cannot omit. */
public final class Pm {

    static {
        // prosemirror-kotlin's "safe mode" (on by default) catches any exception while a transaction is applied
        // and keeps the old state, so the user's edit vanishes without a trace. A visible error is better.
        com.atlassian.prosemirror.util.ToolsKt.setSafeMode(false);
    }

    public static final Slice EMPTY_SLICE = Slice.Companion.getEmpty();
    public static final Fragment EMPTY = Fragment.Companion.getEmpty();

    public static PMEditorState state(Node doc, Selection selection) {
        List<Plugin<?>> plugins = List.of(new HistoryPlugin(new HistoryOptionsConfig(200, 500)));
        return PMEditorState.Companion.create(new EmptyEditorStateConfig(null, doc, selection, null, plugins));
    }

    public static PMEditorState state(Node doc) { return state(doc, atStart(doc)); }

    public static Selection atStart(Node doc) { return Selection.Companion.atStart(doc); }

    public static Selection atEnd(Node doc) { return Selection.Companion.atEnd(doc); }

    public static TextSelection text(Node doc, int anchor, int head) {
        return TextSelection.Companion.create(doc, anchor, head, false);
    }

    public static TextSelection cursor(Node doc, int pos) { return text(doc, pos, pos); }

    public static Selection between(ResolvedPos anchor, ResolvedPos head) {
        return TextSelection.Companion.between(anchor, head, null);
    }

    public static Selection near(ResolvedPos pos, int bias) { return Selection.Companion.near(pos, bias); }

    public static Selection findFrom(ResolvedPos pos, int dir) { return Selection.Companion.findFrom(pos, dir, false); }

    public static Fragment frag(Node... nodes) { return Fragment.Companion.from(Arrays.asList(nodes)); }

    public static Fragment frag(Node node) { return Fragment.Companion.from(node); }

    public static Slice slice(Fragment content, int openStart, int openEnd) { return new Slice(content, openStart, openEnd); }

    public static Node create(NodeType type, Map<String, ?> attrs, Fragment content) {
        return type.create(attrs, content, null);
    }

    public static Node create(NodeType type, Fragment content) { return create(type, null, content); }

    public static Node createAndFill(NodeType type) { return type.createAndFill(null, (Fragment) null, null); }

    public static Node copy(Node node, Fragment content) { return node.copy(content, null); }

    public static NodeRange blockRange(ResolvedPos from, ResolvedPos to) { return from.blockRange(to, null); }

    public static NodeRange blockRange(ResolvedPos from, ResolvedPos to, Predicate<Node> pred) {
        return from.blockRange(to, n -> pred.test(n));
    }

    public static Step replaceStep(Node doc, int from, int to) {
        return com.atlassian.prosemirror.transform.ReplaceKt.replaceStep(doc, from, to, EMPTY_SLICE);
    }

    /** Attributes are stored only when given, so defaults must be read through computeAttr. */
    public static Object attr(Node node, String name) { return node.computeAttr(name); }

    public static int intAttr(Node node, String name) { return ((Number) attr(node, name)).intValue(); }

    /** An attribute that is a string, or null when it was not given. */
    public static String str(Node node, String name) { return (String) attr(node, name); }

    /** name, value, name, value...; unlike Map.of it accepts a null value. */
    public static Map<String, Object> attrs(Object... pairs) {
        var m = new HashMap<String, Object>();
        for (var k = 0; k < pairs.length; k += 2) m.put((String) pairs[k], pairs[k + 1]);
        return m;
    }

    /**
     * prosemirror-kotlin 1.1.21 workaround: MarkType.removeFromSet keeps the marks of the type instead of
     * dropping them, so Transaction.removeStoredMark(MarkType) is a no-op. Same intent, done directly.
     */
    public static void removeStoredMark(Transaction tr, MarkType type) {
        var current = tr.getStoredMarks() != null ? tr.getStoredMarks() : tr.getSelection().get_head().marks();
        tr.ensureMarks(current.stream().filter(m -> m.getType() != type).toList());
    }

    /**
     * prosemirror-kotlin 1.1.21 workaround: Transform.removeMark returns false for block nodes in its
     * nodesBetween callback, which stops it descending, so it never reaches the text. Same steps, done directly.
     */
    public static void removeMark(Transform tr, int from, int to, MarkType type) {
        var steps = new ArrayList<RemoveMarkStep>();
        tr.getDoc().nodesBetween(from, to, (node, pos, parent, index) -> {
            var mark = node.isInline() ? type.isInSet(node.getMarks()) : null;
            if (mark != null) steps.add(new RemoveMarkStep(Math.max(pos, from), Math.min(pos + node.getNodeSize(), to), mark));
            return true;
        }, 0, () -> false);
        steps.forEach(tr::step);
    }

    public static boolean isCode(Node node) { return node.getType().getSpec().getCode(); }

    public static boolean isolating(Node node) { return Boolean.TRUE.equals(node.getType().getSpec().getIsolating()); }

    public static Unit unit() { return Unit.INSTANCE; }

    private Pm() {}
}

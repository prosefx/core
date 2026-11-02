package prosefx.core.editor;

import com.atlassian.prosemirror.history.HistoryKt;
import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.*;
import java.util.*;
import java.util.regex.Pattern;

import static prosefx.core.pm.Pm.*;
import static prosefx.core.schema.EditorSchema.*;

/**
 * Text that arrives from outside (a live transcript, a log, a script) goes onto the end of the document
 * as bullets: text joins the last bullet until a sentence ends, and each new sentence starts a new bullet.
 * If the document does not end in a list, one is started (an empty last paragraph becomes it). Every
 * append is its own undo step. The user's caret is left where it is, unless it was at the very end, in
 * which case it follows the appended text.
 */
public final class Tail {

    /** Sentence ends: . ? ! and the Devanagari danda, followed by space. */
    static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!।॥])\\s+");
    static final Pattern ENDS_SENTENCE = Pattern.compile("[.?!।॥][\"')\\]]*\\s*$");

    /** Where the next commit lands: the end of the document's last textblock. */
    public static int tail(Node doc) {
        var node = doc;
        var start = 0;
        while (!node.isTextblock()) {
            var last = node.getLastChild();
            if (last == null) return start;
            start = start + node.getContent().getSize() - last.getNodeSize() + 1;
            node = last;
        }
        return start + node.getContent().getSize();
    }

    public static Transaction append(PMEditorState state, String text) {
        var pieces = pieces(text);
        if (pieces.isEmpty()) return null;
        var tr = state.getTr();
        var tailBefore = tail(state.getDoc());
        var sel = state.getSelection();
        var following = sel.getEmpty() && sel.getHead() == tailBefore;

        // An empty line right after a list (where Enter twice leaves the caret) is folded back into it.
        var doc = tr.getDoc();
        var lastBlock = doc.getLastChild();
        var count = doc.getChildCount();
        if (count >= 2 && lastBlock.isTextblock() && lastBlock.getContent().getSize() == 0 && isList(doc.child(count - 2))) {
            var end = doc.getContent().getSize();
            tr.delete(end - lastBlock.getNodeSize(), end);
        }
        var at = tr.getDoc().resolve(tail(tr.getDoc()));
        var itemDepth = itemDepth(at);
        var lineText = at.getParent().getTextContent();
        var k = 0;
        if (itemDepth < 0) {
            // No list at the end: start one, replacing an empty last paragraph.
            var items = new ArrayList<Node>();
            for (var p : pieces) items.add(item(p));
            var list = create(BULLET_LIST, frag(items.toArray(Node[]::new)));
            var last = tr.getDoc().getLastChild();
            var end = tr.getDoc().getContent().getSize();
            if (last != null && last.isTextblock() && last.getContent().getSize() == 0) tr.replaceWith(end - last.getNodeSize(), end, list);
            else tr.insert(end, list);
            k = pieces.size();
        } else if (lineText.isBlank() || !ENDS_SENTENCE.matcher(lineText).find()) {
            var sep = lineText.isEmpty() || Character.isWhitespace(lineText.charAt(lineText.length() - 1)) ? "" : " ";
            tr.insertText(sep + pieces.getFirst(), at.getPos(), null);
            k = 1;
        }
        for (; k < pieces.size(); k++) {
            var $tail = tr.getDoc().resolve(tail(tr.getDoc()));
            tr.insert($tail.after(itemDepth($tail)), item(pieces.get(k)));
        }
        if (following) tr.setSelection(cursor(tr.getDoc(), tail(tr.getDoc())));
        return HistoryKt.closeHistory(tr);
    }

    /**
     * A plain paragraph at the end of the document (a timestamp line, say); the next append then
     * starts a fresh list under it. An empty last paragraph is used rather than a new one added.
     */
    public static Transaction appendLine(PMEditorState state, String text) {
        var tr = state.getTr();
        var following = state.getSelection().getEmpty() && state.getSelection().getHead() == tail(state.getDoc());
        var para = create(PARAGRAPH, text.isEmpty() ? frag() : frag(SCHEMA.text(text, null)));
        var last = tr.getDoc().getLastChild();
        var end = tr.getDoc().getContent().getSize();
        if (last != null && last.isTextblock() && last.getContent().getSize() == 0) tr.replaceWith(end - last.getNodeSize(), end, para);
        else tr.insert(end, para);
        if (following) tr.setSelection(cursor(tr.getDoc(), tail(tr.getDoc())));
        return HistoryKt.closeHistory(tr);
    }

    /** Text split into sentences, each keeping its own end mark; spacing tidied. */
    static List<String> pieces(String text) {
        var out = new ArrayList<String>();
        if (text == null) return out;
        for (var s : SENTENCE_END.split(text.strip().replaceAll("\\s+", " "))) if (!s.isBlank()) out.add(s);
        return out;
    }

    static boolean isList(Node n) { return n.getType() == BULLET_LIST || n.getType() == ORDERED_LIST; }

    static Node item(String text) {
        return create(LIST_ITEM, frag(create(PARAGRAPH, frag(SCHEMA.text(text, null)))));
    }

    /** Depth of the innermost list item around pos, or -1. */
    static int itemDepth(com.atlassian.prosemirror.model.ResolvedPos pos) {
        for (var d = pos.getDepth(); d > 0; d--) if (pos.node(d).getType() == LIST_ITEM) return d;
        return -1;
    }

    /** True when the text on the last line ends a sentence, so the next words start a new bullet. */
    public static boolean lineEndsSentence(Node doc) {
        var at = doc.resolve(tail(doc));
        return itemDepth(at) < 0 || ENDS_SENTENCE.matcher(at.getParent().getTextContent()).find();
    }

    private Tail() {}
}

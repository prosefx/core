package prosemirrorfx.core.commands;

import com.atlassian.prosemirror.history.HistoryKt;
import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import prosemirrorfx.core.pm.Pm;
import java.util.Map;
import java.util.function.Consumer;

import static prosemirrorfx.core.commands.Basic.*;
import static prosemirrorfx.core.commands.Command.chain;
import static prosemirrorfx.core.pm.Pm.intAttr;
import static prosemirrorfx.core.schema.EditorSchema.*;

/** What each editing key and formatting action runs, composed from the ported commands. */
public final class Commands {

    public static final Command
            enter = chain(Commands::openFence, Commands::lineInFence, Lists.splitListItem(LIST_ITEM), Split.liftEmptyBlock, Split.splitBlockKeepMarks),
            backspace = chain(deleteSelection, Commands::deleteCharBackward, Commands::liftAtItemStart, Join.joinBackward),
            delete = chain(deleteSelection, Commands::deleteCharForward, Join.joinForward),
            indent = Lists.sinkListItem(LIST_ITEM),
            outdent = ListEscape.liftListItem(LIST_ITEM),
            bold = toggleMark(STRONG),
            italic = toggleMark(EM),
            bulletList = toggleList(BULLET_LIST),
            orderedList = toggleList(ORDERED_LIST),
            cycleHeading = Commands::cycleHeading,
            hardBreak = Commands::hardBreak,
            clearFormatting = Commands::clearFormatting,
            undo = Command.fromKotlin(HistoryKt.getUndo()),
            redo = Command.fromKotlin(HistoryKt.getRedo());

    /** In a browser contenteditable does this itself; here the editor must. A surrogate pair goes as one character. */
    static boolean deleteCharBackward(PMEditorState state, Consumer<Transaction> dispatch) {
        var c = Cuts.cursor(state);
        if (c == null || c.getParentOffset() == 0) return false;
        var before = c.getNodeBefore();
        var n = before != null && before.isText() && endsWithPair(before.getText()) ? 2 : 1;
        if (dispatch != null) { var tr = state.getTr(); tr.delete(c.getPos() - n, c.getPos()); dispatch.accept(tr.scrollIntoView()); }
        return true;
    }

    static boolean deleteCharForward(PMEditorState state, Consumer<Transaction> dispatch) {
        var c = Cuts.cursor(state);
        if (c == null || c.getParentOffset() == c.getParent().getContent().getSize()) return false;
        var after = c.getNodeAfter();
        var n = after != null && after.isText() && startsWithPair(after.getText()) ? 2 : 1;
        if (dispatch != null) { var tr = state.getTr(); tr.delete(c.getPos(), c.getPos() + n); dispatch.accept(tr.scrollIntoView()); }
        return true;
    }

    static boolean endsWithPair(String s) {
        return s != null && s.length() >= 2 && Character.isLowSurrogate(s.charAt(s.length() - 1)) && Character.isHighSurrogate(s.charAt(s.length() - 2));
    }

    static boolean startsWithPair(String s) {
        return s != null && s.length() >= 2 && Character.isHighSurrogate(s.charAt(0)) && Character.isLowSurrogate(s.charAt(1));
    }

    static boolean liftAtItemStart(PMEditorState state, Consumer<Transaction> dispatch) {
        var c = Cuts.atBlockStart(state);
        if (c == null || c.getDepth() < 2 || c.node(-1).getType() != LIST_ITEM || c.index(-1) != 0) return false;
        var outer = 1;
        while (!isList(c.node(outer))) outer++;
        var prev = Selection.Companion.findFrom(state.getDoc().resolve(c.before(null)), -1, true);
        if (prev == null || prev.getHead() < c.start(outer)) return outdent.apply(state, dispatch);
        // Confluence: a line that is not the list's first joins the line above it, children and all.
        if (dispatch != null) {
            var tr = state.getTr();
            tr.delete(prev.getHead(), c.getPos());
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    static boolean isList(Node n) { return n.getType() == BULLET_LIST || n.getType() == ORDERED_LIST; }

    static Command toggleList(NodeType listType) {
        return (state, dispatch) -> {
            var from = state.getSelection().get_from();
            for (var d = from.getDepth(); d > 0; d--) {
                var type = from.node(d).getType();
                if (type == listType) return outdent.apply(state, dispatch);
                if (type == BULLET_LIST || type == ORDERED_LIST) {
                    if (dispatch != null) {
                        var tr = state.getTr();
                        tr.setNodeMarkup(from.before(d), listType, null, null);
                        dispatch.accept(tr.scrollIntoView());
                    }
                    return true;
                }
            }
            return Lists.wrapInList(listType, null).apply(state, dispatch);
        };
    }

    static final java.util.regex.Pattern FENCE_OPEN = java.util.regex.Pattern.compile("^(`{3,}|~{3,})[^`]*$"),
            FENCE_CLOSE = java.util.regex.Pattern.compile("^(`{3,}|~{3,})[ \\t]*$");

    static boolean verbatim(Node n) { return n.getType() == PARAGRAPH && Boolean.TRUE.equals(Pm.attr(n, "verbatim")); }

    static Node lineBreak() { return HARD_BREAK.create(Map.of("raw", ""), (Fragment) null, null); }

    /**
     * Enter at the end of a normal paragraph that is only ```lang (or ~~~): it becomes a fenced block, the way the reader
     * keeps one (a verbatim paragraph: fence line, break, code lines, break, closing fence), with the caret on the empty
     * line inside. A block that would end its container gets an empty paragraph after it, so an arrow key can leave it.
     */
    static boolean openFence(PMEditorState state, Consumer<Transaction> dispatch) {
        var c = Cuts.cursor(state);
        if (c == null) return false;
        var p = c.getParent();
        // only at the top level: inside a list item the code lines would need the item's indent, which the writer does not add to a verbatim block
        if (c.getDepth() != 1 || p.getType() != PARAGRAPH || verbatim(p) || c.getParentOffset() != p.getContent().getSize() || p.getChildCount() != 1 || !p.getFirstChild().isText()) return false;
        var line = p.getTextContent();
        var m = FENCE_OPEN.matcher(line);
        if (!m.matches()) return false;
        if (dispatch != null) {
            var fence = m.group(1);
            var attrs = new java.util.HashMap<String, Object>(p.getAttrs());
            attrs.put("verbatim", true);
            attrs.put("src", null);
            attrs.put("lead", "");
            var block = PARAGRAPH.create(attrs, Fragment.Companion.fromArray(java.util.List.of(SCHEMA.text(line, null), lineBreak(), lineBreak(), SCHEMA.text(fence, null))), null);
            var start = c.before(null);
            var tr = state.getTr();
            tr.replaceWith(start, c.after(null), block);
            if (c.index(-1) == c.node(-1).getChildCount() - 1) tr.insert(start + block.getNodeSize(), PARAGRAPH.create(null, (Fragment) null, null));
            tr.setSelection(Pm.cursor(tr.getDoc(), start + 1 + line.length() + 1));
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    /** Enter inside a fenced block adds a line; only at the end of the closing fence does it leave the block (falls through to a split). */
    static boolean lineInFence(PMEditorState state, Consumer<Transaction> dispatch) {
        var sel = state.getSelection();
        var from = sel.get_from();
        var p = from.getParent();
        if (!verbatim(p) || from.getParent() != sel.get_to().getParent()) return false;
        if (sel.getEmpty() && from.getParentOffset() == p.getContent().getSize()) {
            var text = p.getTextContent();
            var last = p.getLastChild();
            var lastLine = last != null && last.isText() ? last.getText() : "";
            var hasBreak = false;
            for (var k = 0; k < p.getChildCount(); k++) hasBreak |= p.child(k).getType() == HARD_BREAK;
            if (hasBreak && FENCE_CLOSE.matcher(lastLine.strip()).matches() && !text.isEmpty()) return false;
        }
        if (dispatch != null) {
            var tr = state.getTr();
            tr.replaceSelectionWith(lineBreak(), false);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    /** Shift+Enter: a line break inside the paragraph. */
    static boolean hardBreak(PMEditorState state, Consumer<Transaction> dispatch) {
        if (!state.getSelection().get_from().getParent().isTextblock()) return false;
        if (dispatch != null) {
            var tr = state.getTr();
            tr.replaceSelectionWith(HARD_BREAK.create(null, (Fragment) null, null), true);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    /** Ctrl+\: bold and italic off over the selection, or for what is typed next at a caret. */
    static boolean clearFormatting(PMEditorState state, Consumer<Transaction> dispatch) {
        var sel = state.getSelection();
        if (dispatch != null) {
            var tr = state.getTr();
            // Pm.removeStoredMark / Pm.removeMark: the prosemirror-kotlin ones are broken (UpstreamBugsTest)
            if (sel.getEmpty()) { Pm.removeStoredMark(tr, STRONG); Pm.removeStoredMark(tr, EM); }
            else for (var type : new MarkType[] {STRONG, EM}) Pm.removeMark(tr, sel.getFrom(), sel.getTo(), type);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    /** Pressed state of a mark button: at a caret the marks typing would get, over a selection all of it marked. */
    public static boolean markActive(PMEditorState state, MarkType type) {
        var sel = state.getSelection();
        if (sel.getEmpty()) {
            var marks = state.getStoredMarks() != null ? state.getStoredMarks() : sel.get_from().marks();
            return type.isInSet(marks) != null;
        }
        return Marks.fullyMarked(state.getDoc(), sel.getFrom(), sel.getTo(), type);
    }

    /** Pressed state of a list button: the innermost list around the selection start is of this type. */
    public static boolean inList(PMEditorState state, NodeType listType) {
        var from = state.getSelection().get_from();
        for (var d = from.getDepth(); d > 0; d--) {
            var type = from.node(d).getType();
            if (type == BULLET_LIST || type == ORDERED_LIST) return type == listType;
        }
        return false;
    }

    /** 0 is normal text, 1..6 a heading of that level. */
    public static Command setBlock(int level) {
        return level == 0 ? setBlockType(PARAGRAPH, null) : setBlockType(HEADING, Map.of("level", level));
    }

    /** The style of the block the selection starts in: 0 normal text, 1..6 heading level. */
    public static int blockLevel(PMEditorState state) {
        var block = state.getSelection().get_from().getParent();
        return block.getType() == HEADING ? intAttr(block, "level") : 0;
    }

    static boolean cycleHeading(PMEditorState state, Consumer<Transaction> dispatch) {
        var block = state.getSelection().get_from().getParent();
        var level = block.getType() == HEADING ? intAttr(block, "level") : 0;
        return level >= 3
                ? setBlockType(PARAGRAPH, null).apply(state, dispatch)
                : setBlockType(HEADING, Map.of("level", level + 1)).apply(state, dispatch);
    }

    private Commands() {}
}

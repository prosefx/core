package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.function.Consumer;

import static prosefx.core.pm.Pm.*;

/** Enter in an empty nested item (move it out one level), and liftListItem from prosemirror-schema-list. */
public final class ListEscape {

    static boolean splitEmpty(PMEditorState state, Consumer<Transaction> dispatch, ResolvedPos from, NodeType itemType) {
        if (from.getDepth() == 3 || from.node(-3).getType() != itemType || from.index(-2) != from.node(-2).getChildCount() - 1) return false;
        if (dispatch == null) return true;
        var wrap = EMPTY;
        var depthBefore = from.index(-1) > 0 ? 1 : from.index(-2) > 0 ? 2 : 3;
        for (var d = from.getDepth() - depthBefore; d >= from.getDepth() - 3; d--) wrap = frag(copy(from.node(d), wrap));
        var depthAfter = from.indexAfter(-1) < from.node(-2).getChildCount() ? 1
                : from.indexAfter(-2) < from.node(-3).getChildCount() ? 2 : 3;
        wrap = wrap.append(frag(createAndFill(itemType)));
        var start = from.before(from.getDepth() - (depthBefore - 1));
        var tr = state.getTr();
        tr.replace(start, from.after(-depthAfter), slice(wrap, 4 - depthBefore, 0));
        var sel = new int[]{-1};
        tr.getDoc().nodesBetween(start, tr.getDoc().getContent().getSize(), (node, pos, parent, index) -> {
            if (sel[0] > -1) return false;
            if (node.isTextblock() && node.getContent().getSize() == 0) sel[0] = pos + 1;
            return true;
        }, 0, () -> false);
        if (sel[0] > -1) tr.setSelection(near(tr.getDoc().resolve(sel[0]), 1));
        dispatch.accept(tr.scrollIntoView());
        return true;
    }

    public static Command liftListItem(NodeType itemType) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var range = blockRange(sel.get_from(), sel.get_to(), n -> n.getChildCount() > 0 && n.getFirstChild().getType() == itemType);
            if (range == null) return false;
            if (dispatch == null) return true;
            return sel.get_from().node(range.getDepth() - 1).getType() == itemType
                    ? toOuterList(state, dispatch, itemType, range)
                    : outOfList(state, dispatch, range);
        };
    }

    static boolean toOuterList(PMEditorState state, Consumer<Transaction> dispatch, NodeType itemType, NodeRange range) {
        var tr = state.getTr();
        var end = range.getEnd();
        var endOfList = range.getTo().end(range.getDepth());
        if (end < endOfList) {
            var s = slice(frag(create(itemType, frag(copy(range.getParent(), EMPTY)))), 1, 0);
            tr.step(new ReplaceAroundStep(end - 1, endOfList, end, endOfList, s, 1, true));
            range = new NodeRange(tr.getDoc().resolve(range.getFrom().getPos()), tr.getDoc().resolve(endOfList), range.getDepth());
        }
        var target = StructureKt.liftTarget(range);
        if (target == null) return false;
        tr.lift(range, target);
        var after = tr.getDoc().resolve(tr.getMapping().map(end, -1) - 1);
        if (StructureKt.canJoin(tr.getDoc(), after.getPos()) && after.getNodeBefore().getType() == after.getNodeAfter().getType())
            tr.join(after.getPos(), 1);
        dispatch.accept(tr.scrollIntoView());
        return true;
    }

    static boolean outOfList(PMEditorState state, Consumer<Transaction> dispatch, NodeRange range) {
        var tr = state.getTr();
        var list = range.getParent();
        for (int pos = range.getEnd(), i = range.getEndIndex() - 1, e = range.getStartIndex(); i > e; i--) {
            pos -= list.child(i).getNodeSize();
            tr.delete(pos - 1, pos + 1);
        }
        var start = tr.getDoc().resolve(range.getStart());
        var item = start.getNodeAfter();
        if (tr.getMapping().map(range.getEnd(), 1) != range.getStart() + item.getNodeSize()) return false;
        var atStart = range.getStartIndex() == 0;
        var atEnd = range.getEndIndex() == list.getChildCount();
        var parent = start.node(-1);
        var indexBefore = start.index(-1);
        var replacement = item.getContent().append(atEnd ? EMPTY : frag(list));
        if (!parent.canReplace(indexBefore + (atStart ? 0 : 1), indexBefore + 1, replacement, 0, replacement.getChildCount())) return false;
        var s = start.getPos();
        var e = s + item.getNodeSize();
        var shell = (atStart ? EMPTY : frag(copy(list, EMPTY))).append(atEnd ? EMPTY : frag(copy(list, EMPTY)));
        tr.step(new ReplaceAroundStep(s - (atStart ? 1 : 0), e + (atEnd ? 1 : 0), s + 1, e - 1,
                slice(shell, atStart ? 0 : 1, atEnd ? 0 : 1), atStart ? 0 : 1, false));
        dispatch.accept(tr.scrollIntoView());
        return true;
    }

    private ListEscape() {}
}

package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.*;

import static prosefx.core.pm.Pm.*;

/** wrapInList, splitListItem and sinkListItem from prosemirror-schema-list. */
public final class Lists {

    public static Command wrapInList(NodeType listType, Map<String, ?> attrs) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var range = blockRange(sel.get_from(), sel.get_to());
            if (range == null) return false;
            var tr = dispatch == null ? null : state.getTr();
            if (!wrapRange(tr, range, listType, attrs)) return false;
            if (dispatch != null) dispatch.accept(tr.scrollIntoView());
            return true;
        };
    }

    static boolean wrapRange(Transaction tr, NodeRange range, NodeType listType, Map<String, ?> attrs) {
        var doJoin = false;
        var outer = range;
        var doc = range.getFrom().getDoc();
        if (range.getDepth() >= 2 && range.getFrom().node(range.getDepth() - 1).getType().compatibleContent(listType)
                && range.getStartIndex() == 0) {
            if (range.getFrom().index(range.getDepth() - 1) == 0) return false;
            var insert = doc.resolve(range.getStart() - 2);
            outer = new NodeRange(insert, insert, range.getDepth());
            if (range.getEndIndex() < range.getParent().getChildCount())
                range = new NodeRange(range.getFrom(), doc.resolve(range.getTo().end(range.getDepth())), range.getDepth());
            doJoin = true;
        }
        var wrap = StructureKt.findWrapping(outer, listType, attrs, range);
        if (wrap == null) return false;
        if (tr != null) doWrap(tr, range, wrap, doJoin, listType);
        return true;
    }

    static void doWrap(Transaction tr, NodeRange range, List<NodeBase> wrappers, boolean joinBefore, NodeType listType) {
        var content = EMPTY;
        for (var i = wrappers.size() - 1; i >= 0; i--) content = frag(create(wrappers.get(i).getType(), wrappers.get(i).getAttrs(), content));
        var back = joinBefore ? 2 : 0;
        tr.step(new ReplaceAroundStep(range.getStart() - back, range.getEnd(), range.getStart(), range.getEnd(), slice(content, 0, 0), wrappers.size(), true));
        var found = 0;
        for (var i = 0; i < wrappers.size(); i++) if (wrappers.get(i).getType() == listType) found = i + 1;
        var splitDepth = wrappers.size() - found;
        var splitPos = range.getStart() + wrappers.size() - back;
        var parent = range.getParent();
        for (int i = range.getStartIndex(), e = range.getEndIndex(); i < e; i++) {
            if (i > range.getStartIndex() && StructureKt.canSplit(tr.getDoc(), splitPos, splitDepth, null)) {
                tr.split(splitPos, splitDepth, null);
                splitPos += 2 * splitDepth;
            }
            splitPos += parent.child(i).getNodeSize();
        }
    }

    public static Command splitListItem(NodeType itemType) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var from = sel.get_from();
            var to = sel.get_to();
            if ((sel instanceof NodeSelection ns && ns.getNode().isBlock()) || from.getDepth() < 2 || !from.sameParent(to)) return false;
            var grandParent = from.node(-1);
            if (grandParent.getType() != itemType) return false;
            if (from.getParent().getContent().getSize() == 0 && from.node(-1).getChildCount() == from.indexAfter(-1))
                return ListEscape.splitEmpty(state, dispatch, from, itemType);
            var nextType = to.getPos() == from.end(null) ? grandParent.contentMatchAt(0).getDefaultType() : null;
            var tr = state.getTr();
            tr.delete(from.getPos(), to.getPos());
            var types = nextType == null ? null : Arrays.asList((NodeBase) null, new NodeBase(nextType, null));
            if (!StructureKt.canSplit(tr.getDoc(), from.getPos(), 2, types)) return false;
            if (dispatch != null) {
                tr.split(from.getPos(), 2, types);
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        };
    }

    public static Command sinkListItem(NodeType itemType) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var range = blockRange(sel.get_from(), sel.get_to(), n -> n.getChildCount() > 0 && n.getFirstChild().getType() == itemType);
            if (range == null || range.getStartIndex() == 0) return false;
            var parent = range.getParent();
            var nodeBefore = parent.child(range.getStartIndex() - 1);
            if (nodeBefore.getType() != itemType) return false;
            if (dispatch != null) {
                var nestedBefore = nodeBefore.getLastChild() != null && nodeBefore.getLastChild().getType() == parent.getType();
                var inner = nestedBefore ? frag(create(itemType, EMPTY)) : EMPTY;
                var s = slice(frag(create(itemType, frag(create(parent.getType(), inner)))), nestedBefore ? 3 : 1, 0);
                int before = range.getStart(), after = range.getEnd();
                var tr = state.getTr();
                tr.step(new ReplaceAroundStep(before - (nestedBefore ? 3 : 1), after, before, after, s, 1, true));
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        };
    }

    private Lists() {}
}

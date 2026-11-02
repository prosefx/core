package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.*;

import static prosefx.core.commands.Cuts.*;
import static prosefx.core.pm.Pm.*;

/** splitBlock, splitBlockKeepMarks and liftEmptyBlock from prosemirror-commands: what Enter does. */
public final class Split {

    public static final Command splitBlock = (state, dispatch) -> {
        var from = state.getSelection().get_from();
        if (state.getSelection() instanceof NodeSelection ns && ns.getNode().isBlock()) {
            if (from.getParentOffset() == 0 || !StructureKt.canSplit(state.getDoc(), from.getPos(), 1, null)) return false;
            if (dispatch != null) {
                var tr = state.getTr();
                tr.split(from.getPos(), 1, null);
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        }
        if (from.getDepth() == 0) return false;
        var types = new ArrayList<NodeBase>();
        NodeType deflt = null;
        int splitDepth;
        boolean atEnd, atStart;
        for (var d = from.getDepth(); ; d--) {
            var node = from.node(d);
            if (node.isBlock()) {
                atEnd = from.end(d) == from.getPos() + (from.getDepth() - d);
                atStart = from.start(d) == from.getPos() - (from.getDepth() - d);
                deflt = defaultBlockAt(from.node(d - 1).contentMatchAt(from.indexAfter(d - 1)));
                types.addFirst(atEnd && deflt != null ? new NodeBase(deflt, null) : null);
                splitDepth = d;
                break;
            }
            if (d == 1) return false;
            types.addFirst(null);
        }
        var tr = state.getTr();
        if (state.getSelection() instanceof TextSelection) tr.deleteSelection();
        var splitPos = tr.getMapping().map(from.getPos(), 1);
        var can = StructureKt.canSplit(tr.getDoc(), splitPos, types.size(), types);
        if (!can) {
            types.set(0, deflt == null ? null : new NodeBase(deflt, null));
            can = StructureKt.canSplit(tr.getDoc(), splitPos, types.size(), types);
        }
        if (!can) return false;
        tr.split(splitPos, types.size(), types);
        if (!atEnd && atStart && from.node(splitDepth).getType() != deflt && deflt != null) {
            var first = tr.getDoc().resolve(tr.getMapping().map(from.before(splitDepth), 1));
            if (from.node(splitDepth - 1).canReplaceWith(first.index(null), first.index(null) + 1, deflt, null))
                tr.setNodeMarkup(tr.getMapping().map(from.before(splitDepth), 1), deflt, null, null);
        }
        if (dispatch != null) dispatch.accept(tr.scrollIntoView());
        return true;
    };

    public static final Command splitBlockKeepMarks = (state, dispatch) -> splitBlock.apply(state, dispatch == null ? null : tr -> {
        var sel = state.getSelection();
        var marks = state.getStoredMarks() != null ? state.getStoredMarks()
                : sel.get_to().getParentOffset() > 0 ? sel.get_from().marks() : null;
        if (marks != null) tr.ensureMarks(marks);
        dispatch.accept(tr);
    });

    public static final Command liftEmptyBlock = (state, dispatch) -> {
        var cursor = cursor(state);
        if (cursor == null || cursor.getParent().getContent().getSize() > 0) return false;
        if (cursor.getDepth() > 1 && cursor.after(null) != cursor.end(-1)) {
            var before = cursor.before(null);
            if (StructureKt.canSplit(state.getDoc(), before, 1, null)) {
                if (dispatch != null) {
                    var tr = state.getTr();
                    tr.split(before, 1, null);
                    dispatch.accept(tr.scrollIntoView());
                }
                return true;
            }
        }
        return Join.liftBlock(state, dispatch, cursor);
    };

    private Split() {}
}

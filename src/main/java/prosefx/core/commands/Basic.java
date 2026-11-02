package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.*;

import static prosefx.core.pm.Pm.*;

/** deleteSelection, selectAll, lift, wrapIn, setBlockType and toggleMark from prosemirror-commands. */
public final class Basic {

    public static final Command deleteSelection = (state, dispatch) -> {
        if (state.getSelection().getEmpty()) return false;
        if (dispatch != null) dispatch.accept(state.getTr().deleteSelection().scrollIntoView());
        return true;
    };

    public static final Command selectAll = (state, dispatch) -> {
        var doc = state.getDoc();
        if (dispatch != null) dispatch.accept(state.getTr().setSelection(between(doc.resolve(0), doc.resolve(doc.getContent().getSize()))));
        return true;
    };

    public static final Command lift = (state, dispatch) -> {
        var sel = state.getSelection();
        var range = blockRange(sel.get_from(), sel.get_to());
        var target = range == null ? null : StructureKt.liftTarget(range);
        if (target == null) return false;
        if (dispatch != null) {
            var tr = state.getTr();
            tr.lift(range, target);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    };

    public static Command wrapIn(NodeType type, Map<String, ?> attrs) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var range = blockRange(sel.get_from(), sel.get_to());
            var wrapping = range == null ? null : StructureKt.findWrapping(range, type, attrs, range);
            if (wrapping == null) return false;
            if (dispatch != null) {
                var tr = state.getTr();
                tr.wrap(range, wrapping);
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        };
    }

    public static Command setBlockType(NodeType type, Map<String, ?> attrs) {
        return (state, dispatch) -> {
            var applicable = new boolean[1];
            for (var r : state.getSelection().getRanges())
                state.getDoc().nodesBetween(r.getFrom().getPos(), r.getTo().getPos(), (node, pos, parent, index) -> {
                    if (applicable[0]) return false;
                    if (!node.isTextblock() || node.hasMarkup(type, attrs, null)) return true;
                    if (node.getType() == type) applicable[0] = true;
                    else {
                        var p = state.getDoc().resolve(pos);
                        applicable[0] = p.getParent().canReplaceWith(p.index(null), p.index(null) + 1, type, null);
                    }
                    return true;
                }, 0, () -> false);
            if (!applicable[0]) return false;
            if (dispatch != null) {
                var tr = state.getTr();
                for (var r : state.getSelection().getRanges())
                    tr.setBlockType(r.getFrom().getPos(), r.getTo().getPos(), type, attrs);
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        };
    }

    public static Command toggleMark(MarkType type) {
        return (state, dispatch) -> {
            var sel = state.getSelection();
            var cursor = Cuts.cursor(state);
            if ((sel.getEmpty() && cursor == null) || !Marks.applies(state.getDoc(), sel.getRanges(), type)) return false;
            if (dispatch == null) return true;
            if (cursor != null) {
                var current = state.getStoredMarks() != null ? state.getStoredMarks() : cursor.marks();
                var tr = state.getTr();
                if (type.isInSet(current) != null) removeStoredMark(tr, type);
                else tr.addStoredMark(type.create(null));
                dispatch.accept(tr);
            } else dispatch.accept(Marks.toggleOverRanges(state, type).scrollIntoView());
            return true;
        };
    }

    private Basic() {}
}

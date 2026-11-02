package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.function.Consumer;

import static prosefx.core.commands.Cuts.*;
import static prosefx.core.pm.Pm.*;

/** joinBackward / joinForward from prosemirror-commands: what Backspace and Delete do at a block edge. */
public final class Join {

    public static final Command joinBackward = (state, dispatch) -> {
        var cursor = atBlockStart(state);
        if (cursor == null) return false;
        var cut = findCutBefore(cursor);
        if (cut == null) return liftBlock(state, dispatch, cursor);
        var before = cut.getNodeBefore();
        if (Barrier.delete(state, cut, dispatch, -1)) return true;
        if (cursor.getParent().getContent().getSize() == 0 && (textblockAt(before, false, false) || selectable(before)))
            for (var depth = cursor.getDepth(); ; depth--) {
                var step = replaceStep(state.getDoc(), cursor.before(depth), cursor.after(depth));
                if (shrinks(step)) {
                    if (dispatch != null) {
                        var tr = state.getTr();
                        tr.step(step);
                        tr.setSelection(textblockAt(before, false, false)
                                ? findFrom(tr.getDoc().resolve(tr.getMapping().map(cut.getPos(), -1)), -1)
                                : NodeSelection.Companion.create(tr.getDoc(), cut.getPos() - before.getNodeSize()));
                        dispatch.accept(tr.scrollIntoView());
                    }
                    return true;
                }
                if (depth == 1 || cursor.node(depth - 1).getChildCount() > 1) break;
            }
        if (before.isAtom() && cut.getDepth() == cursor.getDepth() - 1) {
            if (dispatch != null) dispatchDelete(state, dispatch, cut.getPos() - before.getNodeSize(), cut.getPos());
            return true;
        }
        return false;
    };

    public static final Command joinForward = (state, dispatch) -> {
        var cursor = atBlockEnd(state);
        if (cursor == null) return false;
        var cut = findCutAfter(cursor);
        if (cut == null) return false;
        var after = cut.getNodeAfter();
        if (Barrier.delete(state, cut, dispatch, 1)) return true;
        if (cursor.getParent().getContent().getSize() == 0 && (textblockAt(after, true, false) || selectable(after))) {
            var step = replaceStep(state.getDoc(), cursor.before(null), cursor.after(null));
            if (shrinks(step)) {
                if (dispatch != null) {
                    var tr = state.getTr();
                    tr.step(step);
                    tr.setSelection(textblockAt(after, true, false)
                            ? findFrom(tr.getDoc().resolve(tr.getMapping().map(cut.getPos(), 1)), 1)
                            : NodeSelection.Companion.create(tr.getDoc(), tr.getMapping().map(cut.getPos(), 1)));
                    dispatch.accept(tr.scrollIntoView());
                }
                return true;
            }
        }
        if (after.isAtom() && cut.getDepth() == cursor.getDepth() - 1) {
            if (dispatch != null) dispatchDelete(state, dispatch, cut.getPos(), cut.getPos() + after.getNodeSize());
            return true;
        }
        return false;
    };

    static boolean liftBlock(PMEditorState state, Consumer<Transaction> dispatch, ResolvedPos cursor) {
        var range = blockRange(cursor, cursor);
        var target = range == null ? null : StructureKt.liftTarget(range);
        if (target == null) return false;
        if (dispatch != null) {
            var tr = state.getTr();
            tr.lift(range, target);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    static boolean shrinks(Step step) {
        return step instanceof ReplaceStep r && r.getSlice().getSize() < r.getTo() - r.getFrom();
    }

    static void dispatchDelete(PMEditorState state, Consumer<Transaction> dispatch, int from, int to) {
        var tr = state.getTr();
        tr.delete(from, to);
        dispatch.accept(tr.scrollIntoView());
    }

    private Join() {}
}

package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.*;
import java.util.*;
import java.util.function.Consumer;

import static prosefx.core.commands.Cuts.*;
import static prosefx.core.pm.Pm.*;

/** deleteBarrier and joinMaybeClear from prosemirror-commands: deleting the boundary between two blocks. */
final class Barrier {

    static boolean delete(PMEditorState state, ResolvedPos cut, Consumer<Transaction> dispatch, int dir) {
        var before = cut.getNodeBefore();
        var after = cut.getNodeAfter();
        var isolated = isolating(before) || isolating(after);
        if (!isolated && joinMaybeClear(state, cut, dispatch)) return true;
        var canDelAfter = !isolated && cut.getParent().canReplace(cut.index(null), cut.index(null) + 1, EMPTY, 0, EMPTY.getChildCount());
        if (canDelAfter && wrapInto(state, cut, before, after, dispatch)) return true;
        var selAfter = isolating(after) || (dir > 0 && isolated) ? null : findFrom(cut, 1);
        var range = selAfter == null ? null : blockRange(selAfter.get_from(), selAfter.get_to());
        var target = range == null ? null : StructureKt.liftTarget(range);
        if (target != null && target >= cut.getDepth()) {
            if (dispatch != null) {
                var tr = state.getTr();
                tr.lift(range, target);
                dispatch.accept(tr.scrollIntoView());
            }
            return true;
        }
        return canDelAfter && textblockAt(after, true, true) && textblockAt(before, false, false)
                && mergeTextblocks(state, cut, before, after, dispatch);
    }

    static boolean wrapInto(PMEditorState state, ResolvedPos cut, Node before, Node after, Consumer<Transaction> dispatch) {
        var match = before.contentMatchAt(before.getChildCount());
        var conn = match.findWrapping(after.getType());
        if (conn == null) return false;
        var m = match.matchType(conn.isEmpty() ? after.getType() : conn.get(0));
        if (m == null || !m.getValidEnd()) return false;
        if (dispatch != null) {
            var end = cut.getPos() + after.getNodeSize();
            var wrap = EMPTY;
            for (var i = conn.size() - 1; i >= 0; i--) wrap = frag(create(conn.get(i), wrap));
            wrap = frag(copy(before, wrap));
            var tr = state.getTr();
            tr.step(new ReplaceAroundStep(cut.getPos() - 1, end, cut.getPos(), end, slice(wrap, 1, 0), conn.size(), true));
            var joinAt = tr.getDoc().resolve(end + 2 * conn.size());
            if (joinAt.getNodeAfter() != null && joinAt.getNodeAfter().getType() == before.getType()
                    && StructureKt.canJoin(tr.getDoc(), joinAt.getPos())) tr.join(joinAt.getPos(), 1);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    static boolean mergeTextblocks(PMEditorState state, ResolvedPos cut, Node before, Node after, Consumer<Transaction> dispatch) {
        var at = before;
        var wrap = new ArrayList<Node>();
        for (;;) {
            wrap.add(at);
            if (at.isTextblock()) break;
            at = at.getLastChild();
        }
        var afterText = after;
        var afterDepth = 1;
        for (; !afterText.isTextblock(); afterText = afterText.getFirstChild()) afterDepth++;
        var content = afterText.getContent();
        if (!at.canReplace(at.getChildCount(), at.getChildCount(), content, 0, content.getChildCount())) return false;
        if (dispatch != null) {
            var end = EMPTY;
            for (var i = wrap.size() - 1; i >= 0; i--) end = frag(copy(wrap.get(i), end));
            var tr = state.getTr();
            tr.step(new ReplaceAroundStep(cut.getPos() - wrap.size(), cut.getPos() + after.getNodeSize(),
                    cut.getPos() + afterDepth, cut.getPos() + after.getNodeSize() - afterDepth,
                    slice(end, wrap.size(), 0), 0, true));
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    static boolean joinMaybeClear(PMEditorState state, ResolvedPos pos, Consumer<Transaction> dispatch) {
        var before = pos.getNodeBefore();
        var after = pos.getNodeAfter();
        var index = pos.index(null);
        if (before == null || after == null || !before.getType().compatibleContent(after.getType())) return false;
        if (before.getContent().getSize() == 0 && pos.getParent().canReplace(index - 1, index, EMPTY, 0, 0)) {
            if (dispatch != null) Join.dispatchDelete(state, dispatch, pos.getPos() - before.getNodeSize(), pos.getPos());
            return true;
        }
        if (!pos.getParent().canReplace(index, index + 1, EMPTY, 0, 0)
                || !(after.isTextblock() || StructureKt.canJoin(state.getDoc(), pos.getPos()))) return false;
        if (dispatch != null) {
            var tr = state.getTr();
            tr.join(pos.getPos(), 1);
            dispatch.accept(tr.scrollIntoView());
        }
        return true;
    }

    private Barrier() {}
}

package prosefx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.pm.Pm.*;
import static prosefx.core.schema.Build.*;
import static prosefx.core.schema.EditorSchema.*;

/**
 * Pins the three prosemirror-kotlin 1.1.21 bugs that Pm.removeStoredMark, Pm.removeMark and the closeHistory in
 * EditorModel.dispatch work around. When one of these starts failing, upstream has fixed it: delete the
 * workaround and this test.
 */
class UpstreamBugsTest {

    @Test void markTypeRemoveFromSetKeepsInsteadOfDropping() {
        var marks = java.util.List.of(STRONG.create(null), EM.create(null));
        assertEquals(1, STRONG.removeFromSet(marks).size());
        assertSame(STRONG, STRONG.removeFromSet(marks).get(0).getType());
    }

    @Test void transformRemoveMarkDoesNotReachTextInsideBlocks() {
        var m = model(p(b("[abc]")));
        var tr = m.state().getTr();
        tr.removeMark(1, 4, STRONG);
        assertEquals(0, tr.getSteps().size());
    }

    /** History.kt:397 does prevRanges!!, where prosemirror's JS checks for null; the ranges go null when a change outside history lands on a collapsed range, twice (as composing text is put in and taken out). */
    @Test void historyDiesOnNullRangesAfterAChangeOutsideHistory() {
        var state = model(p("a [old] b")).state();
        state = state.apply(state.getTr().deleteSelection());
        var outside = state.getTr().insertText("x", null, null);
        outside.setMeta("addToHistory", false);
        state = state.apply(outside);
        var outsideAgain = state.getTr();
        outsideAgain.delete(3, 4);
        outsideAgain.setMeta("addToHistory", false);
        state = state.apply(outsideAgain);
        var undoable = state.getTr().insertText("y", null, null);
        var before = state;
        assertThrows(NullPointerException.class, () -> before.apply(undoable));
    }
}

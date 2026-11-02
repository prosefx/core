package prosefx.core.editor;

import com.atlassian.prosemirror.history.HistoryKt;
import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.*;
import java.util.*;
import java.util.function.BiConsumer;
import prosefx.core.commands.Command;
import prosefx.core.pm.Pm;
import prosefx.core.rules.InputRules;

import static prosefx.core.pm.Pm.*;

/** The editor without a view: holds the state, applies transactions, tells listeners (old, new). */
public final class EditorModel {

    PMEditorState state;
    Transaction last;
    final List<BiConsumer<PMEditorState, PMEditorState>> listeners = new ArrayList<>();

    public EditorModel(Node doc) { state = Pm.state(doc, atEnd(doc)); }

    public PMEditorState state() { return state; }

    /** The transaction behind the change listeners are being told about, to map positions through; null after load. */
    public Transaction lastTransaction() { return last; }

    public void onChange(BiConsumer<PMEditorState, PMEditorState> l) { listeners.add(l); }

    /** The last change was kept out of undo history (an input method's composing text). */
    boolean outsideHistory;

    public void dispatch(Transaction tr) {
        if (tr.getDocChanged()) {
            var kept = !Boolean.FALSE.equals(tr.getMeta("addToHistory"));
            // prosemirror-kotlin 1.1.21: after a change outside history the plugin's prevRanges can be null, and the
            // next change into history within its grouping delay then dies on prevRanges!! (History.kt:397). Closing
            // history first resets them, and makes the change its own undo step, which is what we want anyway.
            if (kept && outsideHistory) HistoryKt.closeHistory(tr);
            outsideHistory = !kept;
        }
        var old = state;
        state = state.apply(tr);
        last = tr;
        listeners.forEach(l -> l.accept(old, state));
    }

    public boolean run(Command c) { return c.apply(state, this::dispatch); }

    /** Typed text replaces the selection, unless an input rule turns it into formatting instead. */
    public void type(String text) {
        var sel = state.getSelection();
        var ruled = InputRules.match(state, sel.getFrom(), sel.getTo(), text);
        dispatch(ruled != null ? ruled : state.getTr().insertText(text, null, null));
    }

    public void select(Selection s) { dispatch(state.getTr().setSelection(s)); }

    /** Replace the whole document, as when a file is opened: a fresh state, so undo does not reach back into the old one. */
    public void load(Node doc) {
        var old = state;
        state = Pm.state(doc, atStart(doc));
        last = null;
        listeners.forEach(l -> l.accept(old, state));
    }
}

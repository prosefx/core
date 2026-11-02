package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;

import static prosefx.core.pm.Pm.*;

/** Position helpers shared by the ported prosemirror-commands. */
final class Cuts {

    static ResolvedPos cursor(PMEditorState state) {
        return state.getSelection() instanceof TextSelection t ? t.get_cursor() : null;
    }

    static ResolvedPos atBlockStart(PMEditorState state) {
        var c = cursor(state);
        return c == null || c.getParentOffset() > 0 ? null : c;
    }

    static ResolvedPos atBlockEnd(PMEditorState state) {
        var c = cursor(state);
        return c == null || c.getParentOffset() < c.getParent().getContent().getSize() ? null : c;
    }

    static ResolvedPos findCutBefore(ResolvedPos pos) {
        if (isolating(pos.getParent())) return null;
        for (var i = pos.getDepth() - 1; i >= 0; i--) {
            if (pos.index(i) > 0) return pos.getDoc().resolve(pos.before(i + 1));
            if (isolating(pos.node(i))) break;
        }
        return null;
    }

    static ResolvedPos findCutAfter(ResolvedPos pos) {
        if (isolating(pos.getParent())) return null;
        for (var i = pos.getDepth() - 1; i >= 0; i--) {
            var parent = pos.node(i);
            if (pos.indexAfter(i) < parent.getChildCount()) return pos.getDoc().resolve(pos.after(i + 1));
            if (isolating(parent)) break;
        }
        return null;
    }

    static boolean textblockAt(Node node, boolean start, boolean only) {
        for (var scan = node; scan != null; scan = start ? scan.getFirstChild() : scan.getLastChild()) {
            if (scan.isTextblock()) return true;
            if (only && scan.getChildCount() != 1) return false;
        }
        return false;
    }

    static NodeType defaultBlockAt(ContentMatch match) {
        for (var i = 0; i < match.getEdgeCount(); i++) {
            var type = match.edge(i).getType();
            if (type.isTextblock() && !type.hasRequiredAttrs()) return type;
        }
        return null;
    }

    static boolean selectable(Node node) { return NodeSelection.Companion.isSelectable(node); }

    private Cuts() {}
}

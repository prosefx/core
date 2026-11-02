package prosefx.core.commands;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import java.util.List;

import static prosefx.core.pm.Pm.*;

/** The range half of toggleMark: add the mark unless every range already has it, trimming edge spaces. */
final class Marks {

    static boolean applies(Node doc, List<SelectionRange> ranges, MarkType type) {
        for (var r : ranges) {
            var can = new boolean[]{r.getFrom().getDepth() == 0 && doc.getInlineContent() && doc.getType().allowsMarkType(type)};
            doc.nodesBetween(r.getFrom().getPos(), r.getTo().getPos(), (node, pos, parent, index) -> {
                if (can[0]) return false;
                can[0] = node.getInlineContent() && node.getType().allowsMarkType(type);
                return true;
            }, 0, () -> false);
            if (can[0]) return true;
        }
        return false;
    }

    static Transaction toggleOverRanges(PMEditorState state, MarkType type) {
        var tr = state.getTr();
        var ranges = state.getSelection().getRanges();
        var add = !ranges.stream().allMatch(r -> fullyMarked(state.getDoc(), r.getFrom().getPos(), r.getTo().getPos(), type));
        for (var r : ranges) {
            int from = r.getFrom().getPos(), to = r.getTo().getPos();
            if (!add) {
                removeMark(tr, from, to, type);
                continue;
            }
            var start = r.getFrom().getNodeAfter();
            var end = r.getTo().getNodeBefore();
            var spaceStart = start != null && start.isText() ? leading(start.getText()) : 0;
            var spaceEnd = end != null && end.isText() ? trailing(end.getText()) : 0;
            if (from + spaceStart < to) {
                from += spaceStart;
                to -= spaceEnd;
            }
            tr.addMark(from, to, type.create(null));
        }
        return tr;
    }

    /** Confluence and Google Docs: a partly bold selection becomes all bold; only an all-bold one is unbolded. */
    static boolean fullyMarked(Node doc, int from, int to, MarkType type) {
        var all = new boolean[]{true, false};
        doc.nodesBetween(from, to, (node, pos, parent, index) -> {
            if (node.isText()) {
                all[1] = true;
                if (type.isInSet(node.getMarks()) == null) all[0] = false;
            }
            return all[0];
        }, 0, () -> false);
        return all[0] && all[1];
    }

    static int leading(String s) {
        var i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    static int trailing(String s) {
        var i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
        return s.length() - i;
    }

    private Marks() {}
}

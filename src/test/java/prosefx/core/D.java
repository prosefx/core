package prosefx.core;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.*;
import com.atlassian.prosemirror.transform.Transform;
import java.util.*;
import java.util.stream.*;
import prosefx.core.editor.EditorModel;
import prosefx.core.pm.Pm;

import static prosefx.core.schema.EditorSchema.*;
import static prosefx.core.schema.Build.*;

/**
 * Test shorthand. Text may carry "|" (cursor) or "[" and "]" (selection anchor, head).
 * show() prints the same shorthand back: p, h1..h3, ul, ol, li, **bold**, _italic_.
 */
public final class D {

    /** A model whose document is these blocks, markers removed and turned into the selection. */
    public static EditorModel model(Node... blocks) {
        var raw = doc(blocks);
        var markers = new ArrayList<int[]>();
        raw.descendants((node, pos, parent, index) -> {
            if (node.isText()) {
                var t = node.getText();
                for (var k = 0; k < t.length(); k++) if ("|[]".indexOf(t.charAt(k)) >= 0) markers.add(new int[]{pos + k, t.charAt(k)});
            }
            return true;
        });
        var tf = new Transform(raw);
        for (var k = markers.size() - 1; k >= 0; k--) tf.delete(markers.get(k)[0], markers.get(k)[0] + 1);
        var doc = tf.getDoc();
        var m = new EditorModel(doc);
        if (markers.size() == 1) m.select(Pm.cursor(doc, markers.get(0)[0]));
        if (markers.size() == 2) m.select(Pm.text(doc, markers.get(0)[0], markers.get(1)[0] - 1));
        return m;
    }

    public static void typeEach(EditorModel m, String text) { text.chars().forEach(c -> m.type(String.valueOf((char) c))); }

    public static String show(EditorModel m) {
        var s = m.state().getSelection();
        var marks = new TreeMap<Integer, String>();
        if (s.getEmpty()) marks.put(s.getHead(), "|");
        else { marks.put(s.getFrom(), "["); marks.put(s.getTo(), "]"); }
        return show(m.state().getDoc().getContent(), 0, marks);
    }

    static String show(Fragment content, int start, TreeMap<Integer, String> sel) {
        var out = new ArrayList<String>();
        var pos = start;
        for (var n : content.getContent()) {
            out.add(n.isTextblock() ? textblock(n, pos + 1, sel) : name(n) + "(" + show(n.getContent(), pos + 1, sel) + ")");
            pos += n.getNodeSize();
        }
        return String.join(", ", out);
    }

    static String textblock(Node n, int start, TreeMap<Integer, String> sel) {
        var sb = new StringBuilder();
        var pos = start;
        for (var child : n.getContent().getContent()) {
            sb.append(sel.getOrDefault(pos, ""));
            var d = delims(child);
            sb.append(d);
            var t = child.isText() ? child.getText() : "\n";
            for (var k = 0; k < t.length(); k++) sb.append(k > 0 ? sel.getOrDefault(pos + k, "") : "").append(t.charAt(k));
            sb.append(new StringBuilder(d).reverse());
            pos += child.getNodeSize();
        }
        sb.append(sel.getOrDefault(pos, ""));
        return name(n) + "(\"" + sb + "\")";
    }

    static String delims(Node text) {
        return text.getMarks().stream().map(mk -> mk.getType() == STRONG ? "**" : mk.getType() == EM ? "_" : "")
                .collect(Collectors.joining());
    }

    static String name(Node n) {
        var t = n.getType();
        if (t == HEADING) return "h" + Pm.intAttr(n, "level");
        if (t == ORDERED_LIST) {
            var order = Pm.intAttr(n, "order");
            return order == 1 ? "ol" : "ol" + order;
        }
        return Map.of(PARAGRAPH, "p", BULLET_LIST, "ul", LIST_ITEM, "li").getOrDefault(t, t.getName());
    }

    private D() {}
}

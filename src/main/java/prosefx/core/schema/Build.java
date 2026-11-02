package prosefx.core.schema;

import com.atlassian.prosemirror.model.*;
import java.util.*;
import prosefx.core.pm.Pm;

import static prosefx.core.schema.EditorSchema.*;

/** Terse document construction: doc(h(1, "Title"), p("plain ", b("bold")), ul(li(p("item")))). */
public final class Build {

    public static Node doc(Node... blocks) { return DOC.create(null, Pm.frag(blocks), null); }

    public static Node p(Object... inline) { return PARAGRAPH.create(null, inline(inline), null); }

    public static Node h(int level, Object... inline) { return HEADING.create(Map.of("level", level), inline(inline), null); }

    public static Node ul(Node... items) { return BULLET_LIST.create(null, Pm.frag(items), null); }

    public static Node ol(Node... items) { return ORDERED_LIST.create(null, Pm.frag(items), null); }

    public static Node li(Node... blocks) { return LIST_ITEM.create(null, Pm.frag(blocks), null); }

    public static Node b(String text) { return SCHEMA.text(text, List.of(STRONG.create(null))); }

    public static Node i(String text) { return SCHEMA.text(text, List.of(EM.create(null))); }

    static Fragment inline(Object... parts) {
        return Pm.frag(Arrays.stream(parts).filter(o -> !"".equals(o))
                .map(o -> o instanceof Node n ? n : SCHEMA.text((String) o, null)).toArray(Node[]::new));
    }

    private Build() {}
}

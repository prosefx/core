package prosemirrorfx.core.schema;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.testbuilder.SchemaBasicKt;
import com.atlassian.prosemirror.testbuilder.SchemaListsKt;
import java.util.*;

public final class EditorSchema {

    public static final Schema SCHEMA = build();

    public static final NodeType
            DOC = SCHEMA.getNodes().get("doc"),
            PARAGRAPH = SCHEMA.getNodes().get("paragraph"),
            HEADING = SCHEMA.getNodes().get("heading"),
            BULLET_LIST = SCHEMA.getNodes().get("bullet_list"),
            ORDERED_LIST = SCHEMA.getNodes().get("ordered_list"),
            LIST_ITEM = SCHEMA.getNodes().get("list_item"),
            HARD_BREAK = SCHEMA.getNodes().get("hard_break");

    public static final MarkType
            STRONG = SCHEMA.getMarks().get("strong"),
            EM = SCHEMA.getMarks().get("em"),
            CODE = SCHEMA.getMarks().get("code"),
            LINK = SCHEMA.getMarks().get("link"),
            RAW = SCHEMA.getMarks().get("raw"),
            HIGHLIGHT = SCHEMA.getMarks().get("highlight"),
            STRIKE = SCHEMA.getMarks().get("strike"),
            FOOTNOTE = SCHEMA.getMarks().get("footnote");

    /**
     * Optional attributes that let a file be written back as it was read (see io.Lossless). All default to
     * "not given", so documents built by code, paste or typing are written exactly as before.
     */
    static Schema build() {
        var nodes = new LinkedHashMap<>(SchemaListsKt.addListNodes(SchemaBasicKt.getNodes(), "paragraph block*", "block"));
        nodes.put("paragraph", Specs.withAttrs(nodes.get("paragraph"), attrs("src", null, "lead", "", "gap", null, "verbatim", false)));
        nodes.put("heading", Specs.withAttrs(nodes.get("heading"), attrs("src", null, "lead", "", "gap", null)));
        nodes.put("list_item", Specs.withAttrs(nodes.get("list_item"), attrs("marker", null, "lead", "", "gap", null)));
        nodes.put("hard_break", Specs.withAttrs(nodes.get("hard_break"), attrs("raw", "\\")));
        nodes.put("doc", Specs.withAttrs(nodes.get("doc"), attrs("tail", "\n", "eol", "\n", "bom", false)));
        var marks = new LinkedHashMap<>(SchemaBasicKt.getMarks());
        marks.put("raw", Specs.exclusive());
        marks.put("highlight", Specs.plain());
        marks.put("strike", Specs.plain());
        marks.put("footnote", Specs.plain());
        return new Schema(new SchemaSpec(nodes, marks, null,
                "unsupportedBlock", "unsupportedInline", null, null, "unsupportedMark", null));
    }

    /** name, default, name, default...; a null default is "not given". */
    private static Map<String, Object> attrs(Object... pairs) {
        var m = new LinkedHashMap<String, Object>();
        for (var k = 0; k < pairs.length; k += 2) m.put((String) pairs[k], pairs[k + 1]);
        return m;
    }

    private EditorSchema() {}
}

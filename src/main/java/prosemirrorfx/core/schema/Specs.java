package prosemirrorfx.core.schema;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.testbuilder.*;
import java.util.*;

/** Extends the stock prosemirror specs with attributes, which the Kotlin data class makes Java spell out in full. */
final class Specs {

    static NodeSpec withAttrs(NodeSpec s, Map<String, Object> extra) {
        var attrs = new LinkedHashMap<String, AttributeSpec>();
        if (s.getAttrs() != null) attrs.putAll(s.getAttrs());
        extra.forEach((k, v) -> attrs.put(k, new AttributeSpecImpl(v, true, null, null)));
        return new NodeSpecImpl(s.getContent(), s.getMarks(), s.getGroup(), s.getInline(), s.getAtom(), attrs,
                s.getSelectable(), s.getDraggable(), s.getCode(), s.getWhitespace(), s.getDefiningAsContext(),
                s.getDefiningForContent(), s.getDefining(), s.getIsolating(), s.getToDebugString(), s.getLeafText(),
                s.getToDOM(), s.getParseDOM(), s.getAutoFocusable(), s.getLinebreakReplacement());
    }

    /** A mark that excludes every other mark: its text is source to be written exactly as it is. */
    static MarkSpec exclusive() { return new MarkSpecImpl(null, false, "_", null, null, null, null); }

    /** A mark with no attributes that stacks with the others and only excludes itself. */
    static MarkSpec plain() { return new MarkSpecImpl(null, true, null, null, null, null, null); }

    private Specs() {}
}

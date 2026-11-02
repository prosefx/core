package prosefx.core;

import com.atlassian.prosemirror.model.Node;
import java.util.stream.IntStream;
import prosefx.core.editor.EditorModel;
import prosefx.core.pm.Pm;

import static prosefx.core.schema.Build.*;

/** Per-keystroke cost of the model alone (no view), in a document of a few hundred bullets. Run with exec:java, test scope. */
public final class Bench {

    public static void main(String[] args) {
        var items = IntStream.range(0, 300).mapToObj(k -> li(p("Sentence number " + k + " of a long list that goes on for a while."))).toArray(Node[]::new);
        var doc = doc(h(1, "Notes"), ul(items), p(""));
        var m = new EditorModel(doc);
        m.select(Pm.atEnd(m.state().getDoc()));
        for (var round = 0; round < 3; round++) {
            var t0 = System.nanoTime();
            var n = 2000;
            for (var k = 0; k < n; k++) m.type(k % 40 == 39 ? " " : "x");
            var us = (System.nanoTime() - t0) / 1000.0 / n;
            System.out.printf("round %d: %.1f us per keystroke (model only)%n", round, us);
        }
    }

    private Bench() {}
}

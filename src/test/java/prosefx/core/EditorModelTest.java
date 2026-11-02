package prosefx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.schema.Build.*;

class EditorModelTest {

    @Test void aListenerCanReadTheTransactionBehindTheChangeItIsToldAbout() {
        var m = model(p("ab|c"));
        var mapped = new int[2];
        m.onChange((old, now) -> {
            var mapping = m.lastTransaction().getMapping();
            mapped[0] = mapping.map(2, 1);
            mapped[1] = mapping.map(3, 1);
        });
        typeEach(m, "X");
        assertEquals(2, mapped[0], "before the typed letter nothing moves");
        assertEquals(4, mapped[1], "at the typed letter, a position moves past it");
    }

    @Test void loadingADocumentLeavesNoTransactionBehind() {
        var m = model(p("one|"));
        typeEach(m, "x");
        assertNotNull(m.lastTransaction());
        m.load(doc(p("fresh")));
        assertNull(m.lastTransaction());
    }
}

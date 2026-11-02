package prosefx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.schema.Build.*;
import static prosefx.core.commands.Commands.*;

class ListTest {

    @Test void bulletListWrapsAndUnwraps() {
        var m = model(p("a|"));
        m.run(bulletList);
        assertEquals("ul(li(p(\"a|\")))", show(m));
        m.run(bulletList);
        assertEquals("p(\"a|\")", show(m));
    }

    @Test void switchingListKind() {
        var m = model(ul(li(p("a|")), li(p("b"))));
        m.run(orderedList);
        assertEquals("ol(li(p(\"a|\")), li(p(\"b\")))", show(m));
        m.run(bulletList);
        assertEquals("ul(li(p(\"a|\")), li(p(\"b\")))", show(m));
    }

    @Test void enterSplitsItem() {
        var m = model(ul(li(p("ab|cd"))));
        m.run(enter);
        assertEquals("ul(li(p(\"ab\")), li(p(\"|cd\")))", show(m));
    }

    @Test void enterOnEmptyLastItemLeavesTheList() {
        var m = model(ul(li(p("a")), li(p("|"))));
        m.run(enter);
        assertEquals("ul(li(p(\"a\"))), p(\"|\")", show(m));
    }

    @Test void tabNestsAndShiftTabUnnests() {
        var m = model(ul(li(p("a")), li(p("b|"))));
        m.run(indent);
        assertEquals("ul(li(p(\"a\"), ul(li(p(\"b|\")))))", show(m));
        m.run(outdent);
        assertEquals("ul(li(p(\"a\")), li(p(\"b|\")))", show(m));
    }

    @Test void firstItemCannotNest() {
        var m = model(ul(li(p("a|"))));
        assertFalse(m.run(indent));
    }

    @Test void enterOnEmptyNestedItemMovesItOut() {
        var m = model(ul(li(p("a"), ul(li(p("b")), li(p("|"))))));
        m.run(enter);
        assertEquals("ul(li(p(\"a\"), ul(li(p(\"b\")))), li(p(\"|\")))", show(m));
    }

    @Test void backspaceAtFirstItemStartRemovesTheBullet() {
        var m = model(p("x"), ul(li(p("|a")), li(p("b"))));
        m.run(backspace);
        assertEquals("p(\"x\"), p(\"|a\"), ul(li(p(\"b\")))", show(m));
        m.run(backspace);
        assertEquals("p(\"x|a\"), ul(li(p(\"b\")))", show(m));
    }

    /** Confluence: the line joins the bullet above, rather than leaving the list (which the next backspace would undo). */
    @Test void backspaceAtLaterItemStartJoinsThePreviousItem() {
        var m = model(ul(li(p("a")), li(p("|b")), li(p("c"))));
        m.run(backspace);
        assertEquals("ul(li(p(\"a|b\")), li(p(\"c\")))", show(m));
    }

    @Test void backspaceAtNestedFirstItemJoinsTheParentLine() {
        var m = model(ul(li(p("a"), ul(li(p("|b")), li(p("c"))))));
        m.run(backspace);
        assertEquals("ul(li(p(\"a|b\"), ul(li(p(\"c\")))))", show(m));
    }

    @Test void joiningAnItemKeepsItsChildren() {
        var m = model(ul(li(p("a")), li(p("|b"), ul(li(p("c"))))));
        m.run(backspace);
        assertEquals("ul(li(p(\"a|b\"), ul(li(p(\"c\")))))", show(m));
    }

    @Test void numberedListOverSeveralParagraphs() {
        var m = model(p("[a"), p("b]"));
        m.run(orderedList);
        assertEquals("ol(li(p(\"[a\")), li(p(\"b]\")))", show(m));
    }
}

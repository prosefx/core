package prosefx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.schema.Build.*;
import static prosefx.core.commands.Commands.*;

class TextEditingTest {

    @Test void typing() {
        var m = model(p("|"));
        typeEach(m, "hello");
        assertEquals("p(\"hello|\")", show(m));
    }

    @Test void enterSplitsParagraph() {
        var m = model(p("hel|lo"));
        m.run(enter);
        assertEquals("p(\"hel\"), p(\"|lo\")", show(m));
    }

    @Test void backspaceJoinsParagraphs() {
        var m = model(p("a"), p("|b"));
        m.run(backspace);
        assertEquals("p(\"a|b\")", show(m));
    }

    @Test void deleteJoinsForward() {
        var m = model(p("a|"), p("b"));
        m.run(delete);
        assertEquals("p(\"a|b\")", show(m));
    }

    @Test void backspaceDeletesOneCharacter() {
        var m = model(p("ab|c"));
        m.run(backspace);
        assertEquals("p(\"a|c\")", show(m));
    }

    @Test void deleteDeletesOneCharacter() {
        var m = model(p("a|bc"));
        m.run(delete);
        assertEquals("p(\"a|c\")", show(m));
    }

    @Test void backspaceAcrossMarkBoundary() {
        var m = model(p("a", b("b"), "|c"));
        m.run(backspace);
        assertEquals("p(\"a|c\")", show(m));
    }

    @Test void backspaceInsideListItemStaysInItem() {
        var m = model(ul(li(p("ab|"))));
        m.run(backspace);
        assertEquals("ul(li(p(\"a|\")))", show(m));
    }

    @Test void backspaceRemovesEmojiWhole() {
        var m = model(p("a😀|"));
        m.run(backspace);
        assertEquals("p(\"a|\")", show(m));
    }

    @Test void backspaceDeletesSelection() {
        var m = model(p("a[bc]d"));
        m.run(backspace);
        assertEquals("p(\"a|d\")", show(m));
    }

    @Test void boldToggledAtCursorAppliesToWhatIsTypedNext() {
        var m = model(p("|"));
        typeEach(m, "a");
        m.run(bold);
        typeEach(m, "b");
        m.run(bold);
        typeEach(m, "c");
        assertEquals("p(\"a**b**c|\")", show(m));
    }

    @Test void boldOverSelectionThenOff() {
        var m = model(p("x[abc]y"));
        m.run(bold);
        assertEquals("p(\"x[**abc**]y\")", show(m));
        m.run(bold);
        assertEquals("p(\"x[abc]y\")", show(m));
    }

    @Test void partlyBoldSelectionBecomesAllBold() {
        var m = model(p("[a ", b("b"), " c]"));
        m.run(bold);
        assertEquals("p(\"[**a b c**]\")", show(m));
    }

    @Test void italic() {
        var m = model(p("[ab]"));
        m.run(italic);
        assertTrue(show(m).contains("_"), show(m));
    }

    @Test void headingCyclesThroughLevelsBackToParagraph() {
        var m = model(p("t|"));
        m.run(cycleHeading);
        assertEquals("h1(\"t|\")", show(m));
        m.run(cycleHeading);
        m.run(cycleHeading);
        assertEquals("h3(\"t|\")", show(m));
        m.run(cycleHeading);
        assertEquals("p(\"t|\")", show(m));
    }

    @Test void enterAtEndOfHeadingGivesParagraph() {
        var m = model(h(1, "Title|"));
        m.run(enter);
        assertEquals("h1(\"Title\"), p(\"|\")", show(m));
    }

    @Test void undoAndRedo() {
        var m = model(p("|"));
        typeEach(m, "abc");
        m.run(undo);
        assertEquals("p(\"|\")", show(m));
        m.run(redo);
        assertEquals("p(\"abc|\")", show(m));
    }
}

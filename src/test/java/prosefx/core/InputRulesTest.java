package prosefx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.schema.Build.*;

class InputRulesTest {

    @Test void hashSpaceMakesHeadings() {
        var m = model(p("|"));
        typeEach(m, "# Title");
        assertEquals("h1(\"Title|\")", show(m));
        var m2 = model(p("|"));
        typeEach(m2, "### Sub");
        assertEquals("h3(\"Sub|\")", show(m2));
    }

    @Test void dashSpaceMakesBulletList() {
        var m = model(p("|"));
        typeEach(m, "- item");
        assertEquals("ul(li(p(\"item|\")))", show(m));
    }

    @Test void numberDotSpaceMakesNumberedList() {
        var m = model(p("|"));
        typeEach(m, "1. one");
        assertEquals("ol(li(p(\"one|\")))", show(m));
    }

    @Test void numberedListKeepsItsStartingNumber() {
        var m = model(p("|"));
        typeEach(m, "3. three");
        assertEquals("ol3(li(p(\"three|\")))", show(m));
    }

    @Test void doubleStarsMakeBoldAndTypingContinuesPlain() {
        var m = model(p("|"));
        typeEach(m, "say **this** now");
        assertEquals("p(\"say **this** now|\")", show(m));
    }

    @Test void underscoresMakeItalic() {
        var m = model(p("|"));
        typeEach(m, "an _odd_ word");
        assertEquals("p(\"an _odd_ word|\")", show(m));
    }

    @Test void rulesOnlyFireAtTheStartOfABlock() {
        var m = model(p("|"));
        typeEach(m, "a - b # c");
        assertEquals("p(\"a - b # c|\")", show(m));
    }

    @Test void snakeCaseIsNotItalic() {
        var m = model(p("|"));
        typeEach(m, "snake_case_name");
        assertEquals("p(\"snake_case_name|\")", show(m));
    }
}

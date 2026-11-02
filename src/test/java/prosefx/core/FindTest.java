package prosefx.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import prosefx.core.commands.Commands;
import prosefx.core.editor.EditorModel;
import prosefx.core.editor.Find;
import prosefx.core.editor.Find.Options;
import prosefx.core.schema.EditorSchema;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.schema.Build.*;

class FindTest {

    static final Options CASE = new Options(false, true, false), WORD = new Options(false, false, true),
            CASE_WORD = new Options(false, true, true), REGEX = new Options(true, false, false), REGEX_CASE = new Options(true, true, false);

    static com.atlassian.prosemirror.model.Node cats() {
        return doc(p("The cat sat. A CAT."), p("concat category"));
    }

    static List<String> found(com.atlassian.prosemirror.model.Node d, String query, Options o) {
        return Find.all(d, query, o).stream().map(m -> d.textBetween(m.from(), m.to(), "\n", "\n")).toList();
    }

    @Test void plainFindIgnoresCaseAndFindsInsideWords() {
        assertEquals(List.of("cat", "CAT", "cat", "cat"), found(cats(), "cat", Options.PLAIN));
        assertEquals(Find.all(cats(), "cat"), Find.all(cats(), "cat", Options.PLAIN), "the old call is the plain one");
    }

    @Test void matchCaseTellsUpperFromLower() {
        assertEquals(List.of("cat", "cat", "cat"), found(cats(), "cat", CASE));
        assertEquals(List.of("CAT"), found(cats(), "CAT", CASE));
    }

    @Test void wholeWordSkipsMatchesInsideLongerWords() {
        assertEquals(List.of("cat", "CAT"), found(cats(), "cat", WORD));
        assertEquals(List.of("cat"), found(cats(), "cat", CASE_WORD));
    }

    @Test void aWordIncludesLettersOfAnyScriptAndTheirMarks() {
        var d = doc(p("வணக்கம் உலகம்"), p("வணக்கம்கள் done"));
        assertEquals(List.of("வணக்கம்"), found(d, "வணக்கம்", WORD), "whole word, and not inside the longer word in the second block");
        assertTrue(found(d, "வணக்", WORD).isEmpty(), "a prefix that ends inside a word is not a whole word");
        assertEquals(List.of("done"), found(d, "done", WORD));
    }

    @Test void regexMatchesPatternsAndHonoursCase() {
        var d = cats();
        assertEquals(List.of("cat", "CAT", "cat", "cat"), found(d, "c.t", REGEX));
        assertEquals(List.of("cat", "cat", "cat"), found(d, "c.t", REGEX_CASE));
        assertEquals(List.of("cat", "CAT"), found(d, "\\bcat\\b", REGEX));
        assertEquals(List.of("category"), found(d, "cat\\w+", REGEX), "only category has letters after cat");
    }

    @Test void wholeWordAppliesToARegexAsAWholeToo() {
        var d = doc(p("one two three"));
        assertEquals(List.of("one", "two"), found(d, "one|two|thr", new Options(true, false, true)));
    }

    @Test void caretAndDollarMatchAtEachLineOfABlock() {
        var d = doc(p("first", EditorSchema.HARD_BREAK.create(null, prosefx.core.pm.Pm.EMPTY, null), "second"));
        assertEquals(List.of("second"), found(d, "^second", REGEX));
        assertEquals(List.of("first"), found(d, "first$", REGEX));
    }

    @Test void aBadPatternIsReportedAndFindsNothing() {
        assertNotNull(Find.problem("(unclosed", REGEX));
        assertNull(Find.problem("(unclosed", Options.PLAIN), "the same text is fine as a plain query");
        assertTrue(Find.all(cats(), "(unclosed", REGEX).isEmpty());
        assertEquals(List.of("("), found(doc(p("a (b")), "(", Options.PLAIN), "special characters are plain text in plain mode");
        assertNull(Find.problem("", REGEX));
    }

    @Test void aPatternThatCanMatchNothingDoesNotHangOrMatch() {
        assertTrue(Find.all(cats(), "x*", REGEX).stream().allMatch(m -> m.to() > m.from()));
        assertTrue(Find.all(cats(), "z*", REGEX).isEmpty());
    }

    @Test void aHitCarriesItsBlockAndTheLineAndColumnItStartsOn() {
        var d = doc(p("nothing here"), p("first line", EditorSchema.HARD_BREAK.create(null, prosefx.core.pm.Pm.EMPTY, null), "the second cat"));
        var hits = Find.hits(d, "cat", Options.PLAIN);
        assertEquals(1, hits.size());
        var h = hits.getFirst();
        assertEquals(1, h.block());
        assertEquals("the second cat", h.line());
        assertEquals(11, h.column());
        assertEquals("cat", d.textBetween(h.from(), h.to(), "\n", "\n"));
    }

    @Test void replacementsAreLiteralInPlainModeAndExpandGroupsInRegexMode() {
        var d = doc(p("mail ann@example and bob@test"));
        var literal = Find.replacements(d, "ann", Options.PLAIN, "$1");
        assertEquals("$1", literal.getFirst().text(), "no group syntax in plain mode");
        var groups = Find.replacements(d, "(\\w+)@(\\w+)", REGEX, "$2 at $1");
        assertEquals(List.of("example at ann", "test at bob"), groups.stream().map(Find.Replacement::text).toList());
        assertThrows(RuntimeException.class, () -> Find.replacements(d, "(\\w+)@", REGEX, "$5"));
    }

    @Test void replaceAllMakesEveryReplacementInOneUndoStep() {
        var m = new EditorModel(doc(p("one cat, two cats"), p("a cat")));
        var tr = Find.replaceAll(m.state(), Find.replacements(m.state().getDoc(), "cat", Options.PLAIN, "dog"));
        m.dispatch(tr);
        assertEquals("one dog, two dogs\na dog", m.state().getDoc().textBetween(0, m.state().getDoc().getContent().getSize(), "\n", "\n"));
        m.run(Commands.undo);
        assertEquals("one cat, two cats\na cat", m.state().getDoc().textBetween(0, m.state().getDoc().getContent().getSize(), "\n", "\n"));
    }

    @Test void aRegexReplacementCanDeleteWithAnEmptyString() {
        var m = new EditorModel(doc(p("a1b22c")));
        m.dispatch(Find.replaceAll(m.state(), Find.replacements(m.state().getDoc(), "\\d+", REGEX, "")));
        assertEquals("abc", m.state().getDoc().textBetween(0, m.state().getDoc().getContent().getSize(), "\n", "\n"));
    }
}

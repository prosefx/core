package prosefx.core;

import org.junit.jupiter.api.Test;
import prosefx.core.editor.Tail;
import prosefx.core.editor.EditorModel;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.D.*;
import static prosefx.core.commands.Commands.*;
import static prosefx.core.schema.Build.*;

class TailTest {

    static void add(EditorModel m, String text) {
        m.dispatch(Tail.append(m.state(), text));
    }

    static String plain(EditorModel m) { return show(m).replace("|", ""); }

    @Test void startsAListInAnEmptyDocumentAndFollows() {
        var m = model(p("|"));
        add(m, "Hello there.");
        assertEquals("ul(li(p(\"Hello there.|\")))", show(m));
    }

    @Test void eachSentenceIsABullet() {
        var m = model(p("|"));
        add(m, "First one. Second one? Third");
        assertEquals("ul(li(p(\"First one.\")), li(p(\"Second one?\")), li(p(\"Third|\")))", show(m));
    }

    @Test void unfinishedSentenceContinuesOnTheSameBullet() {
        var m = model(p("|"));
        add(m, "the quick brown");
        add(m, "fox jumps.");
        add(m, "Next sentence");
        assertEquals("ul(li(p(\"the quick brown fox jumps.\")), li(p(\"Next sentence|\")))", show(m));
    }

    @Test void timestampLineThenAppendStartsAListUnderIt() {
        var m = model(p("|"));
        m.dispatch(Tail.appendLine(m.state(), "[2026-01-15T09:30:00Z]"));
        add(m, "First words.");
        assertEquals("p(\"[2026-01-15T09:30:00Z]\"), ul(li(p(\"First words.|\")))", show(m));
        m.dispatch(Tail.appendLine(m.state(), "[next section]"));
        add(m, "More.");
        assertEquals("p(\"[2026-01-15T09:30:00Z]\"), ul(li(p(\"First words.\"))), p(\"[next section]\"), ul(li(p(\"More.|\")))", show(m));
    }

    @Test void emptyLineAfterAListIsFoldedIntoIt() {
        var m = model(h(1, "Notes"), ul(li(p("old point."))), p("|"));
        add(m, "New point.");
        assertEquals("h1(\"Notes\"), ul(li(p(\"old point.\")), li(p(\"New point.|\")))", show(m));
    }

    @Test void appendsAfterAParagraphAsANewList() {
        var m = model(p("An intro|"));
        add(m, "Added.");
        assertEquals("p(\"An intro\"), ul(li(p(\"Added.\")))", plain(m));
    }

    @Test void caretElsewhereStaysPut() {
        var m = model(h(1, "Ti|tle"), ul(li(p("one."))));
        add(m, "Two.");
        assertEquals("h1(\"Ti|tle\"), ul(li(p(\"one.\")), li(p(\"Two.\")))", show(m));
    }

    @Test void continuesTheInnermostList() {
        var m = model(ul(li(p("a."), ul(li(p("nested|"))))));
        add(m, "more. And another");
        assertEquals("ul(li(p(\"a.\"), ul(li(p(\"nested more.\")), li(p(\"And another|\")))))", show(m));
    }

    @Test void eachAppendIsItsOwnUndoStep() {
        var m = model(p("|"));
        add(m, "One.");
        add(m, "Two.");
        m.run(undo);
        assertEquals("ul(li(p(\"One.\")))", plain(m));
        m.run(undo);
        assertEquals("p(\"\")", plain(m));
    }

    @Test void hindiDandaEndsASentence() {
        var m = model(p("|"));
        add(m, "पहला वाक्य। दूसरा");
        assertEquals("ul(li(p(\"पहला वाक्य।\")), li(p(\"दूसरा|\")))", show(m));
    }

    @Test void blankAppendDoesNothing() {
        assertNull(Tail.append(model(p("|")).state(), "   "));
    }
}

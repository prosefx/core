# prosefx-core

The document model and editing for prosefx, on the JVM with no JavaFX: a ProseMirror schema (paragraphs, headings, nested bullet and numbered lists, bold, italic, code, links), the editing commands behind Enter, Backspace, Tab and the formatting keys, markdown-style input rules, find and replace, word counts, and an `EditorModel` that holds the state and tells listeners about each change.

Built on [prosemirror-kotlin](https://github.com/atlassian-labs/prosemirror-kotlin) by Atlassian (Apache License 2.0), a Kotlin port of [ProseMirror](https://prosemirror.net) by Marijn Haverbeke (MIT). prosefx is an independent project and is not affiliated with ProseMirror or Atlassian.

## Status

Not yet published. Snapshots only to begin with: `0.1.0-SNAPSHOT`, on Central's snapshot repository `https://central.sonatype.com/repository/maven-snapshots/`. Java 25.

    <dependency>
        <groupId>io.github.prosefx</groupId>
        <artifactId>prosefx-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

## Packages

    prosefx.core.pm        Pm: the Java face of prosemirror-kotlin (its Kotlin default arguments, and workarounds)
    prosefx.core.schema    EditorSchema (the base schema), Build (terse document construction for tests and demos)
    prosefx.core.commands  Commands and the ported prosemirror-commands and prosemirror-schema-list
    prosefx.core.rules     InputRules: "# ", "- ", "1. ", **bold**, _italic_ as you type
    prosefx.core.editor    EditorModel, Find, Stats, Tail (append text at the end of the document as bullets)

`UpstreamBugsTest` pins three prosemirror-kotlin 1.1.21 bugs that `Pm` and `EditorModel` work around; when one of them starts failing, upstream has fixed it and the workaround can go.

## Tests

    mvn test

The test shorthand in `D` writes a document as `ul(li(p("one|")))`, with `|` for the caret and `[` `]` for a selection.

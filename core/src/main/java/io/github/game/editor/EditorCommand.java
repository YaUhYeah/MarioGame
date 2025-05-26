// File: core/src/main/java/io/github/game/editor/EditorCommand.java
package io.github.game.editor;

public interface EditorCommand {
    void execute();
    void undo();
}

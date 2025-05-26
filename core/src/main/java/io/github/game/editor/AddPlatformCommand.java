// File: core/src/main/java/io/github/game/editor/AddPlatformCommand.java
package io.github.game.editor;

import io.github.game.Level;
import io.github.game.Level.PlatformData; // If PlatformData is public inner class

public class AddPlatformCommand implements EditorCommand {
    private Level level;
    private PlatformData platformData;

    public AddPlatformCommand(Level level, PlatformData platformData) {
        this.level = level;
        this.platformData = platformData;
    }

    @Override
    public void execute() {
        level.addPlatform(platformData);
    }

    @Override
    public void undo() {
        level.removePlatform(platformData); // Assumes Level.removePlatform can remove by instance
    }
}

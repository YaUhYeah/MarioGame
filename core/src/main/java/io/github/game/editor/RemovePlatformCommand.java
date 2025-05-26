// File: core/src/main/java/io/github/game/editor/RemovePlatformCommand.java
package io.github.game.editor;

import io.github.game.Level;
import io.github.game.Level.PlatformData;
import com.badlogic.gdx.utils.Array; // For storing index if needed

public class RemovePlatformCommand implements EditorCommand {
    private Level level;
    private PlatformData platformData;
    private int originalIndex = -1; // Optional: for restoring exact order

    public RemovePlatformCommand(Level level, PlatformData platformData) {
        this.level = level;
        this.platformData = platformData;
        // To store originalIndex, you'd find it before removing:
        // this.originalIndex = level.getPlatformData().indexOf(platformData, true);
    }

    @Override
    public void execute() {
        // If originalIndex was stored, ensure it's still valid or re-find before removal.
        // For simplicity, we assume Level.removePlatform works by instance.
        level.removePlatform(platformData);
    }

    @Override
    public void undo() {
        // if (originalIndex != -1 && originalIndex < level.getPlatformData().size) {
        //    level.getPlatformData().insert(originalIndex, platformData);
        // } else {
        level.addPlatform(platformData); // Adds to the end if index not used
        // }
    }
}

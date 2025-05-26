// File: core/src/main/java/io/github/game/editor/ClearLevelCommand.java
package io.github.game.editor;

import com.badlogic.gdx.utils.Array;
import io.github.game.Level;
import io.github.game.Level.PlatformData;

public class ClearLevelCommand implements EditorCommand {
    private Level level;
    private Array<PlatformData> originalPlatforms;
    private PlatformData defaultGroundAdded; // The specific instance of default ground added

    public ClearLevelCommand(Level level, Array<PlatformData> platformsBeforeClear, PlatformData defaultGroundInstance) {
        this.level = level;
        this.originalPlatforms = new Array<>(platformsBeforeClear); // Store a copy of the state before clear
        this.defaultGroundAdded = defaultGroundInstance;
    }

    @Override
    public void execute() {
        level.getPlatformData().clear();
        if (defaultGroundAdded != null) {
            level.addPlatform(defaultGroundAdded); // Add the specific instance
        }
    }

    @Override
    public void undo() {
        level.getPlatformData().clear();
        level.getPlatformData().addAll(originalPlatforms);
    }
}

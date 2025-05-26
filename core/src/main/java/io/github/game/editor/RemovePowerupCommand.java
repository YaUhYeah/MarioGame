
package io.github.game.editor;

import io.github.game.Level;

public class RemovePowerupCommand implements EditorCommand {
    private Level level;
    private Level.PowerupData powerupData;

    public RemovePowerupCommand(Level level, Level.PowerupData powerupData) {
        this.level = level;
        this.powerupData = powerupData;
    }

    @Override
    public void execute() {
        level.removePowerup(powerupData);
    }

    @Override
    public void undo() {
        level.addPowerup(powerupData);
    }
}

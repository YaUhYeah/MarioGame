package io.github.game.editor;

import io.github.game.Level;

public class AddPowerupCommand implements EditorCommand {
    private Level level;
    private Level.PowerupData powerupData;

    public AddPowerupCommand(Level level, Level.PowerupData powerupData) {
        this.level = level;
        this.powerupData = powerupData;
    }

    @Override
    public void execute() {
        level.addPowerup(powerupData);
    }

    @Override
    public void undo() {
        level.removePowerup(powerupData);
    }
}

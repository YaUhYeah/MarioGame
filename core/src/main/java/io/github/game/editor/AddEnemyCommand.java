package io.github.game.editor;

import io.github.game.Level;

public class AddEnemyCommand implements EditorCommand {
    private Level level;
    private Level.EnemyData enemyData;

    public AddEnemyCommand(Level level, Level.EnemyData enemyData) {
        this.level = level;
        this.enemyData = enemyData;
    }

    @Override
    public void execute() {
        level.addEnemy(enemyData);
    }

    @Override
    public void undo() {
        level.removeEnemy(enemyData);
    }
}

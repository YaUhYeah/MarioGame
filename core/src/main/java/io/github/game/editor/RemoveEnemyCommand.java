// File: core/src/main/java/io/github/game/editor/RemoveEnemyCommand.java
package io.github.game.editor;

import io.github.game.Level;

public class RemoveEnemyCommand implements EditorCommand {
    private Level level;
    private Level.EnemyData enemyData;
    // private int originalIndex = -1; // Optional for restoring order

    public RemoveEnemyCommand(Level level, Level.EnemyData enemyData) {
        this.level = level;
        this.enemyData = enemyData;
        // this.originalIndex = level.getEnemyData().indexOf(enemyData, true);
    }

    @Override
    public void execute() {
        level.removeEnemy(enemyData);
    }

    @Override
    public void undo() {
        // if (originalIndex != -1 && originalIndex < level.getEnemyData().size) {
        //    level.getEnemyData().insert(originalIndex, enemyData);
        // } else {
        level.addEnemy(enemyData); // Adds to the end if index not used
        // }
    }
}

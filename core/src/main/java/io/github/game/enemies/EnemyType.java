// File: core/src/main/java/io/github/game/enemies/EnemyType.java
package io.github.game.enemies;

public enum EnemyType {
    GOOMBA("Goomba"),
    KOOPA("Koopa Troopa"); // NEW: Added Koopa Troopa
    // Add other enemy types here in the future, e.g., KOOPA_TROOPA

    private final String displayName;

    EnemyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

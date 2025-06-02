// File: core/src/main/java/io/github/game/WorldMapNode.java
package io.github.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class WorldMapNode {

    public enum NodeType {
        LEVEL,
        CASTLE,
        FORTRESS,
        GHOST_HOUSE,
        SPECIAL
    }

    public enum NodeState {
        LOCKED,
        UNLOCKED,
        COMPLETED
    }

    private Vector2 position;
    private Rectangle bounds;
    private String levelName;
    private String levelFileName; // NEW: JSON file name for this level
    private NodeType type;
    private NodeState state;
    private int worldNumber;
    private int levelNumber;
    private boolean isSelected;
    private float animationTimer;
    private Color nodeColor;

    // Static textures for different node types
    private static Texture levelNodeTexture;
    private static Texture castleNodeTexture;
    private static Texture fortressNodeTexture;
    private static Texture spookyNodeTexture;
    private static Texture specialNodeTexture;
    private static Texture lockedOverlayTexture;

    public static final float NODE_SIZE = 32f;
    public static final float CASTLE_SIZE = 48f;

    static {
        loadNodeTextures();
    }

    private static void loadNodeTextures() {
        try {
            // Load node textures based on your file structure
            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/level_node.png").exists()) {
                levelNodeTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/level_node.png");
                com.badlogic.gdx.Gdx.app.log("WorldMapNode", "Loaded level_node.png");
            }

            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/castle_node.png").exists()) {
                castleNodeTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/castle_node.png");
                com.badlogic.gdx.Gdx.app.log("WorldMapNode", "Loaded castle_node.png");
            }

            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/fortress_node.png").exists()) {
                fortressNodeTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/fortress_node.png");
                com.badlogic.gdx.Gdx.app.log("WorldMapNode", "Loaded fortress_node.png");
            }

            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/spooky_node.png").exists()) {
                spookyNodeTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/spooky_node.png");
                com.badlogic.gdx.Gdx.app.log("WorldMapNode", "Loaded spooky_node.png");
            }

            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/special_node.png").exists()) {
                specialNodeTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/special_node.png");
                com.badlogic.gdx.Gdx.app.log("WorldMapNode", "Loaded special_node.png");
            }

            // Try to find a locked overlay texture (you can create this or use a darker version)
            if (com.badlogic.gdx.Gdx.files.internal("mario_sprites/world_map/locked_overlay.png").exists()) {
                lockedOverlayTexture = new com.badlogic.gdx.graphics.Texture("mario_sprites/world_map/locked_overlay.png");
            }

        } catch (Exception e) {
            com.badlogic.gdx.Gdx.app.error("WorldMapNode", "Failed to load some node textures", e);
        }
    }

    public WorldMapNode(float x, float y, String levelName, NodeType type, int worldNumber, int levelNumber) {
        this.position = new Vector2(x, y);
        this.levelName = levelName;
        this.type = type;
        this.worldNumber = worldNumber;
        this.levelNumber = levelNumber;
        this.state = NodeState.LOCKED;
        this.isSelected = false;
        this.animationTimer = 0f;

        // Set appropriate size based on node type
        float nodeSize = NODE_SIZE;
        if (type == NodeType.CASTLE) {
            nodeSize = CASTLE_SIZE;
        }

        this.bounds = new Rectangle(x - nodeSize/2, y - nodeSize/2, nodeSize, nodeSize);

        // Set node color based on type (used as fallback if textures fail)
        switch (type) {
            case LEVEL:
                nodeColor = Color.RED;
                break;
            case CASTLE:
                nodeColor = Color.PURPLE;
                break;
            case FORTRESS:
                nodeColor = Color.DARK_GRAY;
                break;
            case GHOST_HOUSE:
                nodeColor = Color.WHITE;
                break;
            case SPECIAL:
                nodeColor = Color.YELLOW;
                break;
        }
    }

    public void update(float deltaTime) {
        animationTimer += deltaTime;
    }

    public void render(SpriteBatch batch) {
        // Get the appropriate texture for this node type
        Texture nodeTexture = getTextureForType(type);

        // Calculate size based on type
        float renderSize = (type == NodeType.CASTLE) ? CASTLE_SIZE : NODE_SIZE;

        // Calculate pulsing effect for selected nodes
        float pulseScale = 1.0f;
        if (isSelected) {
            pulseScale = 1.0f + 0.15f * (float)Math.sin(animationTimer * 6.0f);
            renderSize *= pulseScale;
        }

        // Calculate glow effect for unlocked nodes
        Color renderColor = Color.WHITE;
        if (state == NodeState.UNLOCKED) {
            float glowAlpha = 0.8f + 0.2f * (float)Math.sin(animationTimer * 3.0f);
            renderColor = new Color(1f, 1f, 1f, glowAlpha);
        } else if (state == NodeState.COMPLETED) {
            renderColor = new Color(0.8f, 1f, 0.8f, 1f); // Slight green tint for completed
        }

        // Calculate position for centered rendering
        float renderX = position.x - renderSize / 2;
        float renderY = position.y - renderSize / 2;

        // Set batch color
        batch.setColor(renderColor);

        if (nodeTexture != null) {
            // Render the actual node texture
            batch.draw(nodeTexture, renderX, renderY, renderSize, renderSize);

            // Render locked overlay if node is locked
            if (state == NodeState.LOCKED) {
                if (lockedOverlayTexture != null) {
                    batch.setColor(0.3f, 0.3f, 0.3f, 0.7f); // Dark overlay
                    batch.draw(lockedOverlayTexture, renderX, renderY, renderSize, renderSize);
                } else {
                    // Fallback: darken the node
                    batch.setColor(0.3f, 0.3f, 0.3f, 0.8f);
                    batch.draw(nodeTexture, renderX, renderY, renderSize, renderSize);
                }
            }
        } else {
            // Fallback: render colored rectangle if texture is missing
            // Note: This would require a simple white pixel texture to work properly
            // For now, we'll log an error
            com.badlogic.gdx.Gdx.app.error("WorldMapNode", "Missing texture for node type: " + type);
        }

        // Reset batch color
        batch.setColor(Color.WHITE);

        // Render selection indicator if selected
        if (isSelected) {
            renderSelectionIndicator(batch, renderX, renderY, renderSize);
        }
    }

    private void renderSelectionIndicator(SpriteBatch batch, float x, float y, float size) {
        // This could be a pulsing ring or glow effect around the selected node
        // For now, we'll use a simple approach - in a full implementation you'd have a selection ring texture

        // Note: This would require switching to ShapeRenderer or having a ring texture
        // Since we're in SpriteBatch context, we'll create a visual effect using the existing texture

        float ringSize = size * 1.3f;
        float ringX = position.x - ringSize / 2;
        float ringY = position.y - ringSize / 2;

        // Create a pulsing yellow tint around the node
        Color selectionColor = new Color(1f, 1f, 0f, 0.3f + 0.2f * (float)Math.sin(animationTimer * 8.0f));
        batch.setColor(selectionColor);

        // If we have the node texture, draw it slightly larger as a selection ring
        Texture nodeTexture = getTextureForType(type);
        if (nodeTexture != null) {
            batch.draw(nodeTexture, ringX, ringY, ringSize, ringSize);
        }

        batch.setColor(Color.WHITE);
    }

    private Texture getTextureForType(NodeType type) {
        switch (type) {
            case LEVEL:
                return levelNodeTexture;
            case CASTLE:
                return castleNodeTexture;
            case FORTRESS:
                return fortressNodeTexture;
            case GHOST_HOUSE:
                return spookyNodeTexture; // Using spooky for ghost house
            case SPECIAL:
                return specialNodeTexture;
            default:
                return levelNodeTexture; // Fallback
        }
    }

    public boolean contains(float x, float y) {
        return bounds.contains(x, y);
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        updateBounds();
    }

    private void updateBounds() {
        float nodeSize = (type == NodeType.CASTLE) ? CASTLE_SIZE : NODE_SIZE;
        bounds.setPosition(position.x - nodeSize/2, position.y - nodeSize/2);
        bounds.setSize(nodeSize, nodeSize);
    }

    public void unlock() {
        if (state == NodeState.LOCKED) {
            state = NodeState.UNLOCKED;
        }
    }

    public void complete() {
        state = NodeState.COMPLETED;
    }

    public boolean isAccessible() {
        return state == NodeState.UNLOCKED || state == NodeState.COMPLETED;
    }

    // Getters and setters
    public Vector2 getPosition() { return position; }
    public Rectangle getBounds() { return bounds; }
    public String getLevelName() { return levelName; }
    public String getLevelFileName() { return levelFileName; } // NEW
    public void setLevelFileName(String levelFileName) { this.levelFileName = levelFileName; } // NEW
    public NodeType getType() { return type; }
    public NodeState getState() { return state; }
    public void setState(NodeState state) { this.state = state; }
    public int getWorldNumber() { return worldNumber; }
    public int getLevelNumber() { return levelNumber; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { this.isSelected = selected; }
    public Color getNodeColor() { return nodeColor; }

    // Make textures accessible for WorldMap class
    public static Texture getLevelNodeTexture() { return levelNodeTexture; }
    public static Texture getCastleNodeTexture() { return castleNodeTexture; }
    public static Texture getFortressNodeTexture() { return fortressNodeTexture; }
    public static Texture getSpookyNodeTexture() { return spookyNodeTexture; }
    public static Texture getSpecialNodeTexture() { return specialNodeTexture; }

    public static void disposeStaticTextures() {
        if (levelNodeTexture != null) {
            levelNodeTexture.dispose();
            levelNodeTexture = null;
        }
        if (castleNodeTexture != null) {
            castleNodeTexture.dispose();
            castleNodeTexture = null;
        }
        if (fortressNodeTexture != null) {
            fortressNodeTexture.dispose();
            fortressNodeTexture = null;
        }
        if (spookyNodeTexture != null) {
            spookyNodeTexture.dispose();
            spookyNodeTexture = null;
        }
        if (specialNodeTexture != null) {
            specialNodeTexture.dispose();
            specialNodeTexture = null;
        }
        if (lockedOverlayTexture != null) {
            lockedOverlayTexture.dispose();
            lockedOverlayTexture = null;
        }
    }
}

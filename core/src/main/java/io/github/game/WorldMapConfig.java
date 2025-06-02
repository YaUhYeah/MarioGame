// File: core/src/main/java/io/github/game/WorldMapConfig.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Configuration class for world map layout and level assignments.
 * This allows easy editing of world map node positions and level assignments.
 */
public class WorldMapConfig implements Json.Serializable {

    public static class NodeConfig {
        public float x, y; // Position on world map
        public String levelFileName; // JSON file name for this level
        public String displayName; // Display name on world map
        public String nodeType; // LEVEL, CASTLE, FORTRESS, GHOST_HOUSE, SPECIAL
        public int worldNumber;
        public int levelNumber;
        public boolean startsUnlocked; // If this node should be unlocked at start

        // Default constructor for JSON serialization
        public NodeConfig() {}

        public NodeConfig(float x, float y, String levelFileName, String displayName,
                          String nodeType, int worldNumber, int levelNumber, boolean startsUnlocked) {
            this.x = x;
            this.y = y;
            this.levelFileName = levelFileName;
            this.displayName = displayName;
            this.nodeType = nodeType;
            this.worldNumber = worldNumber;
            this.levelNumber = levelNumber;
            this.startsUnlocked = startsUnlocked;
        }
    }

    public static class PathConfig {
        public int fromNodeIndex;
        public int toNodeIndex;
        public Array<PathPoint> intermediatePoints; // For curved paths

        public PathConfig() {
            intermediatePoints = new Array<>();
        }

        public PathConfig(int fromNodeIndex, int toNodeIndex) {
            this.fromNodeIndex = fromNodeIndex;
            this.toNodeIndex = toNodeIndex;
            this.intermediatePoints = new Array<>();
        }
    }

    public static class PathPoint {
        public float x, y;

        public PathPoint() {}

        public PathPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    // World map configuration
    public String worldMapTexturePath = "mario_sprites/world_map/worldmap.png";
    public float mapWidth = 800f;
    public float mapHeight = 600f;
    public float playerMoveSpeed = 120f;

    // Node configurations
    public Array<NodeConfig> nodes;

    // Path configurations
    public Array<PathConfig> paths;

    // Camera settings
    public float cameraMinX = 400f; // Half viewport width
    public float cameraMaxX = 400f; // mapWidth - half viewport width
    public float cameraMinY = 240f; // Half viewport height
    public float cameraMaxY = 360f; // mapHeight - half viewport height

    public WorldMapConfig() {
        nodes = new Array<>();
        paths = new Array<>();
        createDefaultConfiguration();
    }

    /**
     * Creates the default world map configuration.
     * FIXED: Use consistent naming convention that matches Main.java
     */
    private void createDefaultConfiguration() {
        // WORLD 1 - Grass World (4 levels + castle) - FIXED naming
        nodes.add(new NodeConfig(120, 180, "level_1_1.json", "Level 1-1", "LEVEL", 1, 1, true)); // Start unlocked
        nodes.add(new NodeConfig(220, 160, "level_1_2.json", "Level 1-2", "LEVEL", 1, 2, false));
        nodes.add(new NodeConfig(320, 200, "level_1_3.json", "Level 1-3", "LEVEL", 1, 3, false));
        nodes.add(new NodeConfig(420, 180, "level_1_4.json", "Level 1-4", "CASTLE", 1, 4, false));

        // WORLD 2 - Desert World (4 levels + castle) - FIXED naming
        nodes.add(new NodeConfig(480, 220, "level_2_1.json", "Level 2-1", "LEVEL", 2, 1, false));
        nodes.add(new NodeConfig(520, 280, "level_2_2.json", "Level 2-2", "LEVEL", 2, 2, false));
        nodes.add(new NodeConfig(560, 240, "level_2_3.json", "Level 2-3", "LEVEL", 2, 3, false));
        nodes.add(new NodeConfig(620, 200, "level_2_4.json", "Level 2-4", "CASTLE", 2, 4, false));

        // WORLD 3 - Water World (4 levels + castle) - FIXED naming
        nodes.add(new NodeConfig(150, 350, "level_3_1.json", "Level 3-1", "LEVEL", 3, 1, false));
        nodes.add(new NodeConfig(250, 370, "level_3_2.json", "Level 3-2", "LEVEL", 3, 2, false));
        nodes.add(new NodeConfig(350, 390, "level_3_3.json", "Level 3-3", "LEVEL", 3, 3, false));
        nodes.add(new NodeConfig(450, 350, "level_3_4.json", "Level 3-4", "CASTLE", 3, 4, false));

        // WORLD 4 - Ice World (4 levels + castle) - FIXED naming
        nodes.add(new NodeConfig(500, 380, "level_4_1.json", "Level 4-1", "LEVEL", 4, 1, false));
        nodes.add(new NodeConfig(600, 400, "level_4_2.json", "Level 4-2", "LEVEL", 4, 2, false));
        nodes.add(new NodeConfig(650, 430, "level_4_3.json", "Level 4-3", "LEVEL", 4, 3, false));
        nodes.add(new NodeConfig(700, 380, "level_4_4.json", "Level 4-4", "CASTLE", 4, 4, false));

        // Special areas - FIXED naming
        nodes.add(new NodeConfig(380, 280, "warp_zone.json", "Warp Zone", "SPECIAL", 0, 0, false));

        // Create paths connecting nodes
        createDefaultPaths();
    }

    private void createDefaultPaths() {
        // World 1 path (linear progression)
        paths.add(new PathConfig(0, 1)); // 1-1 to 1-2
        paths.add(new PathConfig(1, 2)); // 1-2 to 1-3
        paths.add(new PathConfig(2, 3)); // 1-3 to 1-4 (Castle)

        // World 2 path
        paths.add(new PathConfig(3, 4)); // 1-4 to 2-1
        paths.add(new PathConfig(4, 5)); // 2-1 to 2-2
        paths.add(new PathConfig(5, 6)); // 2-2 to 2-3
        paths.add(new PathConfig(6, 7)); // 2-3 to 2-4 (Castle)

        // World 3 path
        paths.add(new PathConfig(7, 8)); // 2-4 to 3-1
        paths.add(new PathConfig(8, 9)); // 3-1 to 3-2
        paths.add(new PathConfig(9, 10)); // 3-2 to 3-3
        paths.add(new PathConfig(10, 11)); // 3-3 to 3-4 (Castle)

        // World 4 path
        paths.add(new PathConfig(11, 12)); // 3-4 to 4-1
        paths.add(new PathConfig(12, 13)); // 4-1 to 4-2
        paths.add(new PathConfig(13, 14)); // 4-2 to 4-3
        paths.add(new PathConfig(14, 15)); // 4-3 to 4-4 (Castle)

        // Special connections
        paths.add(new PathConfig(1, 16)); // 1-2 to Warp Zone
        paths.add(new PathConfig(16, 12)); // Warp Zone to 4-1 (shortcut)
    }

    /**
     * Save the current configuration to a JSON file.
     */
    public void saveToFile() {
        try {
            Json json = new Json();
            FileHandle configDir = Gdx.files.local("config/");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            FileHandle file = Gdx.files.local("config/worldmap_config.json");
            file.writeString(json.prettyPrint(this), false);
            Gdx.app.log("WorldMapConfig", "Configuration saved to: " + file.path());
        } catch (Exception e) {
            Gdx.app.error("WorldMapConfig", "Failed to save world map configuration", e);
        }
    }

    /**
     * Load configuration from JSON file, or create default if not found.
     */
    public static WorldMapConfig loadFromFile() {
        try {
            FileHandle file = Gdx.files.local("config/worldmap_config.json");
            if (file.exists()) {
                Json json = new Json();
                WorldMapConfig config = json.fromJson(WorldMapConfig.class, file.readString());
                Gdx.app.log("WorldMapConfig", "Configuration loaded from: " + file.path());
                return config;
            } else {
                Gdx.app.log("WorldMapConfig", "No existing configuration found, creating default");
                WorldMapConfig config = new WorldMapConfig();
                config.saveToFile(); // Save the default configuration
                return config;
            }
        } catch (Exception e) {
            Gdx.app.error("WorldMapConfig", "Failed to load world map configuration, using default", e);
            return new WorldMapConfig();
        }
    }

    /**
     * Get node configuration by world and level number.
     */
    public NodeConfig getNodeConfig(int world, int level) {
        for (NodeConfig node : nodes) {
            if (node.worldNumber == world && node.levelNumber == level) {
                return node;
            }
        }
        return null;
    }

    /**
     * Get all nodes for a specific world.
     */
    public Array<NodeConfig> getNodesForWorld(int world) {
        Array<NodeConfig> worldNodes = new Array<>();
        for (NodeConfig node : nodes) {
            if (node.worldNumber == world) {
                worldNodes.add(node);
            }
        }
        return worldNodes;
    }

    /**
     * Add a new node configuration.
     */
    public void addNode(NodeConfig node) {
        nodes.add(node);
    }

    /**
     * Remove a node configuration.
     */
    public void removeNode(NodeConfig node) {
        nodes.removeValue(node, true);
    }

    /**
     * Add a new path configuration.
     */
    public void addPath(PathConfig path) {
        paths.add(path);
    }

    /**
     * Remove a path configuration.
     */
    public void removePath(PathConfig path) {
        paths.removeValue(path, true);
    }

    /**
     * Validate that all referenced level files exist.
     */
    public Array<String> validateLevelFiles() {
        Array<String> missingFiles = new Array<>();

        for (NodeConfig node : nodes) {
            if (node.levelFileName != null && !node.levelFileName.isEmpty()) {
                FileHandle levelFile = Gdx.files.local("levels/" + node.levelFileName);
                if (!levelFile.exists()) {
                    missingFiles.add(node.levelFileName);
                }
            }
        }

        return missingFiles;
    }

    @Override
    public void write(Json json) {
        json.writeValue("worldMapTexturePath", worldMapTexturePath);
        json.writeValue("mapWidth", mapWidth);
        json.writeValue("mapHeight", mapHeight);
        json.writeValue("playerMoveSpeed", playerMoveSpeed);
        json.writeValue("cameraMinX", cameraMinX);
        json.writeValue("cameraMaxX", cameraMaxX);
        json.writeValue("cameraMinY", cameraMinY);
        json.writeValue("cameraMaxY", cameraMaxY);
        json.writeValue("nodes", nodes);
        json.writeValue("paths", paths);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        worldMapTexturePath = jsonData.getString("worldMapTexturePath", "mario_sprites/world_map/worldmap.png");
        mapWidth = jsonData.getFloat("mapWidth", 800f);
        mapHeight = jsonData.getFloat("mapHeight", 600f);
        playerMoveSpeed = jsonData.getFloat("playerMoveSpeed", 120f);
        cameraMinX = jsonData.getFloat("cameraMinX", 400f);
        cameraMaxX = jsonData.getFloat("cameraMaxX", 400f);
        cameraMinY = jsonData.getFloat("cameraMinY", 240f);
        cameraMaxY = jsonData.getFloat("cameraMaxY", 360f);

        // Read nodes
        nodes.clear();
        JsonValue nodesJson = jsonData.get("nodes");
        if (nodesJson != null) {
            for (JsonValue nodeJson : nodesJson) {
                NodeConfig node = json.readValue(NodeConfig.class, nodeJson);
                nodes.add(node);
            }
        }

        // Read paths
        paths.clear();
        JsonValue pathsJson = jsonData.get("paths");
        if (pathsJson != null) {
            for (JsonValue pathJson : pathsJson) {
                PathConfig path = json.readValue(PathConfig.class, pathJson);
                paths.add(path);
            }
        }

        // If no nodes were loaded, create default
        if (nodes.size == 0) {
            createDefaultConfiguration();
        }
    }
}

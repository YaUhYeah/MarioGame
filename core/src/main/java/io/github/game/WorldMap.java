// File: core/src/main/java/io/github/game/WorldMap.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

public class WorldMap {

    private Texture worldMapTexture;
    private Array<WorldMapNode> nodes;
    private Array<Array<Vector2>> paths; // Paths connecting nodes
    private WorldMapNode currentSelectedNode;
    private WorldMapNode playerNode; // Current player position
    private Vector2 playerPosition;
    private Vector2 targetPosition;
    private boolean isMovingToTarget;
    private float moveSpeed = 120f;

    private BitmapFont font;
    private BitmapFont smallFont; // NEW: For UI elements
    private ShapeRenderer shapeRenderer;
    private Texture playerIconTexture;

    // Configuration system
    private WorldMapConfig config;

    // World map progress data
    private WorldMapProgress progress;
    private boolean levelSelectionMode = false;

    // Animation and effects
    private float animationTimer = 0f;
    private boolean showLevelInfo = false;

    // Debug mode for editing
    private boolean debugMode = false;
    private boolean editMode = false;
    private WorldMapNode selectedNodeForEdit = null;
    private Vector3 mousePos = new Vector3();
    private boolean wasMousePressed = false; // Track mouse state for release detection

    // NEW: Level assignment GUI
    private boolean showLevelAssignmentDialog = false;
    private WorldMapNode nodeForLevelAssignment = null;
    private Array<String> availableLevelFiles;
    private int selectedLevelFileIndex = -1;
    private Rectangle levelAssignmentDialogBounds;
    private Array<Rectangle> levelFileButtons;
    private Rectangle confirmAssignmentButton;
    private Rectangle cancelAssignmentButton;
    private float levelAssignmentScrollOffset = 0f;
    private float LEVEL_ASSIGNMENT_ITEM_HEIGHT = 35f;
    private final float LEVEL_ASSIGNMENT_DIALOG_WIDTH = 600f;
    private final float LEVEL_ASSIGNMENT_DIALOG_HEIGHT = 700f;

    // NEW: Node creation mode
    private boolean nodeCreationMode = false;
    private WorldMapNode.NodeType selectedNodeType = WorldMapNode.NodeType.LEVEL;

    public WorldMap() {
        this(false);
    }

    public WorldMap(boolean debugMode) {
        this.debugMode = debugMode;
        initialize();
    }

    private void initialize() {
        // Load configuration
        config = WorldMapConfig.loadFromFile();

        // Validate level files
        Array<String> missingFiles = config.validateLevelFiles();
        if (missingFiles.size > 0) {
            Gdx.app.log("WorldMap", "Missing level files detected:");
            for (String file : missingFiles) {
                Gdx.app.log("WorldMap", "  - " + file);
            }
            Gdx.app.log("WorldMap", "Consider creating these levels in the level editor (F1 in debug mode)");
        }

        // Load world map texture
        try {
            worldMapTexture = new Texture(config.worldMapTexturePath);
            Gdx.app.log("WorldMap", "Successfully loaded world map texture: " + config.worldMapTexturePath);
        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to load world map texture: " + config.worldMapTexturePath, e);
        }

        // Load player icon
        try {
            if (Gdx.files.internal("mario_sprites/playables/mario/mario_idle.png").exists()) {
                playerIconTexture = new Texture("mario_sprites/playables/mario/mario_idle.png");
            }
        } catch (Exception e) {
            Gdx.app.log("WorldMap", "Could not load player icon, will use simple rendering");
        }

        font = new BitmapFont();
        font.getData().setScale(1.2f);

        // NEW: Smaller font for UI
        smallFont = new BitmapFont();
        smallFont.getData().setScale(0.8f);

        shapeRenderer = new ShapeRenderer();

        nodes = new Array<>();
        paths = new Array<>();

        // Initialize world map progress
        progress = new WorldMapProgress();
        loadProgress();

        // Create the world map from configuration
        createWorldMapFromConfig();
        createPathsFromConfig();

        // Set move speed from config
        moveSpeed = config.playerMoveSpeed;

        // Set initial player position to first unlocked node
        setInitialPlayerPosition();

        // NEW: Initialize level assignment GUI
        initializeLevelAssignmentGUI();
        refreshAvailableLevelFiles();
    }

    private void initializeLevelAssignmentGUI() {
        availableLevelFiles = new Array<>();
        levelFileButtons = new Array<>();

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // FIXED: Scale dialog size based on screen size
        float scaleFactor = Math.min(screenWidth / 1920f, screenHeight / 1080f); // Scale based on common resolution
        scaleFactor = Math.max(scaleFactor, 0.8f); // Minimum scale factor

        float dialogWidth = 600f * scaleFactor; // Larger dialog
        float dialogHeight = 700f * scaleFactor; // Taller dialog

        // Center the dialog
        float dialogX = (screenWidth - dialogWidth) / 2f;
        float dialogY = (screenHeight - dialogHeight) / 2f;

        levelAssignmentDialogBounds = new Rectangle(dialogX, dialogY, dialogWidth, dialogHeight);

        // Buttons at bottom of dialog - scale these too
        float buttonWidth = 120f * scaleFactor;
        float buttonHeight = 40f * scaleFactor;
        float buttonSpacing = 20f * scaleFactor;

        confirmAssignmentButton = new Rectangle(
            dialogX + dialogWidth - buttonWidth * 2 - buttonSpacing - 10f * scaleFactor,
            dialogY + 15f * scaleFactor,
            buttonWidth, buttonHeight);

        cancelAssignmentButton = new Rectangle(
            dialogX + dialogWidth - buttonWidth - 10f * scaleFactor,
            dialogY + 15f * scaleFactor,
            buttonWidth, buttonHeight);

        // Update item height based on scaling
        LEVEL_ASSIGNMENT_ITEM_HEIGHT = 35f * scaleFactor;

        Gdx.app.log("WorldMap", "Initialized level assignment GUI with scale: " + scaleFactor);
    }


    // NEW: Refresh the list of available level files
    private void refreshAvailableLevelFiles() {
        availableLevelFiles.clear();

        // Scan the levels directory for .json files
        FileHandle levelsDir = Gdx.files.local("levels/");
        if (levelsDir.exists() && levelsDir.isDirectory()) {
            FileHandle[] levelFiles = levelsDir.list(".json");
            for (FileHandle file : levelFiles) {
                availableLevelFiles.add(file.name());
            }
        }

        // Add some default/template options
        availableLevelFiles.add("[Create New Level]");
        availableLevelFiles.add("[No Level Assigned]");

        Gdx.app.log("WorldMap", "Found " + (availableLevelFiles.size - 2) + " level files");

        // Recreate level file buttons
        updateLevelFileButtons();
    }

    // ENHANCED: Better button updating with proper coordinates
    private void updateLevelFileButtons() {
        levelFileButtons.clear();

        if (!showLevelAssignmentDialog) return;

        float scaleFactor = Math.min(Gdx.graphics.getWidth() / 1920f, Gdx.graphics.getHeight() / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.8f);

        float listStartY = levelAssignmentDialogBounds.y + levelAssignmentDialogBounds.height - 100f * scaleFactor;
        float listHeight = levelAssignmentDialogBounds.height - 180f * scaleFactor; // More space for header and buttons
        int visibleItems = Math.max(1, (int)(listHeight / LEVEL_ASSIGNMENT_ITEM_HEIGHT));

        int startIndex = (int)(levelAssignmentScrollOffset / LEVEL_ASSIGNMENT_ITEM_HEIGHT);
        int endIndex = Math.min(startIndex + visibleItems, availableLevelFiles.size);

        for (int i = startIndex; i < endIndex; i++) {
            float buttonY = listStartY - (i - startIndex) * LEVEL_ASSIGNMENT_ITEM_HEIGHT;
            Rectangle button = new Rectangle(
                levelAssignmentDialogBounds.x + 10f * scaleFactor,
                buttonY - LEVEL_ASSIGNMENT_ITEM_HEIGHT,
                levelAssignmentDialogBounds.width - 20f * scaleFactor,
                LEVEL_ASSIGNMENT_ITEM_HEIGHT - 2f * scaleFactor
            );
            levelFileButtons.add(button);
        }
    }

    // Add these fields to handle scaling properly
    // NEW: Public methods for edit mode control
    public void toggleEditMode() {
        editMode = !editMode;
        if (editMode) {
            Gdx.app.log("WorldMap", "Edit mode enabled");
        } else {
            Gdx.app.log("WorldMap", "Edit mode disabled");
            selectedNodeForEdit = null;
            showLevelAssignmentDialog = false;
            nodeCreationMode = false;
            // Save configuration when exiting edit mode
            updateConfigFromNodes();
            config.saveToFile();
        }
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        if (!editMode) {
            selectedNodeForEdit = null;
            showLevelAssignmentDialog = false;
            nodeCreationMode = false;
        }
    }

    public boolean isEditMode() {
        return editMode;
    }

    private void createWorldMapFromConfig() {
        nodes.clear();

        for (int i = 0; i < config.nodes.size; i++) {
            WorldMapConfig.NodeConfig nodeConfig = config.nodes.get(i);

            // Convert string to enum
            WorldMapNode.NodeType nodeType;
            try {
                nodeType = WorldMapNode.NodeType.valueOf(nodeConfig.nodeType);
            } catch (IllegalArgumentException e) {
                Gdx.app.error("WorldMap", "Invalid node type: " + nodeConfig.nodeType + ", defaulting to LEVEL");
                nodeType = WorldMapNode.NodeType.LEVEL;
            }

            // Create the node
            WorldMapNode node = new WorldMapNode(
                nodeConfig.x, nodeConfig.y,
                nodeConfig.displayName,
                nodeType,
                nodeConfig.worldNumber,
                nodeConfig.levelNumber
            );

            // Set level file name for loading
            node.setLevelFileName(nodeConfig.levelFileName);

            // Set initial state based on config
            if (nodeConfig.startsUnlocked) {
                node.unlock();
            }

            nodes.add(node);
        }

        // Apply saved progress
        applyProgress();

        Gdx.app.log("WorldMap", "Created " + nodes.size + " nodes from configuration");
    }

    private void createPathsFromConfig() {
        paths.clear();

        for (WorldMapConfig.PathConfig pathConfig : config.paths) {
            if (pathConfig.fromNodeIndex >= 0 && pathConfig.fromNodeIndex < nodes.size &&
                pathConfig.toNodeIndex >= 0 && pathConfig.toNodeIndex < nodes.size) {

                WorldMapNode fromNode = nodes.get(pathConfig.fromNodeIndex);
                WorldMapNode toNode = nodes.get(pathConfig.toNodeIndex);

                Array<Vector2> path = new Array<>();
                Vector2 start = fromNode.getPosition().cpy();
                Vector2 end = toNode.getPosition().cpy();

                // Add start point
                path.add(start);

                // Add intermediate points if any
                for (WorldMapConfig.PathPoint point : pathConfig.intermediatePoints) {
                    path.add(new Vector2(point.x, point.y));
                }

                // Add end point
                path.add(end);

                paths.add(path);
            }
        }

        Gdx.app.log("WorldMap", "Created " + paths.size + " paths from configuration");
    }

    /**
     * Load a level by its file name. This integrates with the level editor system.
     */
    public Level loadLevelByFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            Gdx.app.error("WorldMap", "Invalid level file name");
            return null;
        }

        try {
            FileHandle levelFile = Gdx.files.local("levels/" + fileName);
            if (levelFile.exists()) {
                Json json = new Json();
                Level level = json.fromJson(Level.class, levelFile.readString());
                Gdx.app.log("WorldMap", "Successfully loaded level: " + fileName);
                return level;
            } else {
                Gdx.app.log("WorldMap", "Level file not found: " + fileName + ", creating default level");
                return createDefaultLevelForNode(fileName);
            }
        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to load level: " + fileName, e);
            return createDefaultLevelForNode(fileName);
        }
    }

    /**
     * Create a default level when the JSON file doesn't exist.
     * This ensures the game doesn't crash and gives developers a starting point.
     */
    private Level createDefaultLevelForNode(String fileName) {
        String levelName = fileName.replace(".json", "").replace("_", " ");
        Level level = new Level(levelName);

        // Add basic ground
        Level.PlatformData ground = new Level.PlatformData(
            0, 0, 32f * 20, 32f * 2, Platform.PlatformType.GROUND
        );
        level.addPlatform(ground);

        // Add some basic elements based on file name
        if (fileName.contains("world_1")) {
            addBasicWorld1Elements(level);
        } else if (fileName.contains("world_2")) {
            addBasicWorld2Elements(level);
        } else if (fileName.contains("world_3")) {
            addBasicWorld3Elements(level);
        } else if (fileName.contains("world_4")) {
            addBasicWorld4Elements(level);
        } else if (fileName.contains("castle") || fileName.contains("fortress")) {
            addBasicCastleElements(level);
        } else if (fileName.contains("bonus") || fileName.contains("warp")) {
            addBasicSpecialElements(level);
        }

        // Add goal post
        level.setGoalPost(32f * 18, 32f * 2);

        // Save the default level for future editing
        saveDefaultLevel(level, fileName);

        return level;
    }

    private void addBasicWorld1Elements(Level level) {
        // Simple grass world
        level.addPlatform(new Level.PlatformData(200, 96, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addEnemy(new Level.EnemyData(250, 64, "GOOMBA"));
        level.addPowerup(new Level.PowerupData(300, 160, "MUSHROOM"));
    }

    private void addBasicWorld2Elements(Level level) {
        // Desert world - more challenging
        level.addPlatform(new Level.PlatformData(150, 128, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addEnemy(new Level.EnemyData(200, 96, "KOOPA"));
        level.addEnemy(new Level.EnemyData(350, 64, "GOOMBA"));
    }

    private void addBasicWorld3Elements(Level level) {
        // Water world
        level.addPlatform(new Level.PlatformData(180, 128, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(320, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addEnemy(new Level.EnemyData(220, 160, "GOOMBA"));
        level.addPowerup(new Level.PowerupData(320, 192, "FIRE_FLOWER"));
    }

    private void addBasicWorld4Elements(Level level) {
        // Ice world - most challenging
        level.addPlatform(new Level.PlatformData(160, 160, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(240, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(320, 96, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addEnemy(new Level.EnemyData(200, 192, "KOOPA"));
        level.addEnemy(new Level.EnemyData(360, 128, "KOOPA"));
    }

    private void addBasicCastleElements(Level level) {
        // Castle/fortress level - boss fight style
        level.addPlatform(new Level.PlatformData(200, 96, 128, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(400, 128, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addEnemy(new Level.EnemyData(250, 128, "KOOPA"));
        level.addEnemy(new Level.EnemyData(300, 128, "KOOPA"));
        level.addPowerup(new Level.PowerupData(232, 128, "FIRE_FLOWER"));
    }

    private void addBasicSpecialElements(Level level) {
        // Special level - bonus or warp zone
        level.addPlatform(new Level.PlatformData(150, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(250, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(350, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPowerup(new Level.PowerupData(200, 96, "STAR"));
        level.addPowerup(new Level.PowerupData(300, 96, "CHICKEN"));
    }

    private void saveDefaultLevel(Level level, String fileName) {
        try {
            Json json = new Json();
            FileHandle dir = Gdx.files.local("levels/");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            FileHandle file = Gdx.files.local("levels/" + fileName);
            file.writeString(json.prettyPrint(level), false);
            Gdx.app.log("WorldMap", "Saved default level: " + fileName);
        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to save default level: " + fileName, e);
        }
    }



    public void update(float deltaTime) {
        animationTimer += deltaTime;

        // Update all nodes
        for (WorldMapNode node : nodes) {
            node.update(deltaTime);
        }

        if (debugMode && editMode) {
            // Edit mode input is handled separately with camera
            // This will be called from render method
        } else {
            handleInput();
            updatePlayerMovement(deltaTime);
        }
    }

    public void update(float deltaTime, OrthographicCamera camera) {
        animationTimer += deltaTime;

        // Update all nodes
        for (WorldMapNode node : nodes) {
            node.update(deltaTime);
        }

        if (debugMode && editMode) {
            updateEditMode(deltaTime, camera);
        } else {
            handleInput();
            updatePlayerMovement(deltaTime);
        }
    }

    private void handleInput() {
        if (isMovingToTarget) return; // Don't process input while moving

        // Arrow key navigation
        WorldMapNode newTarget = null;

        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            newTarget = findNextNode(1, 0); // Move right
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            newTarget = findNextNode(-1, 0); // Move left
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            newTarget = findNextNode(0, 1); // Move up
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            newTarget = findNextNode(0, -1); // Move down
        }

        if (newTarget != null && newTarget.isAccessible()) {
            moveToNode(newTarget);
        }

        // Enter level
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (currentSelectedNode != null && currentSelectedNode.isAccessible()) {
                enterLevel(currentSelectedNode);
            }
        }

        // Toggle level info
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            showLevelInfo = !showLevelInfo;
        }
    }
    // File: core/src/main/java/io/github/game/WorldMap.java
// ENHANCED: Much more user-friendly world map editor

    public void updateEditMode(float deltaTime, OrthographicCamera camera) {
        if (!debugMode || !editMode) return;

        animationTimer += deltaTime;

        // Update all nodes
        for (WorldMapNode node : nodes) {
            node.update(deltaTime);
        }

        // ENHANCED: Much more reliable mouse coordinate handling
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        Vector3 worldPos = screenPos.cpy();
        camera.unproject(worldPos);

        boolean isMousePressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean mouseJustPressed = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean mouseJustReleased = wasMousePressed && !isMousePressed;
        boolean rightMouseJustPressed = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);

        // Handle level assignment dialog input first
        if (showLevelAssignmentDialog) {
            handleLevelAssignmentDialogInput(camera);
            wasMousePressed = isMousePressed;
            return;
        }

        // ENHANCED: Much larger and more forgiving hit detection
        if (mouseJustPressed) {
            selectedNodeForEdit = null;
            float bestDistance = Float.MAX_VALUE;

            Gdx.app.log("WorldMap", "Mouse click at world position: (" + (int)worldPos.x + ", " + (int)worldPos.y + ")");

            // ENHANCED: Much more generous hit detection
            for (int i = 0; i < nodes.size; i++) {
                WorldMapNode node = nodes.get(i);
                Vector2 nodePos = node.getPosition();
                float distance = Vector2.dst(worldPos.x, worldPos.y, nodePos.x, nodePos.y);

                // ENHANCED: Much larger hit radius based on zoom level
                float baseRadius = (node.getType() == WorldMapNode.NodeType.CASTLE) ?
                    WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
                float hitRadius = Math.max(baseRadius * 2f, 80f) * camera.zoom; // Scale with zoom

                Gdx.app.log("WorldMap", "Node " + i + " (" + node.getLevelName() + ") at (" +
                    (int)nodePos.x + ", " + (int)nodePos.y + ") distance: " + (int)distance +
                    " hitRadius: " + (int)hitRadius);

                if (distance <= hitRadius && distance < bestDistance) {
                    selectedNodeForEdit = node;
                    bestDistance = distance;
                    Gdx.app.log("WorldMap", "Selected node: " + node.getLevelName());
                }
            }

            if (selectedNodeForEdit != null) {
                Gdx.app.log("WorldMap", "Final selection: " + selectedNodeForEdit.getLevelName() +
                    " at distance: " + (int)bestDistance);

                // Play selection sound for feedback
                SoundManager.getInstance().playJump();
            } else if (nodeCreationMode) {
                createNewNodeAt(worldPos.x, worldPos.y);
            } else {
                Gdx.app.log("WorldMap", "No node selected - try clicking closer to a node");
                Gdx.app.log("WorldMap", "TIP: Enable node creation mode with 'C' to create new nodes");
            }
        }

        // ENHANCED: Better right-click detection for level assignment
        if (rightMouseJustPressed) {
            WorldMapNode rightClickedNode = null;
            float bestDistance = Float.MAX_VALUE;

            for (WorldMapNode node : nodes) {
                Vector2 nodePos = node.getPosition();
                float distance = Vector2.dst(worldPos.x, worldPos.y, nodePos.x, nodePos.y);
                float baseRadius = (node.getType() == WorldMapNode.NodeType.CASTLE) ?
                    WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
                float hitRadius = Math.max(baseRadius * 2f, 80f) * camera.zoom;

                if (distance <= hitRadius && distance < bestDistance) {
                    rightClickedNode = node;
                    bestDistance = distance;
                }
            }

            if (rightClickedNode != null) {
                openLevelAssignmentDialog(rightClickedNode);
                SoundManager.getInstance().playDoor(); // Audio feedback
                Gdx.app.log("WorldMap", "Right-clicked node: " + rightClickedNode.getLevelName());
            }
        }

        // ENHANCED: Smoother dragging with snap-to-grid option
        if (isMousePressed && selectedNodeForEdit != null) {
            float newX = worldPos.x;
            float newY = worldPos.y;

            // ENHANCED: Optional snap-to-grid (hold SHIFT)
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                float gridSize = 32f; // Snap to 32-pixel grid
                newX = Math.round(newX / gridSize) * gridSize;
                newY = Math.round(newY / gridSize) * gridSize;
            }

            selectedNodeForEdit.setPosition(newX, newY);

            // Update player position if this is the current node
            if (selectedNodeForEdit == currentSelectedNode) {
                playerPosition.set(newX, newY);
                targetPosition.set(newX, newY);
            }
        }

        if (mouseJustReleased) {
            if (selectedNodeForEdit != null) {
                Gdx.app.log("WorldMap", "Finished moving " + selectedNodeForEdit.getLevelName() + " to " +
                    "(" + (int)selectedNodeForEdit.getPosition().x + ", " + (int)selectedNodeForEdit.getPosition().y + ")");

                // Update configuration immediately
                updateConfigFromNodes();
                createPathsFromConfig();

                // Auto-save after each move
                config.saveToFile();
                Gdx.app.log("WorldMap", "Auto-saved configuration");
            }
            selectedNodeForEdit = null;
        }

        wasMousePressed = isMousePressed;
        handleEditModeKeyboard();
    }

    // ENHANCED: Much better keyboard handling with more features
    private void handleEditModeKeyboard() {
        // Toggle node creation mode with visual feedback
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            nodeCreationMode = !nodeCreationMode;
            if (nodeCreationMode) {
                SoundManager.getInstance().playItemGet();
            } else {
                SoundManager.getInstance().playJump();
            }
            Gdx.app.log("WorldMap", "Node creation mode: " + (nodeCreationMode ? "ON (Click anywhere to create)" : "OFF"));
        }

        // ENHANCED: Easy node type selection with number keys
        if (nodeCreationMode) {
            WorldMapNode.NodeType oldType = selectedNodeType;

            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                selectedNodeType = WorldMapNode.NodeType.LEVEL;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                selectedNodeType = WorldMapNode.NodeType.CASTLE;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
                selectedNodeType = WorldMapNode.NodeType.FORTRESS;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
                selectedNodeType = WorldMapNode.NodeType.GHOST_HOUSE;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) {
                selectedNodeType = WorldMapNode.NodeType.SPECIAL;
            }

            if (oldType != selectedNodeType) {
                SoundManager.getInstance().playCoinCollect();
                Gdx.app.log("WorldMap", "Node type changed to: " + selectedNodeType);
            }
        }

        // ENHANCED: Better deletion with confirmation
        if (Gdx.input.isKeyJustPressed(Input.Keys.DEL) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (selectedNodeForEdit != null) {
                // ENHANCED: Require holding SHIFT for deletion to prevent accidents
                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                    String nodeName = selectedNodeForEdit.getLevelName();
                    deleteNode(selectedNodeForEdit);
                    SoundManager.getInstance().playEnemyStomp(); // Deletion sound
                    Gdx.app.log("WorldMap", "DELETED node: " + nodeName);
                } else {
                    Gdx.app.log("WorldMap", "Hold SHIFT + DELETE to delete node: " + selectedNodeForEdit.getLevelName());
                }
            } else {
                Gdx.app.log("WorldMap", "No node selected for deletion. Click a node first.");
            }
        }

        // ENHANCED: Copy/duplicate node functionality
        if (Gdx.input.isKeyJustPressed(Input.Keys.V) && selectedNodeForEdit != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                duplicateNode(selectedNodeForEdit);
            }
        }

        // Refresh level files
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            refreshAvailableLevelFiles();
            SoundManager.getInstance().playItemGet();
            Gdx.app.log("WorldMap", "Refreshed level file list (" + availableLevelFiles.size + " files found)");
        }

        // ENHANCED: Quick save and load
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3) ||
            (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && Gdx.input.isKeyJustPressed(Input.Keys.S))) {
            updateConfigFromNodes();
            config.saveToFile();
            SoundManager.getInstance().playDoor();
            Gdx.app.log("WorldMap", "*** World map configuration SAVED! ***");
        }

        // ENHANCED: Auto-backup
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            createBackup();
        }
    }

    // NEW: Duplicate node functionality
    private void duplicateNode(WorldMapNode originalNode) {
        float offsetX = 50f; // Offset the duplicate
        float offsetY = 0f;

        String newName = originalNode.getLevelName() + " Copy";
        WorldMapNode duplicate = new WorldMapNode(
            originalNode.getPosition().x + offsetX,
            originalNode.getPosition().y + offsetY,
            newName,
            originalNode.getType(),
            originalNode.getWorldNumber(),
            originalNode.getLevelNumber() + 100 // Offset level number
        );

        duplicate.setLevelFileName(originalNode.getLevelFileName());
        duplicate.unlock(); // Start unlocked for testing
        nodes.add(duplicate);

        selectedNodeForEdit = duplicate;
        SoundManager.getInstance().playItemGet();
        Gdx.app.log("WorldMap", "Duplicated node: " + newName);
    }

    // NEW: Create backup functionality
    private void createBackup() {
        try {
            updateConfigFromNodes();
            Json json = new Json();
            String timestamp = String.valueOf(System.currentTimeMillis());
            FileHandle backupFile = Gdx.files.local("config/worldmap_backup_" + timestamp + ".json");
            backupFile.writeString(json.prettyPrint(config), false);
            SoundManager.getInstance().playItemGet();
            Gdx.app.log("WorldMap", "Backup created: " + backupFile.name());
        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to create backup", e);
        }
    }


    // NEW: Enhanced edit mode UI rendering
    private void renderEditModeUI(SpriteBatch batch, OrthographicCamera camera) {
        // Draw mouse cursor indicator in world space
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        Vector3 worldPos = screenPos.cpy();
        camera.unproject(worldPos);

        // Draw cursor crosshair
        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // ENHANCED: Visual cursor in world space
        shapeRenderer.setColor(Color.CYAN);
        float crossSize = 20f * camera.zoom;
        shapeRenderer.line(worldPos.x - crossSize, worldPos.y, worldPos.x + crossSize, worldPos.y);
        shapeRenderer.line(worldPos.x, worldPos.y - crossSize, worldPos.x, worldPos.y + crossSize);

        // ENHANCED: Show hit radius for all nodes
        shapeRenderer.setColor(0.5f, 0.5f, 1f, 0.3f);
        for (WorldMapNode node : nodes) {
            Vector2 nodePos = node.getPosition();
            float baseRadius = (node.getType() == WorldMapNode.NodeType.CASTLE) ?
                WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
            float hitRadius = Math.max(baseRadius * 2f, 80f) * camera.zoom;
            shapeRenderer.circle(nodePos.x, nodePos.y, hitRadius, 20);
        }

        // ENHANCED: Highlight selected node more obviously
        if (selectedNodeForEdit != null) {
            shapeRenderer.setColor(Color.YELLOW);
            Gdx.gl.glLineWidth(4f);
            Vector2 pos = selectedNodeForEdit.getPosition();
            float size = (selectedNodeForEdit.getType() == WorldMapNode.NodeType.CASTLE) ?
                WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
            float highlightRadius = size + 15f;
            shapeRenderer.circle(pos.x, pos.y, highlightRadius, 16);

            // Draw selection arrows
            float arrowSize = 20f;
            shapeRenderer.line(pos.x - highlightRadius - arrowSize, pos.y, pos.x - highlightRadius, pos.y);
            shapeRenderer.line(pos.x + highlightRadius, pos.y, pos.x + highlightRadius + arrowSize, pos.y);
            shapeRenderer.line(pos.x, pos.y - highlightRadius - arrowSize, pos.x, pos.y - highlightRadius);
            shapeRenderer.line(pos.x, pos.y + highlightRadius, pos.x, pos.y + highlightRadius + arrowSize);
        }

        // ENHANCED: Show creation preview
        if (nodeCreationMode) {
            shapeRenderer.setColor(Color.GREEN);
            float previewSize = (selectedNodeType == WorldMapNode.NodeType.CASTLE) ?
                WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
            shapeRenderer.circle(worldPos.x, worldPos.y, previewSize, 12);

            // Pulsing effect
            float pulse = 1.0f + 0.3f * (float)Math.sin(animationTimer * 6f);
            shapeRenderer.circle(worldPos.x, worldPos.y, previewSize * pulse, 12);
        }

        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);
        batch.begin();
    }

    // ENHANCED: Much better edit mode instructions in renderUI
    private void renderEditModeInstructions(SpriteBatch batch) {
        if (!editMode) return;

        // ENHANCED: More comprehensive and organized instructions
        String[] basicInstructions = {
            "=== WORLD MAP EDITOR ===",
            "Mouse: Click nodes to select, drag to move",
            "Right-click: Assign level file to node",
            "WASD: Move camera around the map",
            "",
            "=== NODE CREATION ===",
            "C: Toggle creation mode " + (nodeCreationMode ? "[ON]" : "[OFF]"),
            "1-5: Select node type " + (nodeCreationMode ? "[" + selectedNodeType + "]" : ""),
            "Click empty space: Create new node",
            "",
            "=== NODE EDITING ===",
            "SHIFT + Drag: Snap to grid",
            "SHIFT + DEL: Delete selected node",
            "CTRL + V: Duplicate selected node",
            "",
            "=== FILE OPERATIONS ===",
            "F3 / CTRL+S: Save configuration",
            "F4: Create backup",
            "R: Refresh level file list",
            "F2 / ESC: Exit edit mode"
        };

        // Draw with better formatting
        smallFont.setColor(Color.WHITE);
        for (int i = 0; i < basicInstructions.length; i++) {
            String instruction = basicInstructions[i];
            if (instruction.startsWith("===")) {
                font.setColor(Color.CYAN);
                font.draw(batch, instruction, 20, 400 - (i * 18));
            } else if (instruction.isEmpty()) {
                continue; // Skip empty lines
            } else {
                smallFont.setColor(Color.LIGHT_GRAY);
                smallFont.draw(batch, instruction, 20, 400 - (i * 18));
            }
        }

        // Status information
        if (selectedNodeForEdit != null) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "SELECTED: " + selectedNodeForEdit.getLevelName(),
                20, Gdx.graphics.getHeight() - 80);
            smallFont.setColor(Color.WHITE);
            smallFont.draw(batch, "Position: (" + (int)selectedNodeForEdit.getPosition().x +
                ", " + (int)selectedNodeForEdit.getPosition().y + ")", 20, Gdx.graphics.getHeight() - 110);
            if (selectedNodeForEdit.getLevelFileName() != null) {
                smallFont.draw(batch, "Level: " + selectedNodeForEdit.getLevelFileName(),
                    20, Gdx.graphics.getHeight() - 130);
            }
        }
    }

    // FIXED: Enhanced method to get next level with better logic
    public WorldMapNode getNextLevel(WorldMapNode currentNode) {
        if (currentNode == null) return null;

        int currentIndex = nodes.indexOf(currentNode, true);
        if (currentIndex == -1) return null;

        // First try to find via paths (proper world map progression)
        for (WorldMapConfig.PathConfig path : config.paths) {
            if (path.fromNodeIndex == currentIndex &&
                path.toNodeIndex >= 0 && path.toNodeIndex < nodes.size) {
                WorldMapNode nextNode = nodes.get(path.toNodeIndex);
                // Only return if the next node is accessible or can be unlocked
                if (nextNode.isAccessible() || nextNode.getState() == WorldMapNode.NodeState.LOCKED) {
                    return nextNode;
                }
            }
        }

        // Fallback: next in sequence (for linear progression)
        if (currentIndex + 1 < nodes.size) {
            WorldMapNode nextNode = nodes.get(currentIndex + 1);
            if (nextNode.isAccessible() || nextNode.getState() == WorldMapNode.NodeState.LOCKED) {
                return nextNode;
            }
        }

        return null; // No next level
    }


    // FIXED: Method to check if there's a next level available
    public boolean hasNextLevel(WorldMapNode currentNode) {
        WorldMapNode nextLevel = getNextLevel(currentNode);
        return nextLevel != null;
    }

    // FIXED: Enhanced method to advance to next level with proper unlocking
    public boolean advanceToNextLevel() {
        if (currentSelectedNode == null) return false;

        WorldMapNode nextLevel = getNextLevel(currentSelectedNode);
        if (nextLevel != null) {
            // If the next level is locked, unlock it first
            if (nextLevel.getState() == WorldMapNode.NodeState.LOCKED) {
                nextLevel.unlock();
                Gdx.app.log("WorldMap", "Unlocked next level: " + nextLevel.getLevelName());
            }

            // Move to the next level if it's accessible
            if (nextLevel.isAccessible()) {
                moveToNode(nextLevel);
                enterLevel(nextLevel);
                return true;
            }
        }
        return false;
    }

    // FIXED: Enhanced level completion with proper progression
    public void completeLevel(String levelName) {
        Gdx.app.log("WorldMap", "Completing level: " + levelName);

        for (WorldMapNode node : nodes) {
            if (node.getLevelName().equals(levelName)) {
                node.complete();
                Gdx.app.log("WorldMap", "Marked level as completed: " + levelName);

                // Unlock next level(s) based on paths
                unlockNextLevel(node);

                // Save progress after completing level
                saveProgress();
                break;
            }
        }
    }

    // FIXED: Better next level unlocking logic
    private void unlockNextLevel(WorldMapNode completedNode) {
        int completedIndex = nodes.indexOf(completedNode, true);
        boolean unlockedNext = false;

        Gdx.app.log("WorldMap", "Looking for next levels to unlock after completing: " + completedNode.getLevelName());

        // Check paths to find connected nodes
        for (int i = 0; i < config.paths.size; i++) {
            WorldMapConfig.PathConfig path = config.paths.get(i);
            if (path.fromNodeIndex == completedIndex) {
                // This path starts from the completed node, unlock the destination
                if (path.toNodeIndex >= 0 && path.toNodeIndex < nodes.size) {
                    WorldMapNode nextNode = nodes.get(path.toNodeIndex);
                    if (nextNode.getState() == WorldMapNode.NodeState.LOCKED) {
                        nextNode.unlock();
                        unlockedNext = true;
                        Gdx.app.log("WorldMap", "Unlocked next level via path: " + nextNode.getLevelName());
                    }
                }
            }
        }

        // FIXED: Fallback - if no path-based unlocking worked, unlock next node in sequence
        if (!unlockedNext && completedIndex + 1 < nodes.size) {
            WorldMapNode nextNode = nodes.get(completedIndex + 1);
            if (nextNode.getState() == WorldMapNode.NodeState.LOCKED) {
                nextNode.unlock();
                unlockedNext = true;
                Gdx.app.log("WorldMap", "Unlocked next level by sequence: " + nextNode.getLevelName());
            }
        }

        if (!unlockedNext) {
            Gdx.app.log("WorldMap", "No more levels to unlock after: " + completedNode.getLevelName());
        }
    }

    // FIXED: Enhanced method to set the initially selected node
    public void setCurrentSelectedNode(WorldMapNode node) {
        if (currentSelectedNode != null) {
            currentSelectedNode.setSelected(false);
        }

        currentSelectedNode = node;
        if (node != null) {
            node.setSelected(true);
            // Update player position to match selected node
            playerPosition = node.getPosition().cpy();
            targetPosition = playerPosition.cpy();
            playerNode = node;
            isMovingToTarget = false;
        }
    }

    // FIXED: Get level name that works with both display names and file names
    public String getCurrentLevelDisplayName() {
        return currentSelectedNode != null ? currentSelectedNode.getLevelName() : null;
    }

    // FIXED: Better initial player position setting that finds the first accessible node
    private void setInitialPlayerPosition() {
        // Find the first unlocked level and set player there
        WorldMapNode firstUnlocked = null;

        for (WorldMapNode node : nodes) {
            if (node.isAccessible()) {
                firstUnlocked = node;
                break;
            }
        }

        // If no unlocked nodes, unlock the first one
        if (firstUnlocked == null && nodes.size > 0) {
            nodes.get(0).unlock();
            firstUnlocked = nodes.get(0);
            Gdx.app.log("WorldMap", "No unlocked nodes found, unlocked first node: " + firstUnlocked.getLevelName());
        }

        if (firstUnlocked != null) {
            setCurrentSelectedNode(firstUnlocked);
            Gdx.app.log("WorldMap", "Set initial position to: " + firstUnlocked.getLevelName());
        }
    }

    // FIXED: Enhanced method to find accessible nodes in a direction
    private WorldMapNode findNextNode(int dirX, int dirY) {
        if (currentSelectedNode == null) return null;

        Vector2 currentPos = currentSelectedNode.getPosition();
        WorldMapNode closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (WorldMapNode node : nodes) {
            if (node == currentSelectedNode) continue;
            if (!node.isAccessible()) continue; // Only move to accessible nodes

            Vector2 nodePos = node.getPosition();
            Vector2 direction = nodePos.cpy().sub(currentPos);

            // Avoid division by zero
            if (direction.len() == 0) continue;
            direction.nor();

            // Check if this node is in the desired direction
            boolean isInDirection = false;
            float threshold = 0.3f; // Directional threshold

            if (dirX > 0 && direction.x > threshold) isInDirection = true; // Right
            else if (dirX < 0 && direction.x < -threshold) isInDirection = true; // Left
            else if (dirY > 0 && direction.y > threshold) isInDirection = true; // Up
            else if (dirY < 0 && direction.y < -threshold) isInDirection = true; // Down

            if (isInDirection) {
                float distance = currentPos.dst(nodePos);
                if (distance < closestDistance) {
                    closest = node;
                    closestDistance = distance;
                }
            }
        }

        return closest;
    }

    // FIXED: Debug method to log world map state
    public void logWorldMapState() {
        Gdx.app.log("WorldMap", "=== WORLD MAP STATE ===");
        Gdx.app.log("WorldMap", "Total nodes: " + nodes.size);
        Gdx.app.log("WorldMap", "Current selected: " + (currentSelectedNode != null ? currentSelectedNode.getLevelName() : "None"));

        for (int i = 0; i < nodes.size; i++) {
            WorldMapNode node = nodes.get(i);
            String status = "";
            if (node == currentSelectedNode) status += "[CURRENT] ";
            status += node.getLevelName() + " (" + node.getState() + ")";
            if (node.getLevelFileName() != null) {
                status += " -> " + node.getLevelFileName();
            }
            Gdx.app.log("WorldMap", "Node " + i + ": " + status);
        }

        Gdx.app.log("WorldMap", "======================");
    }

    // NEW: Create a new node at the specified position
    private void createNewNodeAt(float x, float y) {
        // Find the next available world/level number
        int worldNum = 1;
        int levelNum = 1;

        // Simple logic: find highest world number and add 1
        for (WorldMapNode node : nodes) {
            if (node.getWorldNumber() >= worldNum) {
                worldNum = node.getWorldNumber();
                if (node.getLevelNumber() >= levelNum) {
                    levelNum = node.getLevelNumber() + 1;
                }
            }
        }

        if (levelNum > 4) { // Assume max 4 levels per world
            worldNum++;
            levelNum = 1;
        }

        String nodeName = worldNum + "-" + levelNum;
        if (selectedNodeType != WorldMapNode.NodeType.LEVEL) {
            nodeName = selectedNodeType.toString() + " " + worldNum;
        }

        WorldMapNode newNode = new WorldMapNode(x, y, nodeName, selectedNodeType, worldNum, levelNum);
        newNode.unlock(); // Start unlocked for testing
        nodes.add(newNode);

        // Add to configuration
        WorldMapConfig.NodeConfig nodeConfig = new WorldMapConfig.NodeConfig(
            x, y, nodeName.toLowerCase().replace(" ", "_") + ".json",
            nodeName, selectedNodeType.toString(), worldNum, levelNum, false);
        config.addNode(nodeConfig);

        selectedNodeForEdit = newNode;
        Gdx.app.log("WorldMap", "Created new node: " + nodeName + " at (" + (int)x + ", " + (int)y + ")");
    }

    // NEW: Delete a node
    private void deleteNode(WorldMapNode node) {
        if (nodes.size <= 1) {
            Gdx.app.log("WorldMap", "Cannot delete last node");
            return;
        }

        int nodeIndex = nodes.indexOf(node, true);
        if (nodeIndex >= 0) {
            nodes.removeIndex(nodeIndex);

            // Remove from configuration
            if (nodeIndex < config.nodes.size) {
                config.nodes.removeIndex(nodeIndex);
            }

            // Remove any paths involving this node
            for (int i = config.paths.size - 1; i >= 0; i--) {
                WorldMapConfig.PathConfig path = config.paths.get(i);
                if (path.fromNodeIndex == nodeIndex || path.toNodeIndex == nodeIndex) {
                    config.paths.removeIndex(i);
                } else {
                    // Adjust indices for remaining paths
                    if (path.fromNodeIndex > nodeIndex) path.fromNodeIndex--;
                    if (path.toNodeIndex > nodeIndex) path.toNodeIndex--;
                }
            }

            createPathsFromConfig();
            selectedNodeForEdit = null;

            Gdx.app.log("WorldMap", "Deleted node: " + node.getLevelName());
        }
    }

    // NEW: Open level assignment dialog
    private void openLevelAssignmentDialog(WorldMapNode node) {
        nodeForLevelAssignment = node;
        showLevelAssignmentDialog = true;
        selectedLevelFileIndex = -1;
        levelAssignmentScrollOffset = 0f;

        // Find current level file in the list
        if (node.getLevelFileName() != null) {
            for (int i = 0; i < availableLevelFiles.size; i++) {
                if (availableLevelFiles.get(i).equals(node.getLevelFileName())) {
                    selectedLevelFileIndex = i;
                    break;
                }
            }
        }

        updateLevelFileButtons();
        Gdx.app.log("WorldMap", "Opened level assignment dialog for: " + node.getLevelName());
    }

    // NEW: Handle input for level assignment dialog
    private void handleLevelAssignmentDialogInput(OrthographicCamera camera) {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        // Don't unproject for UI elements - use screen coordinates

        boolean mouseJustPressed = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        if (mouseJustPressed) {
            // Check if clicking outside dialog to close
            if (!levelAssignmentDialogBounds.contains(screenPos.x, Gdx.graphics.getHeight() - screenPos.y)) {
                showLevelAssignmentDialog = false;
                return;
            }

            // Check level file buttons
            for (int i = 0; i < levelFileButtons.size; i++) {
                Rectangle button = levelFileButtons.get(i);
                if (button.contains(screenPos.x, Gdx.graphics.getHeight() - screenPos.y)) {
                    int startIndex = (int)(levelAssignmentScrollOffset / LEVEL_ASSIGNMENT_ITEM_HEIGHT);
                    selectedLevelFileIndex = startIndex + i;
                    Gdx.app.log("WorldMap", "Selected level file: " + availableLevelFiles.get(selectedLevelFileIndex));
                    break;
                }
            }

            // Check confirm button
            if (confirmAssignmentButton.contains(screenPos.x, Gdx.graphics.getHeight() - screenPos.y)) {
                confirmLevelAssignment();
            }

            // Check cancel button
            if (cancelAssignmentButton.contains(screenPos.x, Gdx.graphics.getHeight() - screenPos.y)) {
                showLevelAssignmentDialog = false;
            }
        }

        // Handle scrolling
        int scrollAmount = Gdx.input.getDeltaY();
        if (scrollAmount != 0) {
            levelAssignmentScrollOffset += scrollAmount * 20f;
            levelAssignmentScrollOffset = Math.max(0f, levelAssignmentScrollOffset);
            float maxScroll = Math.max(0f, availableLevelFiles.size * LEVEL_ASSIGNMENT_ITEM_HEIGHT - (LEVEL_ASSIGNMENT_DIALOG_HEIGHT - 120f));
            levelAssignmentScrollOffset = Math.min(maxScroll, levelAssignmentScrollOffset);
            updateLevelFileButtons();
        }

        // ESC to close dialog
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            showLevelAssignmentDialog = false;
        }
    }

    // NEW: Confirm level assignment
    // NEW: Complete confirmLevelAssignment() method for WorldMap.java
// Replace the existing method with this implementation

    private void confirmLevelAssignment() {
        if (nodeForLevelAssignment == null || selectedLevelFileIndex < 0 || selectedLevelFileIndex >= availableLevelFiles.size) {
            return;
        }

        String selectedFile = availableLevelFiles.get(selectedLevelFileIndex);

        if ("[No Level Assigned]".equals(selectedFile)) {
            nodeForLevelAssignment.setLevelFileName(null);
            Gdx.app.log("WorldMap", "Removed level assignment from: " + nodeForLevelAssignment.getLevelName());
        } else if ("[Create New Level]".equals(selectedFile)) {
            // FULLY IMPLEMENTED: Create new level file and optionally open in editor
            String newFileName = createNewLevelFile(nodeForLevelAssignment);
            if (newFileName != null) {
                nodeForLevelAssignment.setLevelFileName(newFileName);
                Gdx.app.log("WorldMap", "Created and assigned new level file: " + newFileName);

                // Refresh the available level files list to include the new file
                refreshAvailableLevelFiles();

                // Optionally trigger level editor if in debug mode
                if (debugMode) {
                    Gdx.app.log("WorldMap", "New level created. You can edit it using F1 (Level Editor)");
                    // Note: We could add a flag here to automatically switch to level editor
                    // but for now we'll let the user manually switch
                }
            } else {
                Gdx.app.error("WorldMap", "Failed to create new level file");
                return; // Don't close dialog if creation failed
            }
        } else {
            nodeForLevelAssignment.setLevelFileName(selectedFile);
            Gdx.app.log("WorldMap", "Assigned level file '" + selectedFile + "' to node: " + nodeForLevelAssignment.getLevelName());
        }

        // Update configuration
        updateConfigFromNodes();
        config.saveToFile();

        showLevelAssignmentDialog = false;
    }

    // NEW: Method to create a new level file based on node properties
    private String createNewLevelFile(WorldMapNode node) {
        try {
            // Generate appropriate file name
            String baseName = node.getLevelName().toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", ""); // Remove any other special characters
            String fileName = baseName + ".json";

            // Ensure levels directory exists
            FileHandle levelsDir = Gdx.files.local("levels/");
            if (!levelsDir.exists()) {
                levelsDir.mkdirs();
            }

            // Check if file already exists and generate unique name if needed
            FileHandle levelFile = Gdx.files.local("levels/" + fileName);
            int counter = 1;
            while (levelFile.exists()) {
                fileName = baseName + "_" + counter + ".json";
                levelFile = Gdx.files.local("levels/" + fileName);
                counter++;
            }

            // Create level based on node type and world
            Level newLevel = createLevelForNode(node);
            newLevel.setName(node.getLevelName());

            // Set appropriate background based on world number
            setLevelBackground(newLevel, node.getWorldNumber());

            // Set player start position
            newLevel.setPlayerStartX(150f);
            newLevel.setPlayerStartY(Platform.GROUND_TILE_SIZE * 2);

            // Set appropriate music based on world and node type
            setLevelMusic(newLevel, node.getWorldNumber(), node.getType());

            // Save the level
            Json json = new Json();
            levelFile.writeString(json.prettyPrint(newLevel), false);

            Gdx.app.log("WorldMap", "Successfully created new level file: " + fileName);
            return fileName;

        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to create new level file", e);
            return null;
        }
    }

    // NEW: Create level content based on node properties
    private Level createLevelForNode(WorldMapNode node) {
        Level level = new Level(node.getLevelName());

        // Add basic ground platform
        Level.PlatformData ground = new Level.PlatformData(
            0, 0, 32f * 25, 32f * 2, Platform.PlatformType.GROUND
        );
        level.addPlatform(ground);

        // Add content based on node type and world
        switch (node.getType()) {
            case LEVEL:
                createStandardLevel(level, node.getWorldNumber(), node.getLevelNumber());
                break;
            case CASTLE:
                createCastleLevel(level, node.getWorldNumber());
                break;
            case FORTRESS:
                createFortressLevel(level, node.getWorldNumber());
                break;
            case GHOST_HOUSE:
                createGhostHouseLevel(level, node.getWorldNumber());
                break;
            case SPECIAL:
                createSpecialLevel(level, node.getWorldNumber());
                break;
        }

        // Always add a goal post at the end
        level.setGoalPost(32f * 23, 32f * 2);

        return level;
    }

    private void createStandardLevel(Level level, int world, int levelNum) {
        // Base difficulty on world number
        float difficulty = world * 0.5f + levelNum * 0.3f;

        // Add platforms with increasing complexity
        level.addPlatform(new Level.PlatformData(200, 96, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(450, 96, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));

        if (world >= 2) {
            level.addPlatform(new Level.PlatformData(600, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
            level.addPlatform(new Level.PlatformData(700, 128, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));
        }

        // Add enemies based on world
        if (world == 1) {
            level.addEnemy(new Level.EnemyData(250, 64, "GOOMBA"));
            level.addEnemy(new Level.EnemyData(500, 128, "GOOMBA"));
        } else {
            level.addEnemy(new Level.EnemyData(250, 64, "GOOMBA"));
            level.addEnemy(new Level.EnemyData(400, 64, "KOOPA"));
            level.addEnemy(new Level.EnemyData(750, 160, "GOOMBA"));
        }

        // Add powerups
        level.addPowerup(new Level.PowerupData(300, 160, world == 1 ? "MUSHROOM" : "FIRE_FLOWER"));
        if (world >= 3) {
            level.addPowerup(new Level.PowerupData(600, 192, "STAR"));
        }
    }

    private void createCastleLevel(Level level, int world) {
        // Castle levels are more challenging
        level.addPlatform(new Level.PlatformData(200, 96, 128, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(400, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(500, 160, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(650, 96, 64, 64, Platform.PlatformType.GRAVEL_BLOCK));

        // More enemies for castle
        level.addEnemy(new Level.EnemyData(250, 128, "KOOPA"));
        level.addEnemy(new Level.EnemyData(350, 128, "KOOPA"));
        level.addEnemy(new Level.EnemyData(550, 192, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(680, 160, "KOOPA"));

        // Better powerups for castle
        level.addPowerup(new Level.PowerupData(400, 160, "FIRE_FLOWER"));
        level.addPowerup(new Level.PowerupData(232, 160, "STAR"));
    }

    private void createFortressLevel(Level level, int world) {
        // Similar to castle but different layout
        level.addPlatform(new Level.PlatformData(150, 128, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(400, 96, 32, 96, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(550, 128, 128, 32, Platform.PlatformType.GRAVEL_BLOCK));

        level.addEnemy(new Level.EnemyData(180, 160, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(430, 128, "KOOPA"));
        level.addEnemy(new Level.EnemyData(600, 160, "KOOPA"));

        level.addPowerup(new Level.PowerupData(300, 192, "MUSHROOM"));
    }

    private void createGhostHouseLevel(Level level, int world) {
        // Spooky level with floating platforms
        level.addPlatform(new Level.PlatformData(180, 160, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(320, 200, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(450, 140, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(600, 180, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));

        level.addEnemy(new Level.EnemyData(210, 192, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(480, 172, "KOOPA"));
        level.addEnemy(new Level.EnemyData(630, 212, "GOOMBA"));

        level.addPowerup(new Level.PowerupData(320, 232, "STAR"));
    }

    private void createSpecialLevel(Level level, int world) {
        // Bonus/special level with lots of powerups
        level.addPlatform(new Level.PlatformData(150, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(250, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(350, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(450, 160, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(550, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(650, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));

        // Fewer enemies, more powerups
        level.addEnemy(new Level.EnemyData(480, 192, "GOOMBA"));

        level.addPowerup(new Level.PowerupData(200, 96, "CHICKEN"));
        level.addPowerup(new Level.PowerupData(300, 96, "STAR"));
        level.addPowerup(new Level.PowerupData(400, 96, "FIRE_FLOWER"));
        level.addPowerup(new Level.PowerupData(500, 96, "MUSHROOM"));
    }

    private void setLevelBackground(Level level, int world) {
        switch (world) {
            case 1:
                level.setBackgroundTexturePath("mario_sprites/backgrounds/background_0.png"); // Sky/grass
                break;
            case 2:
                level.setBackgroundTexturePath("mario_sprites/backgrounds/background_1.png"); // Desert/hills
                break;
            case 3:
                level.setBackgroundTexturePath("mario_sprites/backgrounds/background_2.png"); // Water/night
                break;
            case 4:
                level.setBackgroundTexturePath("mario_sprites/backgrounds/background_2.png"); // Ice/night
                break;
            default:
                level.setBackgroundTexturePath("mario_sprites/backgrounds/background_0.png");
                break;
        }
    }

    private void setLevelMusic(Level level, int world, WorldMapNode.NodeType nodeType) {
        // Set music based on world and node type
        String musicPath = "music/level1.mp3"; // Default
        float volume = 0.5f;

        switch (nodeType) {
            case CASTLE:
            case FORTRESS:
                musicPath = "music/castle.mp3";
                volume = 0.6f;
                break;
            case GHOST_HOUSE:
                musicPath = "music/ghost_house.mp3";
                volume = 0.4f;
                break;
            case SPECIAL:
                musicPath = "music/bonus.mp3";
                volume = 0.7f;
                break;
            default:
                musicPath = "music/level" + world + ".mp3";
                break;
        }

        level.setBackgroundMusic(musicPath);
        level.setMusicVolume(volume);
    }

    private void updateConfigFromNodes() {
        // Update the configuration with current node positions and level assignments
        config.nodes.clear();

        for (WorldMapNode node : nodes) {
            WorldMapConfig.NodeConfig nodeConfig = new WorldMapConfig.NodeConfig(
                node.getPosition().x, node.getPosition().y,
                node.getLevelFileName() != null ? node.getLevelFileName() : "",
                node.getLevelName(),
                node.getType().toString(),
                node.getWorldNumber(),
                node.getLevelNumber(),
                node.getState() == WorldMapNode.NodeState.UNLOCKED || node.getState() == WorldMapNode.NodeState.COMPLETED
            );
            config.addNode(nodeConfig);
        }

        // Recreate paths with new positions
        createPathsFromConfig();
    }

    private void moveToNode(WorldMapNode target) {
        if (currentSelectedNode != null) {
            currentSelectedNode.setSelected(false);
        }

        currentSelectedNode = target;
        target.setSelected(true);
        targetPosition = target.getPosition().cpy();
        isMovingToTarget = true;

        // Play movement sound
        SoundManager.getInstance().playJump(); // Reuse jump sound for map movement
    }

    private void updatePlayerMovement(float deltaTime) {
        if (isMovingToTarget) {
            Vector2 direction = targetPosition.cpy().sub(playerPosition).nor();
            float distance = playerPosition.dst(targetPosition);

            if (distance < 5f) {
                // Reached target
                playerPosition.set(targetPosition);
                playerNode = currentSelectedNode;
                isMovingToTarget = false;
            } else {
                // Move towards target
                playerPosition.add(direction.scl(moveSpeed * deltaTime));
            }
        }
    }

    private void enterLevel(WorldMapNode node) {
        Gdx.app.log("WorldMap", "Entering level: " + node.getLevelName());

        // Set the level selection mode with the level file name
        levelSelectionMode = true;

        // Play enter sound
        SoundManager.getInstance().playDoor();
    }

    public void render(SpriteBatch batch, OrthographicCamera camera) {
        // If in edit mode, call the special update method with camera
        if (debugMode && editMode) {
            float deltaTime = Gdx.graphics.getDeltaTime();
            updateEditMode(deltaTime, camera);
        }

        // Center camera on player with bounds from config
        if (!editMode) {
            camera.position.x = MathUtils.clamp(playerPosition.x, config.cameraMinX,
                Math.max(config.cameraMinX, config.mapWidth - config.cameraMinX));
            camera.position.y = MathUtils.clamp(playerPosition.y, config.cameraMinY,
                Math.max(config.cameraMinY, config.mapHeight - config.cameraMinY));
        } else {
            // In edit mode, allow free camera movement with mouse wheel or keys
            handleEditModeCameraMovement(camera);
        }
        camera.update();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw world map background
        if (worldMapTexture != null) {
            batch.draw(worldMapTexture, 0, 0, config.mapWidth, config.mapHeight);
        }

        batch.end();

        // Draw paths
        renderPaths(camera);

        batch.begin();

        // Draw nodes
        for (WorldMapNode node : nodes) {
            node.render(batch);
        }

        // Highlight selected node for editing
        if (editMode && selectedNodeForEdit != null) {
            renderEditModeHighlight(batch, selectedNodeForEdit);
        }

        // Draw player icon (only when not in edit mode)
        if (!editMode) {
            renderPlayer(batch);
        }
        if (debugMode && editMode) {
            renderEditModeUI(batch, camera);
        }
        batch.end();

        // Draw UI
        renderUI(batch, camera);

        // NEW: Draw level assignment dialog
        if (showLevelAssignmentDialog) {
            renderLevelAssignmentDialog(batch);
        }
    }
// ADDITIONAL METHODS for WorldMap.java - Add these to the existing WorldMap class


    // FIXED: Get the currently selected node
    public WorldMapNode getCurrentSelectedNode() {
        return currentSelectedNode;
    }

    private void handleEditModeCameraMovement(OrthographicCamera camera) {
        float cameraSpeed = 300f * Gdx.graphics.getDeltaTime();

        // WASD camera movement in edit mode
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += cameraSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= cameraSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= cameraSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += cameraSpeed;
        }

        // Clamp camera to map bounds
        camera.position.x = MathUtils.clamp(camera.position.x, camera.viewportWidth/2,
            config.mapWidth - camera.viewportWidth/2);
        camera.position.y = MathUtils.clamp(camera.position.y, camera.viewportHeight/2,
            config.mapHeight - camera.viewportHeight/2);
    }

    private void renderEditModeHighlight(SpriteBatch batch, WorldMapNode node) {
        // Draw a bright cyan outline around the selected node
        float nodeSize = (node.getType() == WorldMapNode.NodeType.CASTLE) ?
            WorldMapNode.CASTLE_SIZE : WorldMapNode.NODE_SIZE;
        float highlightSize = nodeSize * 1.4f;

        Vector2 pos = node.getPosition();
        float highlightX = pos.x - highlightSize / 2;
        float highlightY = pos.y - highlightSize / 2;

        // Pulsing cyan highlight
        float pulse = 0.7f + 0.3f * (float)Math.sin(animationTimer * 8.0f);
        batch.setColor(0f, 1f, 1f, pulse); // Cyan

        // Draw the node texture again but larger as highlight
        Texture nodeTexture = getNodeTextureForType(node.getType());
        if (nodeTexture != null) {
            batch.draw(nodeTexture, highlightX, highlightY, highlightSize, highlightSize);
        }

        batch.setColor(Color.WHITE);
    }

    private Texture getNodeTextureForType(WorldMapNode.NodeType type) {
        switch (type) {
            case LEVEL:
                return WorldMapNode.getLevelNodeTexture();
            case CASTLE:
                return WorldMapNode.getCastleNodeTexture();
            case FORTRESS:
                return WorldMapNode.getFortressNodeTexture();
            case GHOST_HOUSE:
                return WorldMapNode.getSpookyNodeTexture();
            case SPECIAL:
                return WorldMapNode.getSpecialNodeTexture();
            default:
                return WorldMapNode.getLevelNodeTexture();
        }
    }

    private void renderPaths(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Draw path backgrounds (thicker, darker lines)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(8f);
        shapeRenderer.setColor(0.4f, 0.2f, 0.1f, 0.8f); // Dark brown

        for (Array<Vector2> path : paths) {
            for (int i = 0; i < path.size - 1; i++) {
                Vector2 point1 = path.get(i);
                Vector2 point2 = path.get(i + 1);
                shapeRenderer.line(point1.x, point1.y, point2.x, point2.y);
            }
        }
        shapeRenderer.end();

        // Draw path foregrounds (thinner, lighter lines)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(4f);
        shapeRenderer.setColor(0.8f, 0.6f, 0.3f, 1.0f); // Light brown/tan

        for (Array<Vector2> path : paths) {
            for (int i = 0; i < path.size - 1; i++) {
                Vector2 point1 = path.get(i);
                Vector2 point2 = path.get(i + 1);
                shapeRenderer.line(point1.x, point1.y, point2.x, point2.y);
            }
        }
        shapeRenderer.end();

        // In edit mode, show path connection points
        if (editMode) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.YELLOW);

            for (Array<Vector2> path : paths) {
                for (Vector2 point : path) {
                    shapeRenderer.circle(point.x, point.y, 3f);
                }
            }
            shapeRenderer.end();
        }

        Gdx.gl.glLineWidth(1f); // Reset line width
    }

    private void renderPlayer(SpriteBatch batch) {
        float playerSize = 32f;
        float bounceOffset = 3f * (float)Math.sin(animationTimer * 4f);

        if (playerIconTexture != null) {
            batch.draw(playerIconTexture,
                playerPosition.x - playerSize/2,
                playerPosition.y - playerSize/2 + bounceOffset,
                playerSize, playerSize);
        } else {
            // Fallback: render simple colored rectangle using ShapeRenderer
            batch.end();
            shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.BLUE);
            shapeRenderer.rect(playerPosition.x - playerSize/2,
                playerPosition.y - playerSize/2 + bounceOffset,
                playerSize, playerSize);
            shapeRenderer.end();
            batch.begin();
        }
    }



    // ENHANCED: Much better level assignment dialog rendering with proper scaling
    private void renderLevelAssignmentDialog(SpriteBatch batch) {
        // Get current screen dimensions
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Scale factor for better visibility
        float scaleFactor = Math.min(screenWidth / 1920f, screenHeight / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.8f);

        // Switch to screen coordinates
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);

        // Draw semi-transparent overlay
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.7f);
        shapeRenderer.rect(0, 0, screenWidth, screenHeight);
        shapeRenderer.end();

        // ENHANCED: Much more visible dialog background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.15f, 0.15f, 0.2f, 0.98f); // Darker, more opaque background
        shapeRenderer.rect(levelAssignmentDialogBounds.x, levelAssignmentDialogBounds.y,
            levelAssignmentDialogBounds.width, levelAssignmentDialogBounds.height);
        shapeRenderer.end();

        // ENHANCED: Thicker, more visible border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.CYAN);
        Gdx.gl.glLineWidth(4f * scaleFactor);
        shapeRenderer.rect(levelAssignmentDialogBounds.x, levelAssignmentDialogBounds.y,
            levelAssignmentDialogBounds.width, levelAssignmentDialogBounds.height);
        shapeRenderer.end();

        // ENHANCED: Draw header section
        float headerHeight = 60f * scaleFactor;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.3f, 0.5f, 1.0f); // Blue header
        shapeRenderer.rect(levelAssignmentDialogBounds.x,
            levelAssignmentDialogBounds.y + levelAssignmentDialogBounds.height - headerHeight,
            levelAssignmentDialogBounds.width, headerHeight);
        shapeRenderer.end();

        // Update and draw level file buttons with better visibility
        updateLevelFileButtons();
        for (int i = 0; i < levelFileButtons.size; i++) {
            Rectangle button = levelFileButtons.get(i);
            int startIndex = (int)(levelAssignmentScrollOffset / LEVEL_ASSIGNMENT_ITEM_HEIGHT);
            int fileIndex = startIndex + i;

            if (fileIndex >= availableLevelFiles.size) continue;

            // ENHANCED: Much more visible button backgrounds
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            if (fileIndex == selectedLevelFileIndex) {
                shapeRenderer.setColor(0.3f, 0.7f, 1f, 0.9f); // Bright blue for selected
            } else if (fileIndex % 2 == 0) {
                shapeRenderer.setColor(0.25f, 0.25f, 0.3f, 0.8f); // Alternating colors
            } else {
                shapeRenderer.setColor(0.2f, 0.2f, 0.25f, 0.8f);
            }
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
            shapeRenderer.end();

            // Button border for better visibility
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(fileIndex == selectedLevelFileIndex ? Color.WHITE : Color.GRAY);
            Gdx.gl.glLineWidth(1f);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
            shapeRenderer.end();
        }

        // ENHANCED: More visible confirm/cancel buttons
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.8f, 0.2f, 0.9f); // Bright green for confirm
        shapeRenderer.rect(confirmAssignmentButton.x, confirmAssignmentButton.y,
            confirmAssignmentButton.width, confirmAssignmentButton.height);
        shapeRenderer.setColor(0.8f, 0.2f, 0.2f, 0.9f); // Bright red for cancel
        shapeRenderer.rect(cancelAssignmentButton.x, cancelAssignmentButton.y,
            cancelAssignmentButton.width, cancelAssignmentButton.height);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        Gdx.gl.glLineWidth(2f);
        shapeRenderer.rect(confirmAssignmentButton.x, confirmAssignmentButton.y,
            confirmAssignmentButton.width, confirmAssignmentButton.height);
        shapeRenderer.rect(cancelAssignmentButton.x, cancelAssignmentButton.y,
            cancelAssignmentButton.width, cancelAssignmentButton.height);
        shapeRenderer.end();

        Gdx.gl.glLineWidth(1f);

        // ENHANCED: Much better text rendering with scaling
        batch.begin();

        // Scale fonts for better visibility
        float fontScale = scaleFactor * 1.2f;
        float smallFontScale = scaleFactor;

        font.getData().setScale(fontScale);
        smallFont.getData().setScale(smallFontScale);

        // ENHANCED: More prominent dialog title
        font.setColor(Color.WHITE);
        String title = "ASSIGN LEVEL FILE";
        font.draw(batch, title, levelAssignmentDialogBounds.x + 20f * scaleFactor,
            levelAssignmentDialogBounds.y + levelAssignmentDialogBounds.height - 20f * scaleFactor);

        // Node information
        if (nodeForLevelAssignment != null) {
            smallFont.setColor(Color.YELLOW);
            String nodeInfo = "Node: " + nodeForLevelAssignment.getLevelName();
            smallFont.draw(batch, nodeInfo, levelAssignmentDialogBounds.x + 20f * scaleFactor,
                levelAssignmentDialogBounds.y + levelAssignmentDialogBounds.height - 45f * scaleFactor);

            // Current assignment with better visibility
            if (nodeForLevelAssignment.getLevelFileName() != null) {
                smallFont.setColor(Color.LIGHT_GRAY);
                String currentFile = "Current: " + nodeForLevelAssignment.getLevelFileName();
                smallFont.draw(batch, currentFile, levelAssignmentDialogBounds.x + 20f * scaleFactor,
                    levelAssignmentDialogBounds.y + levelAssignmentDialogBounds.height - 70f * scaleFactor);
            }
        }

        // ENHANCED: Much more visible level file list
        for (int i = 0; i < levelFileButtons.size; i++) {
            Rectangle button = levelFileButtons.get(i);
            int startIndex = (int)(levelAssignmentScrollOffset / LEVEL_ASSIGNMENT_ITEM_HEIGHT);
            int fileIndex = startIndex + i;

            if (fileIndex >= availableLevelFiles.size) continue;

            String fileName = availableLevelFiles.get(fileIndex);

            // ENHANCED: High contrast text colors
            if (fileIndex == selectedLevelFileIndex) {
                smallFont.setColor(Color.BLACK); // Black text on blue background
            } else {
                smallFont.setColor(Color.WHITE); // White text on dark background
            }

            // Add file type indicators
            String displayText = fileName;
            if (fileName.equals("[Create New Level]")) {
                displayText = "★ " + fileName;
                smallFont.setColor(Color.GREEN);
            } else if (fileName.equals("[No Level Assigned]")) {
                displayText = "✗ " + fileName;
                smallFont.setColor(Color.RED);
            } else if (fileName.endsWith(".json")) {
                displayText = "📄 " + fileName;
            }

            smallFont.draw(batch, displayText,
                button.x + 10f * scaleFactor,
                button.y + button.height - 8f * scaleFactor);
        }

        // ENHANCED: Better button labels
        font.setColor(Color.WHITE);
        font.draw(batch, "CONFIRM",
            confirmAssignmentButton.x + confirmAssignmentButton.width/2f - 35f * scaleFactor,
            confirmAssignmentButton.y + confirmAssignmentButton.height/2f + 5f * scaleFactor);
        font.draw(batch, "CANCEL",
            cancelAssignmentButton.x + cancelAssignmentButton.width/2f - 30f * scaleFactor,
            cancelAssignmentButton.y + cancelAssignmentButton.height/2f + 5f * scaleFactor);

        // ENHANCED: Better instructions
        smallFont.setColor(Color.LIGHT_GRAY);
        String instructions = "Click to select • Scroll to see more • ESC to cancel";
        smallFont.draw(batch, instructions,
            levelAssignmentDialogBounds.x + 20f * scaleFactor,
            levelAssignmentDialogBounds.y + 80f * scaleFactor);

        // Show total files found
        String totalFiles = availableLevelFiles.size + " files found";
        smallFont.draw(batch, totalFiles,
            levelAssignmentDialogBounds.x + 20f * scaleFactor,
            levelAssignmentDialogBounds.y + 60f * scaleFactor);

        // Reset font scales
        font.getData().setScale(1.2f);
        smallFont.getData().setScale(0.8f);

        batch.end();
    }

    private void renderUI(SpriteBatch batch, OrthographicCamera camera) {
        // Set up UI projection
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();

        font.setColor(Color.WHITE);

        // Draw current level info (only when not in edit mode)
        if (!editMode && currentSelectedNode != null) {
            String levelInfo = currentSelectedNode.getLevelName();
            String stateInfo = "State: " + currentSelectedNode.getState().toString();

            font.draw(batch, levelInfo, 20, Gdx.graphics.getHeight() - 20);
            font.draw(batch, stateInfo, 20, Gdx.graphics.getHeight() - 50);

            // Show level file name if available
            if (currentSelectedNode.getLevelFileName() != null) {
                font.setColor(Color.LIGHT_GRAY);
                font.draw(batch, "File: " + currentSelectedNode.getLevelFileName(), 20, Gdx.graphics.getHeight() - 80);
                font.setColor(Color.WHITE);
            }
        }

        // Edit mode info
        if (editMode) {
            font.setColor(Color.CYAN);
            font.draw(batch, "WORLD MAP EDIT MODE", 20, Gdx.graphics.getHeight() - 20);
            font.setColor(Color.WHITE);

            if (selectedNodeForEdit != null) {
                font.setColor(Color.YELLOW);
                font.draw(batch, "Selected: " + selectedNodeForEdit.getLevelName(), 20, Gdx.graphics.getHeight() - 50);
                font.draw(batch, "Position: (" + (int)selectedNodeForEdit.getPosition().x +
                    ", " + (int)selectedNodeForEdit.getPosition().y + ")", 20, Gdx.graphics.getHeight() - 80);

                // Show level assignment
                if (selectedNodeForEdit.getLevelFileName() != null) {
                    smallFont.setColor(Color.LIGHT_GRAY);
                    smallFont.draw(batch, "Level File: " + selectedNodeForEdit.getLevelFileName(),
                        20, Gdx.graphics.getHeight() - 110);
                }
                font.setColor(Color.WHITE);
            }

            // Node creation mode indicator
            if (nodeCreationMode) {
                font.setColor(Color.GREEN);
                font.draw(batch, "NODE CREATION MODE - " + selectedNodeType.toString(),
                    20, Gdx.graphics.getHeight() - 140);
                font.setColor(Color.WHITE);
            }

            // Edit mode instructions
            String[] editInstructions = {
                "Click and drag nodes to reposition them",
                "Right-click node to assign level file",
                "WASD: Move camera",
                "C: Toggle node creation mode",
                "1-5: Select node type (in creation mode)",
                "DEL: Delete selected node",
                "R: Refresh level file list",
                "F2: Exit Edit Mode",
                "F3: Save Configuration",
                "ESC: Exit Edit Mode"
            };

            for (int i = 0; i < editInstructions.length; i++) {
                smallFont.draw(batch, editInstructions[i], 20, 250 - (i * 20));
            }
        } else {
            // Normal mode controls
            String[] controls;
            if (debugMode) {
                controls = new String[]{
                    "Arrow Keys: Navigate",
                    "Enter/Space: Enter Level",
                    "I: Toggle Info",
                    "F1: Level Editor",
                    "F2: Edit World Map"
                };
            } else {
                controls = new String[]{
                    "Arrow Keys: Navigate",
                    "Enter/Space: Enter Level",
                    "I: Toggle Info"
                };
            }

            for (int i = 0; i < controls.length; i++) {
                font.draw(batch, controls[i], 20, 150 - (i * 25));
            }
        }

        // Draw additional level info if enabled (and not in edit mode)
        if (!editMode && showLevelInfo && currentSelectedNode != null) {
            String worldInfo = "World " + currentSelectedNode.getWorldNumber() +
                "-" + currentSelectedNode.getLevelNumber();
            String typeInfo = "Type: " + currentSelectedNode.getType().toString();

            font.draw(batch, worldInfo, 20, Gdx.graphics.getHeight() - 110);
            font.draw(batch, typeInfo, 20, Gdx.graphics.getHeight() - 140);
        }

        // Debug info
        if (debugMode) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "DEBUG MODE", Gdx.graphics.getWidth() - 150, Gdx.graphics.getHeight() - 20);
            if (editMode) {
                font.setColor(Color.CYAN);
                font.draw(batch, "EDIT MODE", Gdx.graphics.getWidth() - 150, Gdx.graphics.getHeight() - 50);

                // Show mouse world coordinates
                Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
                camera.unproject(screenPos);
                font.draw(batch, "Mouse: (" + (int)screenPos.x + ", " + (int)screenPos.y + ")",
                    Gdx.graphics.getWidth() - 200, Gdx.graphics.getHeight() - 80);
            }
            font.setColor(Color.WHITE);
        }

        // Configuration status
        if (debugMode) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Nodes: " + nodes.size + " | Paths: " + paths.size + " | Levels: " + availableLevelFiles.size,
                Gdx.graphics.getWidth() - 300, 30);
            font.setColor(Color.WHITE);
        }

        batch.end();
    }



    private void saveProgress() {
        try {
            // Update progress object with current node states
            progress.completedLevels.clear();
            progress.unlockedLevels.clear();

            for (WorldMapNode node : nodes) {
                String nodeId = node.getWorldNumber() + "-" + node.getLevelNumber();
                if (node.getState() == WorldMapNode.NodeState.COMPLETED) {
                    progress.completedLevels.add(nodeId);
                    progress.unlockedLevels.add(nodeId); // Completed nodes are also unlocked
                } else if (node.getState() == WorldMapNode.NodeState.UNLOCKED) {
                    progress.unlockedLevels.add(nodeId);
                }
            }

            Json json = new Json();
            FileHandle file = Gdx.files.local("worldmap_progress.json");
            file.writeString(json.prettyPrint(progress), false);
            Gdx.app.log("WorldMap", "Progress saved");
        } catch (Exception e) {
            Gdx.app.error("WorldMap", "Failed to save progress", e);
        }
    }

    private void loadProgress() {
        try {
            FileHandle file = Gdx.files.local("worldmap_progress.json");
            if (file.exists()) {
                Json json = new Json();
                progress = json.fromJson(WorldMapProgress.class, file.readString());
                Gdx.app.log("WorldMap", "Progress loaded");
            }
        } catch (Exception e) {
            Gdx.app.log("WorldMap", "No existing progress found, starting fresh");
            progress = new WorldMapProgress();
        }
    }

    private void applyProgress() {
        // Apply saved progress to nodes
        for (WorldMapNode node : nodes) {
            String nodeId = node.getWorldNumber() + "-" + node.getLevelNumber();
            if (progress.completedLevels.contains(nodeId, false)) {
                node.complete();
            } else if (progress.unlockedLevels.contains(nodeId, false)) {
                node.unlock();
            }
        }
    }

    // Getters
    public String getCurrentLevelName() {
        return currentSelectedNode != null ? currentSelectedNode.getLevelName() : null;
    }

    public String getCurrentLevelFileName() {
        return currentSelectedNode != null ? currentSelectedNode.getLevelFileName() : null;
    }

    public boolean isLevelSelectionMode() {
        return levelSelectionMode;
    }

    public void setLevelSelectionMode(boolean mode) {
        this.levelSelectionMode = mode;
    }

    public WorldMapConfig getConfig() {
        return config;
    }

    public Array<WorldMapNode> getNodes() {
        return nodes;
    }

    private float getMapWidth() {
        return config.mapWidth;
    }

    private float getMapHeight() {
        return config.mapHeight;
    }

    public void dispose() {
        if (worldMapTexture != null) worldMapTexture.dispose();
        if (playerIconTexture != null) playerIconTexture.dispose();
        if (font != null) font.dispose();
        if (smallFont != null) smallFont.dispose(); // NEW
        if (shapeRenderer != null) shapeRenderer.dispose();
        WorldMapNode.disposeStaticTextures();
    }

    // Inner class for progress tracking
    public static class WorldMapProgress {
        public Array<String> completedLevels = new Array<>();
        public Array<String> unlockedLevels = new Array<>();

        public WorldMapProgress() {
            // Start with first level unlocked
            unlockedLevels.add("1-1");
        }
    }
}

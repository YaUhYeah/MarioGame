// File: core/src/main/java/io/github/game/Level.java
package io.github.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

public class Level implements Json.Serializable {
    public static class PlatformData {
        public float x, y, width, height;
        public Platform.PlatformType type;
        public Powerup.PowerupType containedPowerup;

        public PlatformData() {}

        public PlatformData(float x, float y, float width, float height, Platform.PlatformType type) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.type = type;
            this.containedPowerup = null;
        }
    }

    public static class EnemyData {
        public float x, y;
        public String type; // e.g., "GOOMBA"

        public EnemyData() {} // For JSON deserialization

        public EnemyData(float x, float y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    // NEW: Powerup data class for standalone powerups
    public static class PowerupData {
        public float x, y;
        public String type; // e.g., "MUSHROOM", "FIRE_FLOWER", etc.

        public PowerupData() {} // For JSON deserialization

        public PowerupData(float x, float y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    private String name;
    private Array<PlatformData> platformData;
    private Array<EnemyData> enemyData;
    private Array<PowerupData> powerupData; // NEW: Array for standalone powerups
    private float playerStartX, playerStartY;
    private String backgroundMusic;
    private float musicVolume;
    private String backgroundTexturePath;
    private ObjectMap<String, Powerup.PowerupType> questionBlockContents;

    public Level() {
        this.name = "Untitled Level";
        this.platformData = new Array<>();
        this.enemyData = new Array<>();
        this.powerupData = new Array<>(); // NEW: Initialize powerup data
        this.playerStartX = 150;
        this.playerStartY = Platform.GROUND_TILE_SIZE * 2;
        this.backgroundMusic = "music/level1.mp3";
        this.musicVolume = 0.5f;
        this.backgroundTexturePath = Background.DEFAULT_BACKGROUND_PATH;
        this.questionBlockContents = new ObjectMap<>();
    }

    public Level(String name) {
        this();
        this.name = name;
    }

    // Existing getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Array<PlatformData> getPlatformData() { return platformData; }
    public float getPlayerStartX() { return playerStartX; }
    public void setPlayerStartX(float x) { this.playerStartX = x; }
    public float getPlayerStartY() { return playerStartY; }
    public void setPlayerStartY(float y) { this.playerStartY = y; }
    public String getBackgroundMusic() { return backgroundMusic; }
    public void setBackgroundMusic(String music) { this.backgroundMusic = music; }
    public float getMusicVolume() { return musicVolume; }
    public void setMusicVolume(float volume) { this.musicVolume = volume; }
    public Array<EnemyData> getEnemyData() { return enemyData; }
    public String getBackgroundTexturePath() { return backgroundTexturePath; }
    public void setBackgroundTexturePath(String path) { this.backgroundTexturePath = path; }

    // NEW: Powerup data getters
    public Array<PowerupData> getPowerupData() { return powerupData; }

    public void setQuestionBlockContent(String blockId, Powerup.PowerupType powerup) {
        questionBlockContents.put(blockId, powerup);
    }

    public Powerup.PowerupType getQuestionBlockContent(String blockId) {
        return questionBlockContents.get(blockId);
    }

    // Platform methods
    public void addPlatform(PlatformData data) {
        platformData.add(data);
    }

    public void removePlatform(PlatformData data) {
        platformData.removeValue(data, true);
    }

    public Array<Platform> createPlatforms() {
        Array<Platform> platforms = new Array<>();
        for (PlatformData data : platformData) {
            Platform platform = new Platform(data.x, data.y, data.width, data.height, data.type);
            if (data.containedPowerup != null) {
                platform.setContainedPowerup(data.containedPowerup);
            }
            platforms.add(platform);
        }
        return platforms;
    }

    // Enemy methods
    public void addEnemy(EnemyData data) {
        enemyData.add(data);
    }

    public void removeEnemy(EnemyData data) {
        enemyData.removeValue(data, true);
    }

    // NEW: Powerup methods
    public void addPowerup(PowerupData data) {
        powerupData.add(data);
    }

    public void removePowerup(PowerupData data) {
        powerupData.removeValue(data, true);
    }

    @Override
    public void write(Json json) {
        json.writeValue("name", name);
        json.writeValue("playerStartX", playerStartX);
        json.writeValue("playerStartY", playerStartY);
        json.writeValue("backgroundMusic", backgroundMusic);
        json.writeValue("musicVolume", musicVolume);
        json.writeValue("backgroundTexturePath", backgroundTexturePath);
        json.writeValue("platforms", platformData);
        json.writeValue("enemies", enemyData);
        json.writeValue("powerups", powerupData); // NEW: Serialize powerups
        json.writeValue("questionBlockContents", questionBlockContents);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        name = jsonData.getString("name");
        playerStartX = jsonData.getFloat("playerStartX");
        playerStartY = jsonData.getFloat("playerStartY");
        backgroundMusic = jsonData.getString("backgroundMusic", "music/level1.mp3");
        musicVolume = jsonData.getFloat("musicVolume", 0.5f);
        backgroundTexturePath = jsonData.getString("backgroundTexturePath", Background.DEFAULT_BACKGROUND_PATH);

        platformData.clear();
        JsonValue platformsJson = jsonData.get("platforms");
        if (platformsJson != null) {
            for (JsonValue platformJson : platformsJson) {
                PlatformData data = json.readValue(PlatformData.class, platformJson);
                platformData.add(data);
            }
        }

        enemyData.clear();
        JsonValue enemiesJson = jsonData.get("enemies");
        if (enemiesJson != null) {
            for (JsonValue enemyJson : enemiesJson) {
                EnemyData data = new EnemyData();
                data.x = enemyJson.getFloat("x");
                data.y = enemyJson.getFloat("y");
                data.type = enemyJson.getString("type");
                enemyData.add(data);
            }
        }

        // NEW: Deserialize powerups
        powerupData.clear();
        JsonValue powerupsJson = jsonData.get("powerups");
        if (powerupsJson != null) {
            for (JsonValue powerupJson : powerupsJson) {
                PowerupData data = new PowerupData();
                data.x = powerupJson.getFloat("x");
                data.y = powerupJson.getFloat("y");
                data.type = powerupJson.getString("type");
                powerupData.add(data);
            }
        }

        questionBlockContents.clear();
        JsonValue contentsJson = jsonData.get("questionBlockContents");
        if (contentsJson != null) {
            for (JsonValue.JsonIterator it = contentsJson.iterator(); it.hasNext(); ) {
                JsonValue entry = it.next();
                try {
                    questionBlockContents.put(entry.name, Powerup.PowerupType.valueOf(entry.asString()));
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Unknown PowerupType '" + entry.asString() + "' for question block ID '" + entry.name + "'. Skipping.");
                }
            }
        }
    }
}

// File: core/src/main/java/io/github/game/SoundManager.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

public class SoundManager implements Disposable {
    private static SoundManager instance;

    private Sound jumpSound;
    private Sound coinSound;
    private Sound growSound;
    private Sound itemGetSound;
    private Sound doorSound;
    private Sound powerupSound;
    private Sound playerDeathSound;
    private Sound enemyStompSound;

    private Music currentMusic;
    private String currentMusicPath;

    private float masterVolume = 0.5f; // Keep existing volume controls
    private float musicVolume = 0.5f;  // This seems to be a specific multiplier for music
    private boolean soundEnabled = true;
    private boolean musicEnabled = true;

    private SoundManager() {
        loadSounds();
    }

    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    private void loadSounds() {
        try {
            jumpSound = Gdx.audio.newSound(Gdx.files.internal("sounds/jump.wav"));
            coinSound = Gdx.audio.newSound(Gdx.files.internal("sounds/coin.wav"));

            if (Gdx.files.internal("sounds/grow.wav").exists()) {
                growSound = Gdx.audio.newSound(Gdx.files.internal("sounds/grow.wav"));
            }
            if (Gdx.files.internal("sounds/item-get.wav").exists()) {
                itemGetSound = Gdx.audio.newSound(Gdx.files.internal("sounds/item-get.wav"));
            }
            if (Gdx.files.internal("sounds/door.wav").exists()) {
                doorSound = Gdx.audio.newSound(Gdx.files.internal("sounds/door.wav"));
            }
            if (Gdx.files.internal("sounds/powerup.wav").exists()) {
                powerupSound = Gdx.audio.newSound(Gdx.files.internal("sounds/powerup.wav"));
            }
            if (Gdx.files.internal("sounds/player_death.wav").exists()) {
                playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("sounds/player_death.wav"));
            } else { Gdx.app.log("SoundManager", "player_death.wav not found."); }

            if (Gdx.files.internal("sounds/enemy_stomp.wav").exists()) {
                enemyStompSound = Gdx.audio.newSound(Gdx.files.internal("sounds/enemy_stomp.wav"));
            } else { Gdx.app.log("SoundManager", "enemy_stomp.wav not found."); }

        } catch (Exception e) {
            System.err.println("Error loading sounds: " + e.getMessage());
            // Consider setting soundEnabled = false here if critical sounds fail
        }
    }

    public void playMusic(String musicPath, float volume) {
        if (!musicEnabled || musicPath == null || musicPath.isEmpty()) return;

        if (currentMusic != null) {
            if (currentMusicPath != null && currentMusicPath.equals(musicPath) && currentMusic.isPlaying()) {
                currentMusic.setVolume(volume * this.musicVolume); // Adjust volume if same music
                return;
            }
            currentMusic.stop();
            currentMusic.dispose(); // Dispose old music before loading new
            currentMusic = null;
            currentMusicPath = null;
        }

        try {
            if (Gdx.files.internal(musicPath).exists()) {
                currentMusic = Gdx.audio.newMusic(Gdx.files.internal(musicPath));
                currentMusicPath = musicPath;
                currentMusic.setLooping(true);
                currentMusic.setVolume(volume * this.musicVolume); // Use the passed volume parameter
                currentMusic.play();
            } else {
                Gdx.app.error("SoundManager", "Music file not found: " + musicPath);
            }
        } catch (Exception e) {
            Gdx.app.error("SoundManager", "Error loading music: " + musicPath, e);
            if (currentMusic != null) currentMusic.dispose(); // Clean up if load failed
            currentMusic = null;
            currentMusicPath = null;
        }
    }

    // Method to play player death sound
    public void playPlayerDeath() {
        if (soundEnabled && playerDeathSound != null) {
            playerDeathSound.play(masterVolume);
        }
    }

    // Method to play enemy stomp sound
    public void playEnemyStomp() {
        if (soundEnabled && enemyStompSound != null) {
            enemyStompSound.play(masterVolume);
        }
    }


    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
        }
    }

    public void pauseMusic() {
        if (currentMusic != null) {
            currentMusic.pause();
        }
    }

    public void resumeMusic() {
        if (currentMusic != null && musicEnabled && !currentMusic.isPlaying()) {
            currentMusic.play();
        }
    }

    public void playJump() {
        if (soundEnabled && jumpSound != null) {
            jumpSound.play(masterVolume);
        }
    }

    public void playCoinCollect() {
        if (soundEnabled && coinSound != null) {
            coinSound.play(masterVolume * 0.8f);
        }
    }

    public void playPowerup() {
        if (soundEnabled) {
            if (powerupSound != null) {
                powerupSound.play(masterVolume);
            } else if (itemGetSound != null) { // Fallback
                itemGetSound.play(masterVolume);
            }
        }
    }

    public void playGrow() {
        if (soundEnabled && growSound != null) {
            growSound.play(masterVolume);
        }
    }

    public void playItemGet() {
        if (soundEnabled && itemGetSound != null) {
            itemGetSound.play(masterVolume);
        }
    }

    public void playDoor() {
        if (soundEnabled && doorSound != null) {
            doorSound.play(masterVolume * 0.7f);
        }
    }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
        // Note: This does not retroactively change playing sound instances' volumes, only future ones.
        // LibGDX Sound instances don't typically have individual volume controls after play() without more complex pooling.
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0f, Math.min(1f, volume));
        if (currentMusic != null) {
            // This requires the 'volume' parameter in playMusic to be the level-specific volume
            // and this.musicVolume to be the global music volume setting.
            // For playMusic(String musicPath, float levelVolume), it would be:
            // currentMusic.setVolume(levelVolume * this.musicVolume);
            currentMusic.setVolume(currentMusic.getVolume() / (this.musicVolume == 0 ? 1 : this.musicVolume) * volume); // Re-calculate based on new global music volume
        }
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setMusicEnabled(boolean enabled) {
        this.musicEnabled = enabled;
        if (!enabled && currentMusic != null) {
            currentMusic.stop();
        } else if (enabled && currentMusic != null && !currentMusic.isPlaying()) {
            // Only play if it was previously stopped due to being disabled, and path is still valid
            if (currentMusicPath != null && Gdx.files.internal(currentMusicPath).exists()){
                currentMusic.play();
            }
        }
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    @Override
    public void dispose() {
        if (jumpSound != null) jumpSound.dispose();
        if (coinSound != null) coinSound.dispose();
        if (growSound != null) growSound.dispose();
        if (itemGetSound != null) itemGetSound.dispose();
        if (doorSound != null) doorSound.dispose();
        if (powerupSound != null) powerupSound.dispose();
        if (playerDeathSound != null) playerDeathSound.dispose();
        if (enemyStompSound != null) enemyStompSound.dispose();

        if (currentMusic != null) {
            currentMusic.dispose();
            currentMusic = null;
        }
        currentMusicPath = null;
        instance = null; // Allow re-creation if needed
    }
}

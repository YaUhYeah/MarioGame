package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

/**
 * Manages all sound effects and music in the game
 */
public class SoundManager implements Disposable {
    private static SoundManager instance;

    private Sound jumpSound;
    private Sound coinSound;
    private Sound growSound;
    private Sound itemGetSound;
    private Sound doorSound;
    private Sound powerupSound;

    private Music currentMusic;
    private String currentMusicPath;

    private Sound playerDeathSound; // ADDED
    private Sound enemyStompSound;  // ADDED
    private float masterVolume = 0.5f;
    private float musicVolume = 0.5f;
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

            // Load other sounds if they exist
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
            }if (Gdx.files.internal("sounds/player_death.wav").exists()) {
                playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("sounds/player_death.wav"));
            } else { Gdx.app.log("SoundManager", "player_death.wav not found."); }
            if (Gdx.files.internal("sounds/enemy_stomp.wav").exists()) {
                enemyStompSound = Gdx.audio.newSound(Gdx.files.internal("sounds/enemy_stomp.wav"));
            } else { Gdx.app.log("SoundManager", "enemy_stomp.wav not found."); }


        } catch (Exception e) {
            System.err.println("Error loading sounds: " + e.getMessage());
            soundEnabled = false;
        }
    }

    public void playMusic(String musicPath, float volume) {
        if (!musicEnabled) return;

        // Stop current music if playing
        if (currentMusic != null && currentMusic.isPlaying()) {
            currentMusic.stop();
        }

        // Don't reload if it's the same music
        if (musicPath != null && !musicPath.equals(currentMusicPath)) {
            try {
                if (currentMusic != null) {
                    currentMusic.dispose();
                }

                if (Gdx.files.internal(musicPath).exists()) {
                    currentMusic = Gdx.audio.newMusic(Gdx.files.internal(musicPath));
                    currentMusicPath = musicPath;
                    currentMusic.setLooping(true);
                    currentMusic.setVolume(volume * musicVolume);
                    currentMusic.play();
                } else {
                    System.err.println("Music file not found: " + musicPath);
                }
            } catch (Exception e) {
                System.err.println("Error loading music: " + e.getMessage());
                musicEnabled = false;
            }
        } else if (currentMusic != null) {
            currentMusic.setVolume(volume * musicVolume);
            if (!currentMusic.isPlaying()) {
                currentMusic.play();
            }
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
        if (currentMusic != null && musicEnabled) {
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
            } else if (itemGetSound != null) {
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
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0f, Math.min(1f, volume));
        if (currentMusic != null) {
            currentMusic.setVolume(musicVolume);
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
        } else if (enabled && currentMusic != null) {
            currentMusic.play();
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
        if (currentMusic != null) currentMusic.dispose();
        instance = null;
    }
}

package wonder.shaderdisplay.serial;

import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import wonder.shaderdisplay.Time;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

class AudioInputStream {

    private static final long AUDIO_VISUAL_MAX_DRIFT_MICROSECONDS = (long)(.1f * 1e6);

    private final Logger logger;

    private final File audioFile;
    private Clip clip;
    private boolean startedPlaying = false;

    public AudioInputStream(File audioFile) {
        this.logger = new SimpleLogger(audioFile.getName(), Logger.LEVEL_DEBUG);
        this.audioFile = audioFile;
    }

    public void startReading() {
        try (javax.sound.sampled.AudioInputStream is = AudioSystem.getAudioInputStream(audioFile)) {
            clip = AudioSystem.getClip();
            clip.open(is);
            clip.setLoopPoints(0, -1);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public void startPlaying() {
        clip.start();
        startedPlaying = true;
    }

    public boolean hasStarted() {
        return startedPlaying;
    }

    private void setVolume(float volume) {
        ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(20f * (float) Math.log10(volume));
    }

    public void close() {
        clip.close();
    }

    public void update() {
        long currentTimeMicro = getWindedCurrentTimeMicro();

        if (clip.isRunning() && Time.isPaused()) {
            clip.stop();
        } else if (!clip.isRunning() && !Time.isPaused()) {
            clip.setMicrosecondPosition(currentTimeMicro);
            clip.start();
        }

        long microsecondDeltaToVisual = currentTimeMicro - clip.getMicrosecondPosition();
        if (clip.isRunning() && Math.abs(microsecondDeltaToVisual) > AUDIO_VISUAL_MAX_DRIFT_MICROSECONDS) {
            logger.debug(String.format("Audio jump: %+.2fms", microsecondDeltaToVisual/1000.f));
            clip.setMicrosecondPosition(currentTimeMicro);
        }
    }

    private long getWindedCurrentTimeMicro() {
        long currentTimeMicro = (long) (Time.getTime() * 1e6);
        currentTimeMicro %= clip.getMicrosecondLength();
        if (currentTimeMicro < 0)
            currentTimeMicro += clip.getMicrosecondLength();
        return currentTimeMicro;
    }
}

package wonder.shaderdisplay.serial;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InputFiles {

    public static InputFiles singleton;

    private final List<InputImageStream> streams = new ArrayList<>();
    private AudioInputStream audioStream;
    private boolean hasInputVideo;
    private float videoFramerate;

    public InputFiles(File[] files, boolean useVideoFramerate) throws BadInitException {
        for (File file : files) {
            String extension = FilesUtils.getFileExtension(file);

            if (!file.isFile() || !file.exists())
                throw new BadInitException("Input file '" + file + "' does not exist");

            switch (extension) {
                case "mp4", "webm", "avi" -> {
                    Main.logger.info("Trying to read '" + file + "' as a video stream");
                    VideoStream stream = new VideoStream(file);
                    streams.add(stream);
                    float framerate = stream.getFramerate();
                    if (useVideoFramerate && videoFramerate != 0 && videoFramerate != framerate)
                        Main.logger.warn("Two different input videos have different frame rates");
                    this.hasInputVideo = true;
                    this.videoFramerate = framerate;
                }
                case "wav" -> {
                    Main.logger.info("Trying to read '" + "' as an audio stream");
                    if (audioStream != null) {
                        Main.logger.err("Cannot have two audio files at the same time");
                        continue;
                    }
                    audioStream = new AudioInputStream(file);
                }
                default -> {
                    Main.logger.info("Trying to read '" + file + "' as an image file");
                    FixedImageStream stream = new FixedImageStream(file);
                    streams.add(stream);
                }
            }
        }
    }

    public int[] getFirstInputFileResolution() throws BadInitException {
        return streams.isEmpty() ? null : streams.get(0).getImageResolution();
    }

    public void startReadingFiles() throws BadInitException {
        for (InputImageStream stream : streams)
            stream.startReading();
        if (audioStream != null)
            audioStream.startReading();
    }

    public Texture getInputTexture(int inputTextureSlot) {
        return streams.size() <= inputTextureSlot
                ? Texture.getMissingTexture()
                : streams.get(inputTextureSlot).getTexture();
    }

    public boolean hasInputVideo() {
        return hasInputVideo;
    }

    public float getCommonVideoFramerate() {
        return videoFramerate;
    }

    public void dispose() {
        for (InputImageStream stream : streams)
            stream.close();
        if (audioStream != null)
            audioStream.close();
    }

    public void updateAudio() {
        if (audioStream != null) {
            if (!audioStream.hasStarted())
                audioStream.startPlaying();
            audioStream.update();
        }
    }
}


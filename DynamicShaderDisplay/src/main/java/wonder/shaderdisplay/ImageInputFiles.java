package wonder.shaderdisplay;

import fr.wonder.commons.files.FilesUtils;
import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import wonder.shaderdisplay.display.PixelBuffer;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ImageInputFiles {

    public static ImageInputFiles singleton;

    private final File[] inputFiles;
    private final InputImageStream[] streams;

    public ImageInputFiles(File[] files) {
        this.inputFiles = files;
        this.streams = new InputImageStream[files.length];
    }

    public void startReadingFiles() throws BadInitException {
        for (int i = 0; i < inputFiles.length; i++) {
            File file = inputFiles[i];
            String extension = FilesUtils.getFileExtension(file);

            if (!file.isFile() || !file.exists())
                throw new BadInitException("Input file '" + file + "' does not exist");

            switch (extension) {
                case "mp4", "avi", "webm" -> {
                    Main.logger.debug("Trying to read '" + file + "' as a video stream");
                    streams[i] = new VideoStream(file);
                }
                default -> {
                    Main.logger.debug("Trying to read '" + file + "' as an image file");
                    streams[i] = new FixedImageStream(file);
                }
            }
        }

        for (InputImageStream stream : streams)
            stream.open();
    }

    public Texture getInputTexture(int inputTextureSlot) {
        return streams.length <= inputTextureSlot
                ? Texture.getMissingTexture()
                : streams[inputTextureSlot].getTexture();
    }

}

interface InputImageStream {

    void open() throws BadInitException;
    void close();
    Texture getTexture();

}

class FixedImageStream implements InputImageStream {

    private final File file;
    private Texture texture;

    public FixedImageStream(File textureFile) {
        this.file = textureFile;
    }

    @Override
    public void open() throws BadInitException {
        this.texture = Texture.loadTexture(file);
        if (texture == Texture.getMissingTexture())
            throw new BadInitException("Could not load texture from '" + file.getAbsolutePath() + "'");
    }

    @Override
    public void close() {
        texture.dispose();
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

}

class VideoStream implements InputImageStream {

    final File videoFile;

    Texture currentFrame;
    PixelBuffer pbo;

    static final int CONCURRENT_LOADED_FRAMES = 10;
    List<int[]> loadedFrames = new ArrayList<>();
    int loadedFramesOrigin = 0;
    int videoFrameDuration;

    Demuxer demuxer;
    MediaPicture workingPicture;
    BufferedImage workingImage;
    MediaPacket workingPacket;
    int videoStreamId = -1;
    Decoder videoDecoder;
    MediaPictureConverter pictureConverter;

    public VideoStream(File videoFile) {
        this.videoFile = videoFile;
    }

    @Override
    public void open() throws BadInitException {
        demuxer = Demuxer.make();
        workingPacket = MediaPacket.make();

        try {
            demuxer.open(videoFile.getAbsolutePath(), null, false, true, null, null);

            int numStreams = demuxer.getNumStreams();
            for(int i = 0; i < numStreams; i++) {
                Decoder decoder = demuxer.getStream(i).getDecoder();
                if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
                    videoStreamId = i;
                    videoDecoder = decoder;
                    break;
                }
            }
        } catch (InterruptedException | IOException e) {
            throw new BadInitException("Could not open a video stream for '" + videoFile.getAbsolutePath() + "'", e);
        }

        if (videoStreamId == -1)
            throw new BadInitException("Could not find video stream in container for '" + videoFile.getAbsolutePath() + "'");

        videoDecoder.open(null, null);

        int w = videoDecoder.getWidth(), h = videoDecoder.getHeight();
        videoFrameDuration = videoDecoder.getFrameCount();
        workingPicture = MediaPicture.make(w, h, videoDecoder.getPixelFormat());
        pictureConverter = MediaPictureConverterFactory.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24, workingPicture);
        pbo = new PixelBuffer(w * h * 4);
        currentFrame = new Texture(w, h, Texture.InternalTextureFormat.RGBA8);

        new Thread(this::videoStreamingLoop, "VideoStreaming").start();
    }

    private void videoStreamingLoop() {
        int currentFrame = 0;

        try {
            while (demuxer.read(workingPacket) >= 0) {
                if (workingPacket.getStreamIndex() == videoStreamId) {
                    int offset = 0;
                    int bytesRead = 0;
                    do {
                        bytesRead += videoDecoder.decode(workingPicture, workingPacket, offset);
                        if (loadFrame(currentFrame, workingPicture))
                            currentFrame++;
                        offset += bytesRead;
                    } while (offset < workingPacket.getSize());
                }
            }

            while (true) {
                videoDecoder.decode(workingPicture, null, 0);
                if (!workingPicture.isComplete())
                    break;
                if (loadFrame(currentFrame, workingPicture))
                    currentFrame++;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean loadFrame(int frame, MediaPicture picture) {
        if (!picture.isComplete())
            return false;
        workingImage = pictureConverter.toImage(workingImage, workingPicture);
        int[] pixelData = Texture.loadTextureData(workingImage, false);

        synchronized (this) {
            if (loadedFrames.size() >= CONCURRENT_LOADED_FRAMES) {
                try {
                    this.wait();
                } catch (InterruptedException x) {
                }
            }
            int expectedFrame = loadedFramesOrigin + loadedFrames.size();
            if (expectedFrame != frame) {
                System.err.println("Frame error, loaded " + frame + " but queue was waiting for " + expectedFrame);
                return false;
            }
            loadedFrames.add(pixelData);
            if (loadedFrames.size() == 1)
                this.notify();
            System.out.println("Loaded frame " + frame + ", buffer has " + loadedFrames.size() + " from " + loadedFramesOrigin);
        }

        return true;
    }

    @Override
    public void close() {
        try {
            demuxer.close();
        } catch (InterruptedException | IOException e) {
            Main.logger.err("Failed to close a video stream for '" + videoFile.getAbsolutePath() + "'?");
        }
    }

    @Override
    public Texture getTexture() {
        pbo.copyToTexture(currentFrame);
        loadNextFrameIntoPBO();
        return currentFrame;
    }

    private void loadNextFrameIntoPBO() {
        ByteBuffer buf = pbo.map();
        synchronized (this) {
            if (loadedFrames.isEmpty()) {
                System.out.println("Frame throttling, empty buffer");
                try {
                    this.wait();
                } catch (InterruptedException x) {
                }
            }
            if (!loadedFrames.isEmpty())
                buf.asIntBuffer().put(loadedFrames.remove(0));
            loadedFramesOrigin++;
            if (loadedFrames.size() < CONCURRENT_LOADED_FRAMES)
                this.notify();
        }
        pbo.unmap();
    }
}

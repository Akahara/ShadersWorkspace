package wonder.shaderdisplay;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
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
    private final boolean useVideoFramerate;
    private boolean hasInputVideo;
    private float videoFramerate;

    public ImageInputFiles(File[] files, boolean useVideoFramerate) {
        this.inputFiles = files;
        this.streams = new InputImageStream[files.length];
        this.useVideoFramerate = useVideoFramerate;
    }

    public void startReadingFiles() throws BadInitException {
        for (int i = 0; i < inputFiles.length; i++) {
            File file = inputFiles[i];
            String extension = FilesUtils.getFileExtension(file);

            if (!file.isFile() || !file.exists())
                throw new BadInitException("Input file '" + file + "' does not exist");

            switch (extension) {
                case "mp4", "webm", "avi" -> {
                    Main.logger.debug("Trying to read '" + file + "' as a video stream");
                    VideoStream stream = new VideoStream(file);
                    stream.startReadingVideo();
                    streams[i] = stream;
                    float framerate = stream.getFramerate();
                    if (useVideoFramerate && videoFramerate != 0 && videoFramerate != framerate)
                        Main.logger.warn("Two different input videos have different frame rates");
                    this.hasInputVideo = true;
                    this.videoFramerate = framerate;
                }
                default -> {
                    Main.logger.debug("Trying to read '" + file + "' as an image file");
                    FixedImageStream stream = new FixedImageStream(file);
                    stream.readImage();
                    streams[i] = stream;
                }
            }
        }
    }

    public Texture getInputTexture(int inputTextureSlot) {
        return streams.length <= inputTextureSlot
                ? Texture.getMissingTexture()
                : streams[inputTextureSlot].getTexture();
    }

    public boolean hasInputVideo() {
        return hasInputVideo;
    }

    public float getCommonVideoFramerate() {
        return videoFramerate;
    }
}

interface InputImageStream {

    void close();
    Texture getTexture();

}

class FixedImageStream implements InputImageStream {

    private final File file;
    private Texture texture;

    public FixedImageStream(File textureFile) {
        this.file = textureFile;
    }

    public void readImage() throws BadInitException {
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

    static final int LEVEL_VDEBUG = Logger.LEVEL_DEBUG-100;

    final File videoFile;
    final Logger logger;

    Texture currentFrame;
    PixelBuffer pbo;

    static class LoadedFrame {
        int frameNum;
        int[] data;
    }

    static final int CONCURRENT_LOADED_FRAMES = 10;
    List<LoadedFrame> loadedFrames = new ArrayList<>();
    int pboStoredFrame = -1;
    int textureStoredFrame = -1;
    int nextFrameSeekInVideoStream = -1;

    Demuxer demuxer;
    MediaPicture workingPicture;
    BufferedImage workingImage;
    MediaPacket workingPacket;
    int videoStreamId = -1;
    float videoFramerate;
    int videoFrameDuration;
    double presentationTimeToFrames;
    Decoder videoDecoder;
    MediaPictureConverter pictureConverter;

    public VideoStream(File videoFile) {
        this.videoFile = videoFile;
        this.logger = new SimpleLogger(videoFile.getName(), Logger.LEVEL_ERROR);
    }

    public void startReadingVideo() throws BadInitException {
        demuxer = Demuxer.make();
        workingPacket = MediaPacket.make();

        try {
            demuxer.open(videoFile.getAbsolutePath(), null, false, true, null, null);

            int numStreams = demuxer.getNumStreams();
            for(int i = 0; i < numStreams; i++) {
                DemuxerStream stream = demuxer.getStream(i);
                Decoder decoder = stream.getDecoder();
                if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
                    Rational streamFrameRate = stream.getFrameRate();
                    Rational streamTimeBase = stream.getTimeBase();

                    presentationTimeToFrames = streamTimeBase.multiply(streamFrameRate).getDouble();
                    videoFramerate = (float) stream.getFrameRate().getValue();
                    videoFrameDuration = (int) (stream.getDuration() * presentationTimeToFrames);
                    if (stream.getDuration() == Global.NO_PTS)
                        videoFrameDuration = (int) (streamTimeBase.getValue() * demuxer.getDuration() * videoFramerate / 1000);
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
        workingPicture = MediaPicture.make(w, h, videoDecoder.getPixelFormat());
        pictureConverter = MediaPictureConverterFactory.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24, workingPicture);
        pbo = new PixelBuffer(w * h * 4);
        currentFrame = new Texture(w, h, Texture.InternalTextureFormat.RGBA8);

        new Thread(this::videoStreamingLoop, "VideoStreaming").start();
    }

    private void videoStreamingLoop() {
        try {
            while (true) {
                int seekFrame = -1;
                synchronized (this) {
                    if (nextFrameSeekInVideoStream >= 0) {
                        seekFrame = nextFrameSeekInVideoStream;
                        nextFrameSeekInVideoStream = -1;
                    }
                }

                if (seekFrame >= 0) {
                    logger.debug("Jumping in video stream to frame " + seekFrame);
                    offset = bytesRead = 0;
                    continueReading = false;
                    if (!doSeek(seekFrame))
                        doSeek(0);
                }

                if (!readVideoStreamUntilNextFrame()) {
                    // Loop arround to frame 0
                    doSeek(0);
                }
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean doSeek(int upToFrame) throws IOException, InterruptedException {
        int r = demuxer.seek(videoStreamId, 0, upToFrame, upToFrame, Demuxer.SeekFlag.SEEK_FRAME.swigValue());
        if (r != 0) logger.warn("Could not seek?");
        videoDecoder.flush();

        long upToPoints = (long) (upToFrame / presentationTimeToFrames);

        while (demuxer.read(workingPacket) >= 0) {
            if (workingPacket.getStreamIndex() == videoStreamId) {
                continueReading = true;
                offset = bytesRead = 0;
                do {
                    bytesRead += videoDecoder.decode(workingPicture, workingPacket, offset);
                    offset += bytesRead;
                    long picturePts = workingPicture.getPts();
                    if (!workingPicture.isComplete())
                        continue;

                    logger.log("Skipping frame " + upToPoints + " / " + picturePts, LEVEL_VDEBUG);
                    if (picturePts >= upToPoints)
                        return loadFrame(workingPicture);
                } while (offset < workingPacket.getSize());
                continueReading = false;
            }
        }

        while (true) {
            videoDecoder.decode(workingPicture, null, 0);
            if (!workingPicture.isComplete())
                break;
            long picturePts = workingPicture.getPts();
            logger.log("Skipping late frame " + upToPoints + " / " + picturePts, LEVEL_VDEBUG);
            if (picturePts >= upToPoints) {
                return loadFrame(workingPicture);
            }
        }

        logger.warn("Could not seek to frame " + upToFrame);
        return false;
    }

    int offset = 0;
    int bytesRead = 0;
    boolean continueReading = false;

    private boolean readVideoStreamUntilNextFrame() throws IOException, InterruptedException {
        if (continueReading) {
            while (offset < workingPacket.getSize()) {
                bytesRead += videoDecoder.decode(workingPicture, workingPacket, offset);
                offset += bytesRead;
                if (loadFrame(workingPicture))
                    return true;
            }
            continueReading = false;
        }

        while (demuxer.read(workingPacket) >= 0) {
            if (workingPacket.getStreamIndex() == videoStreamId) {
                continueReading = true;
                offset = bytesRead = 0;
                do {
                    bytesRead += videoDecoder.decode(workingPicture, workingPacket, offset);
                    offset += bytesRead;
                    if (loadFrame(workingPicture))
                        return true;
                } while (offset < workingPacket.getSize());
                continueReading = false;
            }
        }

        while (true) {
            videoDecoder.decode(workingPicture, null, 0);
            if (!workingPicture.isComplete())
                return false;
            if (loadFrame(workingPicture))
                return true;
        }
    }

    private boolean loadFrame(MediaPicture picture) {
        if (!picture.isComplete())
            return false;

        workingImage = pictureConverter.toImage(workingImage, workingPicture);
        LoadedFrame frame = new LoadedFrame();
        frame.data = Texture.loadTextureData(workingImage, false);
        frame.frameNum = (int) Math.round(picture.getPts() * presentationTimeToFrames);

        synchronized (this) {
            if (loadedFrames.size() >= CONCURRENT_LOADED_FRAMES) {
                try {
                    this.wait();
                } catch (InterruptedException x) {
                }
            }

            if (nextFrameSeekInVideoStream >= 0) {
                logger.debug("Frame that was being loaded has been dropped after a time update");
                return true;
            }

            loadedFrames.add(frame);
            if (loadedFrames.size() == 1)
                this.notify();
        }

        return true;
    }

    private int getRealFrameAsVideoFrame() {
        int frame = (int) (Time.getTime() * videoFramerate);
        frame %= videoFrameDuration;
        if (frame < 0)
            frame += videoFrameDuration;
        return frame;
    }

    @Override
    public void close() {
        try {
            demuxer.close();
        } catch (InterruptedException | IOException e) {
            logger.err("Failed to close the video stream?");
        }
    }

    @Override
    public Texture getTexture() {
        if (Time.justChanged()) {
            synchronized (this) {
                nextFrameSeekInVideoStream = getRealFrameAsVideoFrame();
                loadedFrames.clear();
                this.notify();
            }
        }

        int currentFrameNum = getRealFrameAsVideoFrame();
        if (textureStoredFrame != currentFrameNum) {
            if (pboStoredFrame != currentFrameNum) {
                loadNextFrameIntoPBO();
                if (pboStoredFrame != currentFrameNum)
                    logger.warn("Unexpected frame displayed " + pboStoredFrame + " vs " + currentFrameNum);
            }
            pbo.copyToTexture(currentFrame);
            textureStoredFrame = pboStoredFrame;
            loadNextFrameIntoPBO();
        }
        return currentFrame;
    }

    int readNum = 0;
    private void loadNextFrameIntoPBO() {
        ByteBuffer buf = pbo.map();
        synchronized (this) {
            if (loadedFrames.isEmpty()) {
                logger.debug("Frame throttling, empty buffer");
                try {
                    this.wait();
                } catch (InterruptedException x) {
                }
            }
            if (!loadedFrames.isEmpty()) {
                LoadedFrame frame = loadedFrames.remove(0);
                logger.log("Reading frame " + frame.frameNum + " (" + (readNum++) + ")", LEVEL_VDEBUG);
                buf.asIntBuffer().put(frame.data);
                pboStoredFrame = frame.frameNum;
            }
            if (loadedFrames.size() < CONCURRENT_LOADED_FRAMES)
                this.notify();
        }
        pbo.unmap();
    }

    public float getFramerate() {
        return videoFramerate;
    }
}

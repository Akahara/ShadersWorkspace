package wonder.shaderdisplay.serial;

import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.PixelBuffer;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class VideoStream implements InputImageStream {

    static final int LEVEL_VDEBUG = Logger.LEVEL_DEBUG - 100;

    final File videoFile;
    final Logger logger;

    int videoWidth, videoHeight;

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

    int packetRunningOffset = 0;
    int packetRunningBytesRead = 0;
    boolean continueReadingPacket = false;

    public VideoStream(File videoFile) throws BadInitException {
        this.videoFile = videoFile;
        this.logger = new SimpleLogger(videoFile.getName(), Logger.LEVEL_ERROR);
        this.demuxer = Demuxer.make();
        this.workingPacket = MediaPacket.make();

        try {
            demuxer.open(videoFile.getAbsolutePath(), null, false, true, null, null);

            int numStreams = demuxer.getNumStreams();
            for (int i = 0; i < numStreams; i++) {
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
        this.videoWidth = videoDecoder.getWidth();
        this.videoHeight = videoDecoder.getHeight();
        this.workingPicture = MediaPicture.make(videoWidth, videoHeight, videoDecoder.getPixelFormat());
        this.pictureConverter = MediaPictureConverterFactory.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24, workingPicture);
    }

    @Override
    public int[] getImageResolution() {
        return new int[]{videoWidth, videoHeight};
    }

    @Override
    public void startReading() {
        this.pbo = new PixelBuffer(videoWidth * videoHeight * 4);
        this.currentFrame = new Texture(videoWidth, videoHeight, Texture.InternalTextureFormat.RGBA8);
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
                    packetRunningOffset = packetRunningBytesRead = 0;
                    continueReadingPacket = false;
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
                continueReadingPacket = true;
                packetRunningOffset = packetRunningBytesRead = 0;
                do {
                    packetRunningBytesRead += videoDecoder.decode(workingPicture, workingPacket, packetRunningOffset);
                    packetRunningOffset += packetRunningBytesRead;
                    long picturePts = workingPicture.getPts();
                    if (!workingPicture.isComplete())
                        continue;

                    logger.log("Skipping frame " + upToPoints + " / " + picturePts, LEVEL_VDEBUG);
                    if (picturePts >= upToPoints)
                        return loadFrame(workingPicture);
                } while (packetRunningOffset < workingPacket.getSize());
                continueReadingPacket = false;
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

    private boolean readVideoStreamUntilNextFrame() throws IOException, InterruptedException {
        if (continueReadingPacket) {
            while (packetRunningOffset < workingPacket.getSize()) {
                packetRunningBytesRead += videoDecoder.decode(workingPicture, workingPacket, packetRunningOffset);
                packetRunningOffset += packetRunningBytesRead;
                if (loadFrame(workingPicture))
                    return true;
            }
            continueReadingPacket = false;
        }

        while (demuxer.read(workingPacket) >= 0) {
            if (workingPacket.getStreamIndex() == videoStreamId) {
                continueReadingPacket = true;
                packetRunningOffset = packetRunningBytesRead = 0;
                do {
                    packetRunningBytesRead += videoDecoder.decode(workingPicture, workingPacket, packetRunningOffset);
                    packetRunningOffset += packetRunningBytesRead;
                    if (loadFrame(workingPicture))
                        return true;
                } while (packetRunningOffset < workingPacket.getSize());
                continueReadingPacket = false;
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

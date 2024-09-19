package wonder.shaderdisplay;

import com.zakgof.velvetvideo.IDemuxer;
import com.zakgof.velvetvideo.IVideoDecoderStream;
import com.zakgof.velvetvideo.IVideoFrame;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;
import fr.wonder.commons.exceptions.NotImplementedException;
import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageInputFiles {

    public static ImageInputFiles singleton;

    private final File[] inputFiles;
    private final ImageInputStream[] streams;

    public ImageInputFiles(File[] files) {
        this.inputFiles = files;
        this.streams = new ImageInputStream[files.length];
    }

    public void startReadingFiles() throws BadInitException {
        List<VideoStream> videoStreams = new ArrayList<>();

        for (int i = 0; i < inputFiles.length; i++) {
            File file = inputFiles[i];
            String extension = FilesUtils.getFileExtension(file);

            if (!file.isFile() || !file.exists())
                throw new BadInitException("Input file '" + file + "' does not exist");

            switch (extension) {
                case "mp4", "avi" -> {
                    Main.logger.debug("Trying to read '" + file + "' as a video stream");
                    VideoStream stream = new VideoStream(file);
                    streams[i] = stream;
                    videoStreams.add(stream);
                }
                default -> {
                    Main.logger.debug("Trying to read '" + file + "' as an image file");
                    streams[i] = new FixedImageStream(file);
                }
            }
        }

        for (ImageInputStream stream : streams)
            stream.open();

        if (!videoStreams.isEmpty())
            new Thread(() -> loadVideoLoop(videoStreams), "asset-streaming").start();
    }

    private static void loadVideoLoop(List<VideoStream> videoStreams) {
        if (videoStreams.size() != 1)
            throw new NotImplementedException("TODO- loading multiple videos at the same time");

        while (true) {
            VideoStream nextLoadingStream = videoStreams.get(0);
            int nextFrame;
            synchronized (nextLoadingStream) {
                nextFrame = nextLoadingStream.nextLoadableFrame++;
                if (nextFrame - Time.getFrame() > 15) {
                    try {
                        long nano = System.nanoTime();
                        nextLoadingStream.wait();
                        System.out.printf("Waited for %.2fms%n", (System.nanoTime() - nano) / 1E6);
                    } catch (InterruptedException x) {
                    }
                }
            }

            long nano = System.nanoTime();
            nextLoadingStream.videoDecoderStream.seek(nextFrame % nextLoadingStream.videoDecoderStream.properties().frames());
            IVideoFrame frame = nextLoadingStream.videoDecoderStream.nextFrame();
            System.out.printf("Loaded frame %d in %.2fms%n", nextFrame, (System.nanoTime() - nano) / 1E6);

            synchronized (nextLoadingStream) {
                if (frame == null)
                    throw new IllegalStateException("Could not read frame " + Time.getFrame() + " of '" + nextLoadingStream.videoFile + "'");
                nextLoadingStream.loadedFrames.add(frame.image());
                nextLoadingStream.notify();
            }
        }
    }

    public void update() throws IOException {
        for (ImageInputStream stream : streams)
            stream.update();
    }

    public Texture getInputTexture(int inputTextureSlot) {
        return streams.length < inputTextureSlot
                ? Texture.getMissingTexture()
                : streams[inputTextureSlot].getTexture();
    }

}

interface ImageInputStream {

    void open() throws BadInitException;
    void update();
    void close();
    Texture getTexture();

}

class FixedImageStream implements ImageInputStream {

    private File file;
    private Texture texture;

    public FixedImageStream(File textureFile) {
        this.file = textureFile;
    }

    @Override
    public void open() throws BadInitException {
        this.texture = Texture.loadTexture(file);
    }

    @Override
    public void update() {}

    @Override
    public void close() {
        texture.dispose();
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

}

class VideoStream implements ImageInputStream {

    final File videoFile;

    IDemuxer demuxer;
    IVideoDecoderStream videoDecoderStream;
    List<BufferedImage> loadedFrames = new ArrayList<>();
    Texture currentFrame;
    int currentFrameNumber = 0;
    int nextLoadableFrame = 0;

    public VideoStream(File videoFile) {
        this.videoFile = videoFile;
    }

    @Override
    public void open() throws BadInitException {
        demuxer = VelvetVideoLib.getInstance().demuxer(videoFile);
        videoDecoderStream = demuxer.videoStream(0);
    }

    @Override
    public void update() {
    }

    @Override
    public void close() {
        demuxer.close();
    }

    @Override
    public synchronized Texture getTexture() {
        if (currentFrameNumber != Time.getFrame()) {
            if (loadedFrames.isEmpty()) {
                try {
                    long nano = System.nanoTime();
                    wait();
                    System.out.printf("Blocked for %.2fms%n", (System.nanoTime() - nano) / 1E6);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Displayed frame " + currentFrameNumber);
            currentFrame = new Texture(loadedFrames.remove(0));
            currentFrameNumber++;
        }
        notify();
        return currentFrame;
    }
}

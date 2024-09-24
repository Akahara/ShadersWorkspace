package wonder.shaderdisplay;

import fr.wonder.commons.files.FilesUtils;
import io.humble.video.*;
import io.humble.video.awt.ImageFrame;
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
                case "mp4", "avi", "webm" -> {
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

        /*
        long decodeDelta = 0, uploadDelta = 0, trueDelta = 0, t0 = System.nanoTime();
        for (VideoStream stream : videoStreams) {
            //nextLoadingStream.videoDecoderStream.seek(nextFrame % nextLoadingStream.videoDecoderStream.properties().frames());
            long frames = stream.videoDecoderStream.properties().frames();
            frames = 20;
            for (long i = 0; i < frames; i++) {
                long n0 = System.nanoTime();
                IVideoFrame frame = stream.videoDecoderStream.nextFrame();
                if (frame == null)
                    throw new IllegalStateException("Could not read frame " + Time.getFrame() + " of '" + stream.videoFile + "'");
                stream.loadedFrames.add(frame.image());
                decodeDelta += System.nanoTime() - n0;
                n0 = System.nanoTime();
                //stream.gpuFrames.add(new Texture(frame.image()));
                uploadDelta += System.nanoTime() - n0;
                GL11.glFinish();
                trueDelta += System.nanoTime() - n0;
            }
        }
        System.out.printf("%.2f %.2f %.2f %.2f%n", decodeDelta / 1e9, uploadDelta/ 1e9, trueDelta/ 1e9, (System.nanoTime() - t0)/ 1e9);
        */
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

    List<BufferedImage> loadedFrames = new ArrayList<>();
    Texture currentFrame;
    PixelBuffer pbo;
    int pboStoredFrame = -1;

    public VideoStream(File videoFile) {
        this.videoFile = videoFile;
    }

    @Override
    public void open() throws BadInitException {
        Demuxer demuxer = Demuxer.make();

        try {
            demuxer.open(videoFile.getAbsolutePath(), null, false, true, null, null);
            int numStreams = demuxer.getNumStreams();
            int videoStreamId = -1;
            Decoder videoDecoder = null;
            for(int i = 0; i < numStreams; i++)
            {
                final DemuxerStream stream = demuxer.getStream(i);
                final Decoder decoder = stream.getDecoder();
                if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
                    videoStreamId = i;
                    videoDecoder = decoder;
                    break;
                }
            }
            if (videoStreamId == -1)
                throw new RuntimeException("could not find video stream in container");

            videoDecoder.open(null, null);

            int w = videoDecoder.getWidth(), h = videoDecoder.getHeight();

            MediaPicture picture = MediaPicture.make(w, h, videoDecoder.getPixelFormat());
            MediaPictureConverter converter = MediaPictureConverterFactory.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24, picture);
            MediaPacket packet = MediaPacket.make();
            BufferedImage image = null;

            int totalFrames = 150;

            while (demuxer.read(packet) >= 0) {
                if (packet.getStreamIndex() == videoStreamId) {
                    int offset = 0;
                    int bytesRead = 0;
                    do {
                        bytesRead += videoDecoder.decode(picture, packet, offset);
                        if (picture.isComplete()) {
                            image = converter.toImage(image, picture);
                            loadedFrames.add(image);
                        }
                        if (loadedFrames.size() >= totalFrames) break;
                        offset += bytesRead;
                    } while (offset < packet.getSize());
                    if (loadedFrames.size() >= totalFrames) break;
                }
            }

            do {
                videoDecoder.decode(picture, null, 0);
                if (picture.isComplete()) {
                    image = converter.toImage(image, picture);
                    loadedFrames.add(image);
                }
                if (loadedFrames.size() >= totalFrames) break;
            } while (picture.isComplete());

            demuxer.close();

            pbo = new PixelBuffer(w * h * 4);
            currentFrame = new Texture(w, h);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update() {
    }

    @Override
    public void close() {
    }

    @Override
    public Texture getTexture() {
        int wantedFrame = Time.getFrame() % loadedFrames.size();
        if (pboStoredFrame != wantedFrame) {
            //videoDecoderStream.seek(wantedFrame);
            loadNextFrameIntoPBO();
        }
        pbo.copyToTexture(currentFrame);
        loadNextFrameIntoPBO();
        pboStoredFrame = wantedFrame;
        return currentFrame;
    }

    private void loadNextFrameIntoPBO() {
        ByteBuffer buf = pbo.map();
        buf.asIntBuffer().put(Texture.loadTextureData(loadedFrames.get(Time.getFrame() % loadedFrames.size()), false));
        pbo.unmap();
    }
}

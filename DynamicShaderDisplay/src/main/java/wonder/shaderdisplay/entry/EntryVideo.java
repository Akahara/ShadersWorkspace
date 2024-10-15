package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.UnreachableException;
import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import wonder.shaderdisplay.ImageInputFiles;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.display.WindowBlit;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneRenderTarget;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

public class EntryVideo extends SetupUtils {

    protected static void loadCommonOptions(Main.VideoOptions options, ImageInputFiles inputFiles) throws BadInitException {
        loadCommonOptions(options.displayOptions, inputFiles);

        if (options.framerate <= 0)
            throw new BadInitException("The framerate must be >0");
        if (options.lastFrame <= 0 && options.videoDuration <= 0)
            throw new BadInitException("Video duration not specified, run with -l <last frame> or -d <duration in seconds>");
        if (options.lastFrame != 0 && options.videoDuration != 0)
            throw new BadInitException("Both 'last frame' and 'duration' cannot be specified at the same time");
        options.lastFrame = options.lastFrame <= 0 ? (int) (options.videoDuration * options.framerate) : options.lastFrame;
        if (options.lastFrame <= options.firstFrame)
            throw new BadInitException("Last frame cannot be less than or equal to the first frame");

        Time.setFps(options.framerate);
    }

    public static void run(Main.VideoOptions options, File fragment, File... inputFiles) {
        Main.logger.info("-- Running video generation --");

        int videoWidth = options.displayOptions.winWidth, videoHeight = options.displayOptions.winHeight;
        Display display;
        Scene scene;
        fragment = getMainSceneFile(fragment);

        try {
            ImageInputFiles imageInputFiles = ImageInputFiles.singleton = new ImageInputFiles(inputFiles, false);
            loadCommonOptions(options, imageInputFiles);

            display = createDisplay(options.displayOptions, options.preview, false);
            imageInputFiles.startReadingFiles();
            scene = createScene(options.displayOptions, fragment);
            scene.prepareSwapChain(videoWidth, videoHeight);
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        BufferedImage frame = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_3BYTE_BGR);
        int[] buffer = new int[videoWidth*videoHeight];

        Muxer muxer = Muxer.make(options.outputFile.getAbsolutePath(), null, "mp4");
        MuxerFormat format = muxer.getFormat();
        Codec codec = Codec.findEncodingCodec(format.getDefaultVideoCodecId());
        Encoder encoder = Encoder.make(codec);
        MediaPacket packet = MediaPacket.make();

        PixelFormat.Type pixelFormat = PixelFormat.Type.PIX_FMT_YUV420P;
        Rational framerate = Rational.make(1 / options.framerate);
        encoder.setWidth(videoWidth);
        encoder.setHeight(videoHeight);
        encoder.setPixelFormat(pixelFormat);
        encoder.setTimeBase(framerate);

        if (format.getFlag(MuxerFormat.Flag.GLOBAL_HEADER))
            encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);

        encoder.open(null, null);
        muxer.addNewStream(encoder);
        try {
            muxer.open(null, null);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        MediaPictureConverter converter = null;
        MediaPicture picture = MediaPicture.make(videoWidth, videoHeight, pixelFormat);
        picture.setTimeBase(framerate);

        for(int f = options.firstFrame; f < options.lastFrame && !GLWindow.shouldDispose(); f++) {
            Time.setFrame(f);
            display.renderer.render(scene);
            scene.swapChain.readColorAttachment(SceneRenderTarget.DEFAULT_RT.name, buffer, options.displayOptions.background);
            frame.setRGB(0, 0, videoWidth, videoHeight, buffer, videoWidth*(videoHeight-1), -videoWidth);

            BufferedImage screen = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_3BYTE_BGR);
            screen.setRGB(0, 0, videoWidth, videoHeight, buffer, 0, videoWidth);
            if (converter == null)
                converter = MediaPictureConverterFactory.createConverter(screen, picture);
            converter.toPicture(picture, screen, f - options.firstFrame);

            do {
                encoder.encode(packet, picture);
                if (packet.isComplete())
                    muxer.write(packet, false);
            } while (packet.isComplete());

            printProgressbar(f-options.firstFrame + 1, options.lastFrame-options.firstFrame);

            if (options.preview) {
                Texture backbuffer = scene.swapChain.getAttachment(SceneRenderTarget.DEFAULT_RT.name);
                WindowBlit.blitToScreen(backbuffer, options.displayOptions.background == Main.DisplayOptions.BackgroundType.NORMAL);
                glfwSwapBuffers(GLWindow.getWindow());
                glfwPollEvents();
            }
        }

        do {
            encoder.encode(packet, null);
            if (packet.isComplete())
                muxer.write(packet,  false);
        } while (packet.isComplete());
        muxer.close();

        Main.logger.info("Successfully wrote " + options.outputFile);

        Main.exit();
    }

    private static void printProgressbar(int current, int max) {
        boolean isEnded = current == max;
        String maxStr = String.valueOf(max);
        String currentStr = String.format("% " + maxStr.length() + "d", current);
        int barLength = 32 - "Writing frames".length() - 4 - maxStr.length()*2;
        int filled = current * barLength / max;
        int empty = barLength - filled - (isEnded ? 0 : 1);
        System.out.printf("[%s%s%s] %s/%s Writing frames\r%s",
                "=".repeat(filled), isEnded?"":">", "-".repeat(empty), currentStr, maxStr, isEnded?"\n":"");
    }
}

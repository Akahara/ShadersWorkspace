package wonder.shaderdisplay.entry;

import com.zakgof.velvetvideo.*;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;
import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.systems.process.ProcessUtils;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.display.WindowBlit;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneRenderTarget;

import java.awt.image.BufferedImage;
import java.io.File;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

public class EntryVideo extends SetupUtils {

    protected static void loadCommonOptions(Main.VideoOptions options) throws BadInitException {
        loadCommonOptions(options.displayOptions);

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

    public static void run(Main.VideoOptions options, File fragment) {
        Main.logger.info("-- Running video generation --");

        int videoWidth = options.displayOptions.winWidth, videoHeight = options.displayOptions.winHeight;
        Display display;
        Scene scene;

        try {
            loadCommonOptions(options);

            display = createDisplay(options.displayOptions, options.preview, false);
            scene = createScene(options.displayOptions, fragment);
            scene.prepareSwapChain(videoWidth, videoHeight);
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        BufferedImage frame = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_3BYTE_BGR);
        int[] buffer = new int[videoWidth*videoHeight];

        IVelvetVideoLib lib = VelvetVideoLib.getInstance();
        IVideoEncoderBuilder encoderBuilder = lib.videoEncoder("libx264").dimensions(videoWidth, videoHeight);
        try (IMuxer muxer = lib.muxer("mp4").videoEncoder(encoderBuilder).build(options.outputFile)) {
            IVideoEncoderStream videoEncoder = muxer.videoEncoder(0);

            for(int f = options.firstFrame; f < options.lastFrame && !GLWindow.shouldDispose(); f++) {
                Time.setFrame(f);
                display.renderer.render(scene);
                scene.swapChain.readColorAttachment(SceneRenderTarget.DEFAULT_RT.name, buffer, options.displayOptions.background);
                frame.setRGB(0, 0, videoWidth, videoHeight, buffer, videoWidth*(videoHeight-1), -videoWidth);
                videoEncoder.encode(frame);
                ProcessUtils.printProgressbar(f-options.firstFrame, options.lastFrame-options.firstFrame, "Writing frames");

                if (options.preview) {
                    Texture backbuffer = scene.swapChain.getAttachment(SceneRenderTarget.DEFAULT_RT.name);
                    WindowBlit.blitToScreen(backbuffer, options.displayOptions.background == Main.DisplayOptions.BackgroundType.NORMAL);
                    glfwSwapBuffers(GLWindow.getWindow());
                    glfwPollEvents();
                }
            }
        }

        Main.exit();
    }
}

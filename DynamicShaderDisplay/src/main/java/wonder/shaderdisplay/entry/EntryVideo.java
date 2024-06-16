package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import fr.wonder.commons.systems.process.ProcessUtils;
import fr.wonder.commons.utils.StringUtils;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.TexturesSwapChain;
import wonder.shaderdisplay.scene.Scene;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        Display display;
        Scene scene;

        try {
            loadCommonOptions(options);

            display = createDisplay(options.displayOptions, options.preview, false);
            scene = createScene(options.displayOptions, fragment);
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        int videoWidth = options.displayOptions.winWidth, videoHeight = options.displayOptions.winHeight;
        TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(videoWidth, videoHeight);
        BufferedImage frame = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_3BYTE_BGR);
        int[] buffer = new int[videoWidth*videoHeight];
        Logger logger = new SimpleLogger("ffmpeg");

        Process ffmpegProcess;
        try {
            List<String> command = new ArrayList<>();
            command.add(options.ffmpegPath);
            if(!options.ffmpegOptions.isBlank())
                command.addAll(Arrays.asList(StringUtils.splitCLIArgs(options.ffmpegOptions)));
            command.addAll(Arrays.asList(
                    "-y",               // overwrite existing output file
                    "-f", "image2pipe", // pipe images instead of transferring files
                    "-codec", "mjpeg",  // using jpeg compression
                    "-framerate", "" + options.framerate,
                    "-i", "pipe:0",     // which pipe to use (stdin)
                    "-c:a", "copy", "-c:v", "libx264", "-crf", "18", "-preset", "veryslow",
                    options.outputFile.getPath()));
            logger.info("Running ffmpeg with ['" + StringUtils.join("', '", command) + "']");
            ffmpegProcess = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            logger.err(e, "Could not execute ffmpeg, is it installed and on the PATH?");
            Main.exit();
            throw new UnreachableException();
        }

        OutputStream ffmpegStdin = ffmpegProcess.getOutputStream();

        try {
            for(int f = options.firstFrame; f < options.lastFrame && !GLWindow.shouldDispose(); f++) {
                Time.setFrame(f);
                renderTargetsSwapChain.swap();
                renderTargetsSwapChain.bind();
                display.renderer.render(scene);
                renderTargetsSwapChain.readColorAttachment(0, buffer, options.displayOptions.background);
                frame.setRGB(0, 0, videoWidth, videoHeight, buffer, videoWidth*(videoHeight-1), -videoWidth);
                ImageIO.write(frame, "jpeg", ffmpegStdin);
                ProcessUtils.printProgressbar(f-options.firstFrame, options.lastFrame-options.firstFrame, "Writing frames");

                if (options.preview) {
                    renderTargetsSwapChain.blitToScreen(options.displayOptions.background == Main.DisplayOptions.BackgroundType.NORMAL);
                    glfwSwapBuffers(GLWindow.getWindow());
                    glfwPollEvents();
                }
            }
            ffmpegStdin.close();
            ffmpegProcess.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.err(e, "Could not pipe a frame to ffmpeg");
        }

        if(ffmpegProcess.isAlive()) {
            ffmpegProcess.destroyForcibly();
            logger.err("ffmpeg did not terminate");
        } else {
            logger.info("ffmpeg exited with status " + ffmpegProcess.exitValue());
        }

        Main.exit();
    }
}

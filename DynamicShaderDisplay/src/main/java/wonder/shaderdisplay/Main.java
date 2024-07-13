package wonder.shaderdisplay;

import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import fr.wonder.commons.systems.argparser.ArgParser;
import fr.wonder.commons.systems.argparser.annotations.*;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.entry.EntryImage;
import wonder.shaderdisplay.entry.EntryRun;
import wonder.shaderdisplay.entry.EntrySnippets;
import wonder.shaderdisplay.entry.EntryVideo;

import java.io.File;

@ProcessDoc(doc = "DSD - dynamic shader display.\nThis stand-alone is a wrapper for lwjgl and openGL shaders.\nRun with '?' to print help.")
public class Main {

	public static final Logger logger = new SimpleLogger("DSD");

	public static void main(String[] args) {
		if(args.length == 0) {
			String testCmd = System.getenv("TEST_COMMAND");
			if(testCmd != null)
				args = testCmd.split(" "); // warning: quotes cannot be used
		}
		
		try {
			ArgParser.runHere(args);
		} catch (Throwable t) {
			logger.merr(t);
			exit();
		}
	}

	@OptionClass
	public static class SnippetsOptions {

		@Option(name = "--verbose", desc = "verbose output")
		public boolean verbose;
		@Option(name = "--filter", desc = "snippet filter (regex)")
		public String filter = ".*";
		@Option(name = "--code", desc = "print the snippets' codes instead of their names")
		public boolean printCode;
		
	}
	
	@OptionClass
	public static class DisplayOptions {

		@Option(name = "--geometry", shorthand = "-g", desc = "The geometry shader file")
		public File geometryShaderFile;
		@Option(name = "--vertex", shorthand = "-v", desc = "The vertex shader file")
		public File vertexShaderFile;
		@Option(name = "--compute", shorthand = "-c", desc = "The compute shader file")
		public File computeShaderFile;
		@Option(name = "--verbose", desc = "Verbose output")
		public boolean verbose;
		@Option(name = "--force-gl-version", desc = "Forces the opengl version, use format <major>.<minor> (ie: 4.3)")
		public String forcedGLVersion;
		@Option(name = "--width", shorthand = "-w", desc = "Sets the initial window width")
		public int winWidth = 500 * 16/9;
		@Option(name = "--height", shorthand = "-h", desc = "Sets the initial window height")
		public int winHeight = 500;
		@Option(name = "--background", valueName = "format", desc = "When generating images/videos, set to 'no-alpha' to get an opaque image, set to 'black' to add an opaque black background")
		public BackgroundType background = BackgroundType.NORMAL;
		
		public enum BackgroundType {
			NORMAL,
			NO_ALPHA,
			BLACK,
		}
		
	}
	
	@OptionClass
	public static class RunOptions {
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--no-texture-cache", desc = "Disable texture caching")
		public boolean noTextureCache;
		@Option(name = "--hard-reload", desc = "Reload every shader file every time a change is detected\n in one of their folders (may be required with some configurations)")
		public boolean hardReload;
		@Option(name = "--vsync", desc = "Enables vsync, when used the info given in the window title may be inaccurate")
		public boolean vsync;
		@Option(name = "--fps", desc = "Target maximum fps (frames per second), or exact fps for video rendering")
		public float targetFPS = 60;
		@Option(name = "--nogui", desc = "Removes the uniforms gui")
		public boolean noGui = false;
		@Option(name = "--reset-time-on-update", shorthand = "-r", desc = "Resets the iTime uniform when shaders are updated")
		public boolean resetTimeOnUpdate = false;
		@Option(name = "--reset-render-targets-on-update", shorthand = "-t", desc = "Clears the render target textures when shaders are updated")
		public boolean resetRenderTargetsOnUpdate = false;
		@Option(name = "--frame-exact", shorthand = "-e", desc = "Forces iFrame to advance by 1 each frame, if not set iFrame will try to catch up if frames take longer than 1/fps")
		public boolean frameExact = false;
		@Option(name = "--template", shorthand = "-m", desc = "If the fragment file does not exist, create it from a template")
		public FragmentTemplate fragmentTemplate = FragmentTemplate.STANDARD;
		
		public enum FragmentTemplate {
			STANDARD,
			SHADERTOY,
			RAYCASTING,
			FRAMEBUFFERS,
		}
		
	}
	
	@OptionClass
	public static class VideoOptions {
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--first-frame", shorthand = "-f", desc = "First rendering frame (defaults to 0)")
		public int firstFrame;
		@Option(name = "--last-frame", shorthand = "-l", desc = "Last rendering frame, either -d or -l must be specified")
		public int lastFrame;
		@Option(name = "--fps", desc = "Framerate of the generated video")
		public float framerate = 60;
		@Option(name = "--duration", shorthand = "-d", desc = "Output video duration in seconds, either -d or -l must be specified")
		public float videoDuration;
		@Option(name = "--ffmpeg-path", desc = "The path to the ffmpeg executable, defaults to \"ffmpeg\" meaning ffmpeg must be on the PATH")
		public String ffmpegPath = "ffmpeg";
		@Option(name = "--ffmpeg-options", desc = "Options passed to ffmpeg, wrap with quotes")
		public String ffmpegOptions = "";
		@Option(name = "--output", shorthand = "-o", valueName = "file", desc = "Output file path, defaults to \"video.mp4\"")
		public File outputFile = new File("video.mp4");
		@Option(name = "--preview", shorthand = "-p", desc = "Show the window during generation")
		public boolean preview = false;
		
	}
	
	@OptionClass
	public static class ImagePassOptions {

		public static final int NO_RUN_FROM_FRAME = -1;
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--output", shorthand = "-o", valueName = "file", desc = "Output file(s) path, defaults to \"out.png\"")
		public String outputPath = "out.png";
		@Option(name = "--overwrite", shorthand = "-r", desc = "Overwrite existing output files")
		public boolean overwriteExistingFiles = false;
		@Option(name = "--run-from", valueName = "frame", desc = "Run the shader from <frame> up to the screenshot frame, useful when the shader uses rendertargets")
		public int runFromFrame = NO_RUN_FROM_FRAME;
		@Option(name = "--screenshot-frame", shorthand = "-f", valueName = "frame", desc = "Take the screenshot at frame <frame>, previous frames are not simulated if --run-from is not specified")
		public int screenshotFrame;
		@Option(name = "--size-to-image", shorthand = "-s", desc = "Size the shader input to the input image instead of using a fixed size window")
		public boolean sizeToImage = false;
		
	}
	
	@EntryPoint(path = "snippets", help = "Prints a set of useful glsl snippets, filter with -f and print code with -c")
	public static void writeSnippets(SnippetsOptions options) {
		EntrySnippets.run(options);
	}

	@EntryPoint(path = "systeminfo", help = "Prints a number of system information, may be useful for debuging")
	public static void systemInformation() {
		GLWindow.createWindow(1, 1, false, null, true);
		GLWindow.printSystemInformation();
		GLWindow.dispose();
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "run", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void runDisplay(RunOptions options, File fragment) {
		EntryRun.run(options, fragment);
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "video", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void generateVideo(VideoOptions options, File fragment) {
		EntryVideo.run(options, fragment);
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file")
	@Argument(name = "file", desc = "One or more image files to apply the shader to")
	@EntryPoint(path = "image", help = "Applies a shader to an image and saves the output")
	public static void applyShaderToImages(ImagePassOptions options, File fragment, File... inputFiles) {
		EntryImage.run(options, fragment, inputFiles);
	}
	
	/**
	 * Stops all threads and forcibly exit the app.
	 * OpenGL resources are released.
	 */
	public static void exit() {
		System.exit(0);
	}
	
}
package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.imageio.ImageIO;

import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import fr.wonder.commons.systems.argparser.ArgParser;
import fr.wonder.commons.systems.argparser.annotations.Argument;
import fr.wonder.commons.systems.argparser.annotations.EntryPoint;
import fr.wonder.commons.systems.argparser.annotations.InnerOptions;
import fr.wonder.commons.systems.argparser.annotations.Option;
import fr.wonder.commons.systems.argparser.annotations.OptionClass;
import fr.wonder.commons.systems.argparser.annotations.ProcessDoc;
import fr.wonder.commons.systems.process.ProcessUtils;
import fr.wonder.commons.utils.StringUtils;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import wonder.shaderdisplay.Resources.Snippet;
import wonder.shaderdisplay.renderers.FixedFileInputRenderer;
import wonder.shaderdisplay.renderers.FormatedInputRenderer;
import wonder.shaderdisplay.renderers.Renderer;
import wonder.shaderdisplay.renderers.RestrictedRenderer;
import wonder.shaderdisplay.renderers.ScriptRenderer;
import wonder.shaderdisplay.renderers.StandardRenderer;
import wonder.shaderdisplay.uniforms.InputTextureUniform;
import wonder.shaderdisplay.uniforms.ResolutionUniform;

@ProcessDoc(doc = "DSD - dynamic shader display.\nThis stand-alone is a wrapper for lwjgl and openGL shaders.\nRun with '?' to print help.")
public class Main {

	public static final Logger logger = new SimpleLogger("DSD");
	
	public static ActiveUserControls activeUserControls = new ActiveUserControls();

	public static boolean isImagePass = false; // true iff run with "image" (see #applyShaderToImages)
	
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
		@Option(name = "--fps", desc = "Target maximum fps (frames per second), or exact fps for video rendering")
		public float targetFPS = 60;
		@Option(name = "--verbose", desc = "Verbose output")
		public boolean verbose;
		@Option(name = "--input-file", shorthand = "-i", desc = "Input file to get the vertices data from")
		public File inputFile;
		@Option(name = "--script", desc = "External script to run and get the vertices data from")
		public File scriptFile;
		@Option(name = "--script-log", desc = "Maximum numbers of characters printed from the script output at each execution, -1 to print everything")
		public int scriptLogLength = 0;
		@Option(name = "--force-gl-version", desc = "Forces the opengl version, use format <major>.<minor> (ie: 4.3)")
		public String forcedGLVersion;
		@Option(name = "--force-restricted-renderer", desc = "Forces the use of the restricted renderer, it can only take fragment and vertex shaders as inputs but should work even on old opengl versions")
		public boolean forceRestrictedRenderer;
		@Option(name = "--width", shorthand = "-w", desc = "Sets the initial window width")
		public int winWidth = 500;
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
		@Option(name = "--nogui", desc = "Removes the uniforms gui")
		public boolean noGui = false;
		@Option(name = "--reset-time-on-update", shorthand = "-r", desc = "Resets the iTime uniform when shaders are updated")
		public boolean resetTimeOnUpdate = false;
		@Option(name = "--reset-render-targets-on-update", shorthand = "-t", desc = "Clears the render target textures when shaders are updated")
		public boolean resetRenderTargetsOnUpdate = false;
		@Option(name = "--frame-exact", shorthand = "-e", desc = "Forces iFrame to advance by 1 each frame, if not set iFrame will try to catch up if frames take longer than 1/fps")
		public boolean frameExact = false;
		
	}
	
	@OptionClass
	public static class VideoOptions {
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--first-frame", shorthand = "-f", desc = "First rendering frame (defaults to 0)")
		public int firstFrame;
		@Option(name = "--last-frame", shorthand = "-l", desc = "Last rendering frame, either -d or -l must be specified")
		public int lastFrame;
		@Option(name = "--duration", shorthand = "-d", desc = "Output video duration in seconds, either -d or -l must be specified")
		public float videoDuration;
		@Option(name = "--ffmpeg-path", desc = "The path to the ffmpeg executable, defaults to \"ffmpeg\" meaning ffmpeg must be on the PATH")
		public String ffmpegPath = "ffmpeg";
		@Option(name = "--ffmpeg-options", desc = "Options passed to ffmpeg, wrap with quotes")
		public String ffmpegOptions = "";
		@Option(name = "--output", shorthand = "-o", valueName = "file", desc = "Output file path, defaults to \"video.mp4\"")
		public File outputFile = new File("video.mp4");
		
	}
	
	@OptionClass
	public static class ImagePassOptions {
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--output", shorthand = "-o", valueName = "file", desc = "Output file(s) path, defaults to \"out.png\"")
		public String outputPath = "out.png";
		@Option(name = "--overwrite", shorthand = "-r", desc = "Overwrite existing output files")
		public boolean overwriteExistingFiles = false;
		@Option(name = "--size-to-image", shorthand = "-s", desc = "Size the shader input to the input image instead of using a fixed size window")
		public boolean sizeToImage = false;
		
	}
	
	@OptionClass
	public static class ScreenshotPassOptions {
		
		public static final int NO_RUN_FROM_FRAME = -1;
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--run-from", valueName = "frame", desc = "Run the shader from <frame> up to the screenshot frame, useful when the shader uses rendertargets")
		public int runFromFrame = NO_RUN_FROM_FRAME;
		@Option(name = "--screenshot-frame", shorthand = "-f", valueName = "frame", desc = "Take the screenshot at frame <frame>, previous frames are not simulated if --run-from is not specified")
		public int screenshotFrame;
		@Option(name = "--overwrite", shorthand = "-r", desc = "Overwrite existing output files")
		public boolean overwriteExistingFiles = false;
		
	}
	
	public static class ActiveUserControls {
		
		public boolean takeScreenshot = false;
		public boolean drawBackground = true;
		
	}
	
	private static class Display {
		
		public Renderer renderer;
		
	}
	
	@EntryPoint(path = "snippets", help = "Prints a set of useful glsl snippets, filter with -f and print code with -c")
	public static void writeSnippets(SnippetsOptions options) {
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		Resources.scanForAndLoadSnippets();
		List<Snippet> snippets;
		try {
			snippets = Resources.filterSnippets(options.filter);
		} catch (PatternSyntaxException e) {
			Main.logger.err(e, "Invalid filter");
			return;
		}
		if(snippets.isEmpty()) {
			System.err.println("No matching snippets");
			return;
		}
		for(Snippet s : snippets) {
			if(options.printCode)
				System.out.println(s.code);
			else
				System.out.println(s.name);
		}
		if(!options.printCode) {
			System.out.println("Found " + snippets.size() + " snippets, run with -c to print their codes");
		}
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
		logger.info("-- Running display --");
		
		// create display, load renderer etc
		Display display = createDisplay(options.displayOptions, true, options.vsync);
		Texture.setUseCache(!options.noTextureCache);
		
		try {
			TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(options.displayOptions.winWidth, options.displayOptions.winHeight);
			ShaderFileWatcher shaderFiles = new ShaderFileWatcher();
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			shaderFiles.startWatching(options.hardReload);
			Resources.scanForAndLoadSnippets();
			UserControls.init();
			GLWindow.addResizeListener(renderTargetsSwapChain::resizeTextures);
	
			reloadShaders(shaderFiles, display.renderer);
		
			// load ImGui
			ImGuiImplGlfw glfw = null;
			ImGuiImplGl3 gl3 = null;
			if(!options.noGui) {
				ImGui.createContext();
				glfw = new ImGuiImplGlfw();
				gl3 = new ImGuiImplGl3();
				glfw.init(GLWindow.getWindow(), true);
				gl3.init();
			}
			
			logger.info("Running shader");
		
			long shaderLastNano = System.nanoTime();
			long nextFrame = System.nanoTime();
			long lastSec = System.nanoTime();
			int frames = 0;
			long workTime = 0;
			
			while (!GLWindow.shouldDispose()) {
				// reload shaders if necessary
				if(shaderFiles.needShaderRecompilation()) {
					reloadShaders(shaderFiles, display.renderer);
					if(options.resetTimeOnUpdate)
						Time.setFrame(0);
					if(options.resetRenderTargetsOnUpdate)
						renderTargetsSwapChain.clearTextures();
				}
				
				if(System.in.available() > 0)
					UserControls.readStdin();
				
				// -------- draw frame ---------

				// render the actual frame
				renderTargetsSwapChain.swap();
				renderTargetsSwapChain.bind();
				if(!Time.isPaused() || Time.justChanged())
					display.renderer.render();
				renderTargetsSwapChain.blitToScreen();

				// update time *after* having drawn the frame and *before* drawing controls
				// that way time can be set by the controls and not be modified until next frame
				long current = System.nanoTime();
				if(options.frameExact)
					Time.stepFrame(1);
				else
					Time.step((current - shaderLastNano)/(float)1E9);
				shaderLastNano = current;
				
				// begin GUI
				if(!options.noGui) {
					glfw.newFrame();
					ImGui.newFrame();
					UserControls.renderControls(renderTargetsSwapChain);
					display.renderer.renderControls();
					ImGui.render();
					gl3.renderDrawData(ImGui.getDrawData());
					ImGui.updatePlatformWindows();
					ImGui.renderPlatformWindowsDefault();
				}
				
				// ---------/draw frame/---------
				
				glfwSwapBuffers(GLWindow.getWindow());
				glfwPollEvents();
				
				if(activeUserControls.takeScreenshot) {
					UserControls.takeScreenshot(renderTargetsSwapChain, options.displayOptions);
					activeUserControls.takeScreenshot = false;
				}
	
				workTime += System.nanoTime() - current;
				current = System.nanoTime();
				if (current < nextFrame)
					ProcessUtils.sleep((nextFrame - current) / (int) 1E6);
				nextFrame += 1E9 / options.displayOptions.targetFPS;
				frames++;
				if (current > lastSec + 1E9) {
					GLWindow.setWindowTitle(String.format("Shader workspace - %d fps - %f millis per frame - %d expected fps",
							frames, workTime / 1E6 / frames, (int) (1E9 * frames / workTime)));
					lastSec = current;
					workTime = 0;
					frames = 0;
				}
			}
			
			GLWindow.dispose();
		} catch (Throwable e) {
			if(options.displayOptions.verbose)
				logger.merr(e, "An error occurred");
			else
				logger.err(e, "An error occurred");
		} finally {
			exit();
		}
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "video", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void generateVideo(VideoOptions options, File fragment) {
		if(options.lastFrame <= 0 && options.videoDuration <= 0) {
			System.err.println("Video duration not specified, run with -l <last frame> or -d <duration in seconds>");
			return;
		}
		if(options.lastFrame != 0 && options.videoDuration != 0) {
			System.err.println("Both 'last frame' and 'duration' cannot be specified at the same time");
			return;
		}
		int firstFrame = options.firstFrame;
		int lastFrame = options.lastFrame <= 0 ? (int) (options.videoDuration * options.displayOptions.targetFPS) : options.lastFrame;
		if(lastFrame <= firstFrame) {
			System.err.println("Last frame cannot be less than or equal to the first frame");
			return;
		}
		
		logger.info("-- Running video generation --");
		// create display, load renderer etc
		int w = options.displayOptions.winWidth, h = options.displayOptions.winHeight;
		
		Display display = createDisplay(options.displayOptions, false, false);
		ShaderFiles shaderFiles = new ShaderFiles();
		TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(w, h);
		BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		int[] buffer = new int[w*h];
		Logger logger = new SimpleLogger("ffmpeg");
		
		try {
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			reloadShaders(shaderFiles, display.renderer);
		} catch (IOException e) {
			logger.err(e, "Could not load shaders");
		}
		
		
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
					"-framerate", ""+options.displayOptions.targetFPS,
					"-i", "pipe:0",     // which pipe to use (stdin)
					"-c:a", "copy", "-c:v", "libx264", "-crf", "18", "-preset", "veryslow",
					options.outputFile.getPath()));
			logger.info("Running ffmpeg with ['" + StringUtils.join("', '", command) + "']");
			ffmpegProcess = new ProcessBuilder(command)
					.redirectError(Redirect.INHERIT)
					.redirectOutput(Redirect.INHERIT)
					.start();
		} catch (IOException e) {
			logger.err(e, "Could not execute ffmpeg");
			exit(); throw new UnreachableException();
		}
		
		OutputStream ffmpegStdin = ffmpegProcess.getOutputStream();

		try {
			for(int f = firstFrame; f < lastFrame; f++) {
				Time.setFrame(f);
				renderTargetsSwapChain.swap();
				renderTargetsSwapChain.bind();
				display.renderer.render();
				renderTargetsSwapChain.readColorAttachment(0, buffer, options.displayOptions.background);
				frame.setRGB(0, 0, w, h, buffer, w*(h-1), -w);
				ImageIO.write(frame, "jpeg", ffmpegStdin);
				ProcessUtils.printProgressbar(f-firstFrame, lastFrame-firstFrame, "Writing frames");
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
		exit();
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file")
	@Argument(name = "file", desc = "One or more image files to apply the shader to")
	@EntryPoint(path = "image", help = "Applies a shader to an image and saves the output")
	public static void applyShaderToImages(ImagePassOptions options, File fragment, File... inputFiles) {
		if(!options.outputPath.contains("{}") && !new File(options.outputPath).isDirectory() && inputFiles.length > 1) {
			System.err.println("Output path is not a directory and multiple input files given, use -o <directory>");
			return;
		}
		
		logger.info("-- Running image shader pass --");
		Main.isImagePass = true;
		
		// create display, load renderer etc
		int w = options.displayOptions.winWidth, h = options.displayOptions.winHeight;
		
		Display display = createDisplay(options.displayOptions, false, false);
		ShaderFiles shaderFiles = new ShaderFiles();
		TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(w, h);
		BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		int[] buffer = new int[w*h];
		
		try {
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			reloadShaders(shaderFiles, display.renderer);
		} catch (IOException e) {
			logger.err(e, "Could not load shaders");
		}
		
		for(File inputFile : inputFiles) {
			String outputPath = options.outputPath.replaceAll("\\{}", inputFile.getName());
			File outputFile = new File(outputPath);
			if(outputFile.isDirectory())
				outputFile = new File(outputFile, inputFile.getName());
			String imageFormat = FilesUtils.getFileExtension(outputFile).toUpperCase();
			if(!options.overwriteExistingFiles && outputFile.exists()) {
				logger.warn("File '" + outputFile.getPath() + "' already exists, add -r to overwrite it");
				continue;
			}
			
			Texture inputTexture;
			try {
				inputTexture = new Texture(ImageIO.read(inputFile));
			} catch (IOException e) {
				logger.err("Could not read file '" + inputFile.getPath() + "': " + e.getMessage());
				continue;
			}
			
			if(options.sizeToImage) {
				w = inputTexture.getWidth();
				h = inputTexture.getHeight();
				ResolutionUniform.updateViewportSize(w, h);
				renderTargetsSwapChain.resizeTextures(w, h);
				frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
				buffer = new int[w*h];
			}
				
			renderTargetsSwapChain.swap();
			renderTargetsSwapChain.bind();
			inputTexture.bind(InputTextureUniform.INPUT_TEXTURE_SLOT);
			display.renderer.render();
			renderTargetsSwapChain.readColorAttachment(0, buffer, options.displayOptions.background);
			frame.setRGB(0, 0, w, h, buffer, w*(h-1), -w);
			try {
				ImageIO.write(frame, imageFormat, outputFile);
				logger.info("Wrote '" + outputFile.getPath() + "'");
			} catch (IOException e) {
				logger.err("Could not write file '" + outputFile.getPath() + "': " + e.getMessage());
			}
			
			inputTexture.dispose();
		}
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file")
	@Argument(name = "file", desc = "The output file")
	@EntryPoint(path = "screenshot", help = "Runs the shader and saves a single frame to a file")
	public static void applyShaderToImages(ScreenshotPassOptions options, File fragment, File outputFile) {
		if(outputFile.isDirectory()) {
			System.err.println(outputFile + " is a directory");
			return;
		} else if(outputFile.exists() && !options.overwriteExistingFiles) {
			System.err.println(outputFile + " already exists, add -r to overwrite it");
			return;
		}
		
		logger.info("-- Running screenshot shader pass --");
		Main.isImagePass = true;
		
		// create display, load renderer etc
		int w = options.displayOptions.winWidth, h = options.displayOptions.winHeight;
		
		Display display = createDisplay(options.displayOptions, false, false);
		ShaderFiles shaderFiles = new ShaderFiles();
		TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(w, h);
		BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		int[] buffer = new int[w*h];
		
		try {
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			reloadShaders(shaderFiles, display.renderer);
		} catch (IOException e) {
			logger.err(e, "Could not load shaders");
		}
		
		String imageFormat = FilesUtils.getFileExtension(outputFile).toUpperCase();
		
		if(options.runFromFrame != ScreenshotPassOptions.NO_RUN_FROM_FRAME) {
			for(int i = options.runFromFrame; i < options.screenshotFrame; i++) {
				Time.setFrame(i);
				renderTargetsSwapChain.swap();
				renderTargetsSwapChain.bind();
				display.renderer.render();
			}
		}
		
		Time.setFrame(options.screenshotFrame);
		
		renderTargetsSwapChain.swap();
		renderTargetsSwapChain.bind();
		display.renderer.render();
		renderTargetsSwapChain.readColorAttachment(0, buffer, options.displayOptions.background);
		frame.setRGB(0, 0, w, h, buffer, w*(h-1), -w);
		try {
			ImageIO.write(frame, imageFormat, outputFile);
			logger.info("Wrote '" + outputFile.getPath() + "'");
		} catch (IOException e) {
			logger.err("Could not write file '" + outputFile.getPath() + "': " + e.getMessage());
		}
	}
	
	private static Display createDisplay(DisplayOptions options, boolean windowVisible, boolean useVSync) {
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		ScriptRenderer.scriptLogger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		
		Display display = new Display();
		
		// validate options
		if(options.scriptLogLength == -1) {
			options.scriptLogLength = Integer.MAX_VALUE;
		} else if(options.scriptLogLength < 0) {
			logger.err("Invalid script log length: " + options.scriptLogLength);
			System.exit(1);
		}
		if(options.targetFPS <= 0) {
			logger.err("Invalid fps: " + options.targetFPS);
			System.exit(1);
		}

		// loading resources
		try {
			display.renderer = getSuitableRenderer(options);
			logger.debug("Using renderer: " + display.renderer.getClass().getSimpleName());
		} catch (IllegalArgumentException e) {
			logger.err(e.getMessage());
			System.exit(1);
		}

		logger.info("Creating window");
		GLWindow.createWindow(options.winWidth, options.winHeight, windowVisible, options.forcedGLVersion, options.verbose);
		GLWindow.setVSync(useVSync);
		GLWindow.setTaskBarIcon("/icon.png");
		GLWindow.addResizeListener(ResolutionUniform::updateViewportSize);
		ResolutionUniform.updateViewportSize(options.winWidth, options.winHeight);
		Time.setFps(options.targetFPS);
		
		display.renderer.loadResources();
		
		return display;
	}
	
	private static Renderer getSuitableRenderer(DisplayOptions options) {
		int activeRenderers = 0;
		if(options.computeShaderFile != null) activeRenderers++;
		if(options.scriptFile != null)        activeRenderers++;
		if(options.inputFile != null)         activeRenderers++;
		if(options.forceRestrictedRenderer)   activeRenderers++;
		if(activeRenderers > 1)
			throw new IllegalArgumentException("Only one can be active: restricted renderer, compute shader, script input, input file");
		
		if(options.scriptFile != null)
			return new ScriptRenderer(options.scriptFile, options.scriptLogLength);
		if(options.inputFile != null)
			return new FixedFileInputRenderer(options.inputFile);
		if(options.computeShaderFile != null)
			return new StandardRenderer();
		if(options.forceRestrictedRenderer)
			return new RestrictedRenderer();
		
		return new StandardRenderer();
	}
	
	private static void fillInShaderFiles(ShaderFiles shaderFiles, File fragment, DisplayOptions options) throws IOException {
		shaderFiles.addShaderFile(fragment, Resources.TYPE_FRAGMENT);
		if(options.geometryShaderFile != null)
			shaderFiles.addShaderFile(options.geometryShaderFile, Resources.TYPE_GEOMETRY);
		if(options.vertexShaderFile != null)
			shaderFiles.addShaderFile(options.vertexShaderFile, Resources.TYPE_VERTEX);
		if(options.computeShaderFile != null)
			shaderFiles.addShaderFile(options.computeShaderFile, Resources.TYPE_COMPUTE);
		if(options.scriptFile != null)
			shaderFiles.addDummyFile(options.scriptFile);
		if(options.inputFile != null)
			shaderFiles.addDummyFile(options.inputFile);
		shaderFiles.completeWithDefaultSources();
	}
	
	private static void reloadShaders(ShaderFiles shaderFiles, Renderer renderer) {
		if(renderer instanceof FormatedInputRenderer)
			((FormatedInputRenderer) renderer).reloadInputFile();
		renderer.compileShaders(shaderFiles.pollShadersSources());
	}
	
	/**
	 * Stops all threads and forcibly exit the app.
	 * OpenGL resources are released.
	 */
	public static void exit() {
		System.exit(0);
	}
	
}
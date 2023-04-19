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
import imgui.flag.ImGuiCond;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import wonder.shaderdisplay.Resources.Snippet;
import wonder.shaderdisplay.renderers.FixedFileInputRenderer;
import wonder.shaderdisplay.renderers.FormatedInputRenderer;
import wonder.shaderdisplay.renderers.Renderer;
import wonder.shaderdisplay.renderers.RestrictedRenderer;
import wonder.shaderdisplay.renderers.ScriptRenderer;
import wonder.shaderdisplay.renderers.StandardRenderer;

@ProcessDoc(doc = "DSD - dynamic shader display.\nThis stand-alone is a wrapper for lwjgl and openGL shaders.\nRun with '?' to print help.")
public class Main {

	public static final Logger logger = new SimpleLogger("DSD");
	
//	public static RunOptions options; // nullable, depends on the entry point
	public static Events events = new Events();
	
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

		@Option(name = "--verbose", desc = "Verbose output")
		public boolean verbose;
		@Option(name = "--filter", desc = "snippet filter (regex)")
		public String filter;
		
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
		
	}
	
	@OptionClass
	public static class RunOptions {
		
		@InnerOptions
		public DisplayOptions displayOptions;
		@Option(name = "--no-texture-cache", desc = "Disable texture caching")
		public boolean noTextureCache;
		@Option(name = "--hard-reload", desc = "Reload every shader file every time a change is detected\n in one of their folders (may be required with some configurations)")
		public boolean hardReload;
		@Option(name = "--vsync", desc = "Enables vsync, when used the informations given in the window title may be inaccurate")
		public boolean vsync;
		@Option(name = "--nogui", desc = "Removes the uniforms gui")
		public boolean noGui = false;
		
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
	
	public static class Events {
		
		public boolean nextFrameIsScreenshot = false;
		
	}
	
	private static class Display {
		
		public Renderer renderer;
		
	}
	
	@EntryPoint(path = "snippets", help = "Prints a list of snippets to standard output. Snippets can be filtered with --filter")
	public static void writeSnippets(SnippetsOptions options) {
		if(options.filter == null) {
			System.out.println("No filter given, did you forget --filter ?");
			return;
		}
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		Resources.scanForAndLoadSnippets();
		List<Snippet> snippets;
		try {
			snippets = Resources.filterSnippets(options.filter);
		} catch (PatternSyntaxException e) {
			Main.logger.err(e, "Invalid filter");
			return;
		}
		if(snippets.isEmpty())
			System.err.println("No matching snippets");
		for(Snippet s : snippets)
			System.out.println(s.code);
	}

	@EntryPoint(path = "snippets-list", help = "List known snippets. Snippets can be filtered with --filter")
	public static void listSnippets(SnippetsOptions options) {
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		Resources.scanForAndLoadSnippets();
		List<Snippet> snippets;
		try {
			snippets = options.filter == null ? Resources.SNIPPETS : Resources.filterSnippets(options.filter);
		} catch (PatternSyntaxException e) {
			Main.logger.err(e, "Invalid filter");
			return;
		}
		if(snippets.isEmpty())
			System.out.println("No matching snippets");
		for(Snippet s : snippets)
			System.out.println("- " + s.name);
	}
	
	@EntryPoint(path = "systeminfo", help = "Prints a number of system information, may be useful for debuging")
	public static void systemInformation() {
		GLWindow.createWindow(1, 1, false, false, null);
		GLWindow.printSystemInformation();
		GLWindow.dispose();
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "run", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void runDisplay(RunOptions options, File fragment) {
		logger.info("-- Running display --");
		
		// create display, load renderer etc
		Display display = initDisplayCapabilities(options.displayOptions, fragment, true, options.vsync);
		
		if(display == null)
			exit(); // early exit
		
		try {
			ShaderFileWatcher shaderFiles = new ShaderFileWatcher();
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			shaderFiles.startWatching(options.hardReload);
			Resources.scanForAndLoadSnippets();
			UserControls.init();
	
			reloadShaders(shaderFiles, display.renderer);
		
			// load ImGui
			ImGuiImplGlfw glfw = null;
			ImGuiImplGl3 gl3 = null;
			if(!options.noGui) {
				loadImguiDLL();
				ImGui.createContext();
				ImGui.getIO().setIniFilename(null); // remove the imgui.ini file
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
				long current = System.nanoTime();
				Time.step((current - shaderLastNano)/(float)1E9);
				shaderLastNano = current;
	
				if(shaderFiles.needShaderRecompilation())
					reloadShaders(shaderFiles, display.renderer);
				
				if(System.in.available() > 0)
					UserControls.readStdin();
				
				// -------- draw frame ---------
				
				// begin GUI
				if(!options.noGui && !events.nextFrameIsScreenshot) {
					glfw.newFrame();
					ImGui.newFrame();
					ImGui.setNextWindowPos(0, 0, ImGuiCond.Once);
					ImGui.setNextWindowCollapsed(true, ImGuiCond.Once);
					if(ImGui.begin("Shader controls"))
						UserControls.renderControls();
					display.renderer.renderControls();
					ImGui.end();
					ImGui.render();
				}
				// render the actual frame
				display.renderer.render();
				// end GUI
				if(!options.noGui && !events.nextFrameIsScreenshot)
					gl3.renderDrawData(ImGui.getDrawData());
				
				// ---------/draw frame/---------
				
				glfwSwapBuffers(GLWindow.getWindow());
				glfwPollEvents();
				
				if(events.nextFrameIsScreenshot) {
					UserControls.takeScreenshot();
					events.nextFrameIsScreenshot = false;
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
			logger.err(e, "An error occured");
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
		
		Display display = initDisplayCapabilities(options.displayOptions, fragment, false, false);
		ShaderFiles shaderFiles = new ShaderFiles();
		FrameBuffer fbo = new FrameBuffer(w, h);
		BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		int[] buffer = new int[w*h];
		Logger logger = new SimpleLogger("ffmpeg");
		
		try {
			fillInShaderFiles(shaderFiles, fragment, options.displayOptions);
			reloadShaders(shaderFiles, display.renderer);
		} catch (IOException e) {
			logger.err(e, "Could not load shaders");
		}
		fbo.bind();
		
		
		Process ffmpegProcess;
		try {
			List<String> command = new ArrayList<>();
			command.add(options.ffmpegPath);
			if(!options.ffmpegOptions.isBlank())
				command.addAll(Arrays.asList(StringUtils.splitCLIArgs(options.ffmpegOptions)));
			command.addAll(Arrays.asList(
					"-y",               // overwrite existing output file
					"-f", "image2pipe", // pipe images instead of transfering files
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
				display.renderer.render();
				fbo.readColorAttachment(buffer);
				frame.setRGB(0, 0, w, h, buffer, 0, w);
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
	
	private static Display initDisplayCapabilities(DisplayOptions options, File fragment, boolean windowVisible, boolean useVSync) {
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		ScriptRenderer.scriptLogger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		
		Display display = new Display();
		
		// validate options
		if(options.scriptLogLength == -1) {
			options.scriptLogLength = Integer.MAX_VALUE;
		} else if(options.scriptLogLength < 0) {
			logger.err("Invalid script log length: " + options.scriptLogLength);
			return null;
		}
		if(options.targetFPS <= 0) {
			logger.err("Invalid fps: " + options.targetFPS);
			return null;
		}
		
		// loading resources
		try {
			display.renderer = getSuitableRenderer(options);
			logger.debug("Using renderer: " + display.renderer.getClass().getSimpleName());
		} catch (IllegalArgumentException e) {
			logger.err(e.getMessage());
			return null;
		}

		logger.info("Creating window");
		GLWindow.createWindow(options.winWidth, options.winHeight, windowVisible, useVSync, options.forcedGLVersion);
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
	
	private static void loadImguiDLL() throws IOException {
		File dllFile = new File("imgui-java64.dll");
		if(!dllFile.exists()) {
			logger.info("Extracting imgui dll to " + dllFile.getPath());
			ProcessUtils.extractFileFromResources("/imgui-java64.dll", dllFile);
		}
		ProcessUtils.loadDLL(dllFile.getAbsolutePath()); // TODO make this work on linux/macos
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
			shaderFiles.setScriptFile(options.scriptFile);
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
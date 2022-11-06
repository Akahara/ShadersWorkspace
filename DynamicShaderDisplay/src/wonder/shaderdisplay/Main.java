package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

import java.io.File;
import java.io.IOException;

import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.loggers.SimpleLogger;
import fr.wonder.commons.systems.process.ProcessUtils;
import fr.wonder.commons.systems.process.argparser.ArgParser;
import fr.wonder.commons.systems.process.argparser.Argument;
import fr.wonder.commons.systems.process.argparser.EntryPoint;
import fr.wonder.commons.systems.process.argparser.Option;
import fr.wonder.commons.systems.process.argparser.ProcessDoc;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import wonder.shaderdisplay.renderers.FixedFileInputRenderer;
import wonder.shaderdisplay.renderers.FormatedInputRenderer;
import wonder.shaderdisplay.renderers.Renderer;
import wonder.shaderdisplay.renderers.RestrictedRenderer;
import wonder.shaderdisplay.renderers.ScriptRenderer;
import wonder.shaderdisplay.renderers.StandardRenderer;

@ProcessDoc(doc = "DSD - dynamic shader display.\nThis stand-alone is a wrapper for lwjgl and openGL shaders.\nRun with '?' to print help.")
public class Main {

	public static final Logger logger = new SimpleLogger("DSD");
	public static Options options;
	
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
	
	@Argument(name = "name", desc = "A specific snippet name", defaultValue = "_")
	@EntryPoint(path = "snippets", help = "Prints a list of snippets to standard output. Snippets can be filtered with <name>")
	public static void writeSnippets(String name) {
		try {
			String snippets = Resources.readSnippets(name);
			System.out.println(snippets);
		} catch (IOException e) {
			logger.merr(e, "Could not read snippets");
			return;
		}
	}

	@Argument(name = "name", desc = "A specific snippet name", defaultValue = "_")
	@EntryPoint(path = "listsnippets", help = "List known snippets. Snippets can be filtered with <name>")
	public static void listSnippets(String name) {
		try {
			String snippets = Resources.listSnippets(name);
			System.out.println(snippets);
		} catch (IOException e) {
			logger.merr(e, "Could not read snippets");
			return;
		}
	}
	
	@EntryPoint(path = "systeminfo", help = "Prints a number of system information, may be useful for debuging")
	public static void systemInformation(Options options) {
		Main.options = options;
		GLWindow.createWindow(1, 1);
		GLWindow.printSystemInformation();
		GLWindow.dispose();
	}
	
	public static class Options {

		@Option(name = "--geometry", shortand = "-g", desc = "The geometry shader file")
		public File geometryShaderFile;
		@Option(name = "--vertex", shortand = "-v", desc = "The vertex shader file")
		public File vertexShaderFile;
		@Option(name = "--compute", shortand = "-c", desc = "The compute shader file")
		public File computeShaderFile;
		@Option(name = "--fps", desc = "Target maximum fps (frames per second)")
		public int targetFPS = 60;
		@Option(name = "--no-texture-cache", desc = "Disable texture caching")
		public boolean noTextureCache;
		@Option(name = "--verbose", desc = "Verbose output")
		public boolean verbose;
		@Option(name = "--hard-reload", desc = "Reload every shader file every time a change is detected\n in one of their folders (may be required with some configurations)")
		public boolean hardReload;
		@Option(name = "--input-file", shortand = "-i", desc = "Input file to get the vertices data from")
		public File inputFile;
		@Option(name = "--script", desc = "External script to run and get the vertices data from")
		public File scriptFile;
		@Option(name = "--script-log", desc = "Maximum numbers of characters printed from the script output at each execution, -1 to print everything")
		public int scriptLogLength = 0;
		@Option(name = "--vsync", desc = "Enables vsync, when used the informations given in the window title may be inaccurate")
		public boolean vsync;
		@Option(name = "--force-gl-version", desc = "Forces the opengl version, use format <major>.<minor> (ie: 4.3)")
		public String forcedGLVersion;
		@Option(name = "--force-restricted-renderer", desc = "Forces the use of the restricted renderer, it can only take fragment and vertex shaders as inputs but should work even on old opengl versions")
		public boolean forceRestrictedRenderer;
		@Option(name = "--width", desc = "Sets the initial window width")
		public int winWidth = 500;
		@Option(name = "--height", desc = "Sets the initial window height")
		public int winHeight = 500;
		@Option(name = "--nogui", desc = "Removes the uniforms gui")
		public boolean noGui = false;
		
	}
	
	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "run", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void runDisplay(Options options, File fragment) {
		logger.info("-- Running display --");
		
		Main.options = options;
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		ScriptRenderer.scriptLogger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		
		if(options.scriptLogLength == -1) {
			options.scriptLogLength = Integer.MAX_VALUE;
		} else if(options.scriptLogLength < 0) {
			logger.err("Invalid script log length: " + options.scriptLogLength);
			return;
		}
		if(options.targetFPS <= 0) {
			logger.err("Invalid fps: " + options.targetFPS);
			return;
		}
		
		Renderer renderer;
		try {
			renderer = getSuitableRenderer(options);
			logger.debug("Using renderer: " + renderer.getClass().getSimpleName());
		} catch (IllegalArgumentException e) {
			logger.err(e.getMessage());
			exit(); return;
		}
		
		ShaderFileWatcher shaderFiles = new ShaderFileWatcher();
		try {
			watchFiles(shaderFiles, fragment);
		} catch (IOException e) {
			logger.merr(e, "Unable to read/watch shader files");
			exit();
		}
		
		long nextFrame = System.nanoTime();
		long lastSec = System.nanoTime();
		int frames = 0;
		long workTime = 0;
		
		logger.info("Creating window");
		
		try {
			ImGuiImplGlfw glfw = null;
			ImGuiImplGl3 gl3 = null;
			try {
				GLWindow.createWindow(options.winWidth, options.winHeight);
				if(!options.noGui) {
					loadImguiDLL();
					ImGui.createContext();
					ImGui.getIO().setIniFilename(null); // remove the imgui.ini file
					glfw = new ImGuiImplGlfw();
					gl3 = new ImGuiImplGl3();
					glfw.init(GLWindow.getWindow(), true);
					gl3.init();
				}
				renderer.loadResources();
			} catch (Error e) {
				logger.merr(e, "Unable to create the window");
				exit();
				throw new UnreachableException();
			}
			
			reloadShaders(shaderFiles, renderer);
			
			logger.info("Running shader");
		
			long shaderLastNano = System.nanoTime();
			
			while (!GLWindow.shouldDispose()) {
				long current = System.nanoTime();
				renderer.step((current - shaderLastNano)/(float)1E9);
				shaderLastNano = current;
	
				if(shaderFiles.needShaderRecompilation())
					reloadShaders(shaderFiles, renderer);
				
				// draw frame
				if(!options.noGui) {
					glfw.newFrame();
					ImGui.newFrame();
					if(ImGui.begin("Shader controls"))
						UserControls.renderControls();
					renderer.renderControls();
					ImGui.end();
					ImGui.render();
				}
				renderer.render();
				if(!options.noGui) {
					gl3.renderDrawData(ImGui.getDrawData());
				}
				glfwSwapBuffers(GLWindow.getWindow());
				glfwPollEvents();
	
				workTime += System.nanoTime() - current;
				current = System.nanoTime();
				if (current < nextFrame)
					ProcessUtils.sleep((nextFrame - current) / (int) 1E6);
				nextFrame += 1E9 / options.targetFPS;
				frames++;
				if (current > lastSec + 1E9) {
					GLWindow.setWindowTitle(String.format("Shader workspace - %d fps - %f millis per frame - %d expected fps",
							frames, workTime / 1E6 / frames, (int) (1E9 * frames / workTime)));
					lastSec = current;
					workTime = 0;
					frames = 0;
				}
			}
		} catch (Throwable e) {
			logger.merr(e);
		}
		try {
			GLWindow.dispose();
		} finally {
			exit();
		}
	}
	
	private static Renderer getSuitableRenderer(Options options) {
		int activeRenderers = 0;
		if(options.computeShaderFile != null) activeRenderers++;
		if(options.scriptFile != null) activeRenderers++;
		if(options.inputFile != null) activeRenderers++;
		if(options.forceRestrictedRenderer) activeRenderers++;
		if(activeRenderers > 1)
			throw new IllegalArgumentException("Only one can be active: restricted renderer, compute shader, script input, input file");
		
		if(options.scriptFile != null)
			return new ScriptRenderer();
		if(options.inputFile != null)
			return new FixedFileInputRenderer();
		if(options.computeShaderFile != null)
			return new StandardRenderer();
		if(options.forceRestrictedRenderer)
			return new RestrictedRenderer();
		
		return new StandardRenderer();
	}
	
	private static void loadImguiDLL() throws IOException {
		File dllFile = new File("imgui-java64.dll" /* output file path */);
		if(!dllFile.exists()) {
			logger.info("Extracting imgui dll to " + dllFile.getPath());
			ProcessUtils.extractFileFromResources("/imgui-java64.dll" /* file name in resources */, dllFile);
		}
		ProcessUtils.loadDLL(dllFile.getAbsolutePath()); // TODO make this work on linux/macos
	}
	
	private static void watchFiles(ShaderFileWatcher shaderFiles, File fragment) throws IOException {
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
		shaderFiles.startWatching();
	}
	
	private static void reloadShaders(ShaderFileWatcher shaderFiles, Renderer renderer) {
		synchronized (shaderFiles) {
			if(renderer instanceof FormatedInputRenderer)
				((FormatedInputRenderer) renderer).reloadInputFile();
			renderer.compileShaders(shaderFiles.pollShadersSources());
		}
	}
	
	public static void exit() {
		System.exit(0);
	}
	
}
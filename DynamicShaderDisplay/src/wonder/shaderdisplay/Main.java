package wonder.shaderdisplay;

import java.io.IOException;

import fr.wonder.commons.loggers.AnsiLogger;
import fr.wonder.commons.loggers.Logger;
import fr.wonder.commons.systems.process.argparser.ArgParser;
import fr.wonder.commons.systems.process.argparser.Argument;
import fr.wonder.commons.systems.process.argparser.EntryPoint;
import fr.wonder.commons.systems.process.argparser.Option;
import fr.wonder.commons.systems.process.argparser.ProcessDoc;

@ProcessDoc(doc = "DSD - dynamic shader display.\nThis stand-alone is a wrapper for lwjgl and openGL shaders.")
public class Main {

	public static final Logger logger = new AnsiLogger("DSD");
	public static Options options;
	
	public static void main(String[] args) {
		ArgParser.runHere(args);
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
	
	public static class Options {

		@Option(name = "--geometry", shortand = "-g", desc = "The geometry shader file")
		public String geometryShaderFile;
		@Option(name = "--vertex", shortand = "-v", desc = "The vertex shader file")
		public String vertexShaderFile;
		@Option(name = "--fps", desc = "Target maximum fps (frames per second)")
		public int targetFPS = 60;
		@Option(name = "--no-texture-cache", desc = "Disable texture caching")
		public boolean noTextureCache;
		@Option(name = "--verbose", desc = "Verbose output")
		public boolean verbose;
		
	}

	@Argument(name = "fragment", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "run", help = "Creates a window running the specified fragment shader. Other shaders may be specified with options.")
	public static void runDisplay(Options options, String fragment) {
		logger.info("-- Running display --");
		
		Main.options = options;
		logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
		
		ShaderFileWatcher shaderFiles = new ShaderFileWatcher();
		try {
			shaderFiles.addShaderFile(fragment, Resources.TYPE_FRAGMENT);
			if(options.geometryShaderFile != null)
				shaderFiles.addShaderFile(options.geometryShaderFile, Resources.TYPE_GEOMETRY);
			if(options.vertexShaderFile != null)
				shaderFiles.addShaderFile(options.vertexShaderFile, Resources.TYPE_VERTEX);
			shaderFiles.completeWithDefaultSources();
			shaderFiles.startWatching();
		} catch (IOException e) {
			logger.merr(e, "Unable to read/watch shader files");
			exit();
		}
		
		long lastFrame = System.nanoTime();
		long lastSec = System.nanoTime();
		int frames = 0;
		long workTime = 0;
		final int targetFPS = 60;
		
		logger.info("Creating window");
		
		try {
			GLWindow.createWindow(500, 500);
		} catch (Error e) {
			logger.merr(e, "Unable to create the window");
			exit();
		}
		
		synchronized (shaderFiles) {
			if(!GLWindow.compileShaders(shaderFiles.pollShadersSources())) {
				logger.merr("Could not compile stored shader");
				exit();
			}
		}
		
		logger.info("Running shader");
		
		try {
			while (!GLWindow.shouldDispose()) {
				long current = System.nanoTime();
	
				if(shaderFiles.needShaderRecompilation()) {
					synchronized (shaderFiles) {
						GLWindow.compileShaders(shaderFiles.pollShadersSources());
					}
				}
				
				GLWindow.render();
	
				workTime += System.nanoTime() - current;
				current = System.nanoTime();
				if (current < lastFrame) {
					try {
						Thread.sleep((lastFrame - current) / (int) 1E6);
					} catch (InterruptedException x) {
					}
				}
				lastFrame += 1E9 / targetFPS;
				frames++;
				if (current > lastSec + 1E9) {
					GLWindow.setWindowTitle(String.format("Shader workspace - %d fps - %f millis per frame - %d expected fps",
							frames, workTime / 1E6 / frames, (int) (1E9 * frames / workTime)));
					lastSec = current;
					workTime = 0;
					frames = 0;
				}
			}
		} catch (IllegalStateException e) {
			logger.merr(e);
		}
		GLWindow.dispose();
		exit();
	}
	
	public static synchronized void exit() {
		System.exit(0);
	}
}

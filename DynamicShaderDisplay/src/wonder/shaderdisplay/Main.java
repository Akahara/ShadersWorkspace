package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import fr.wonder.commons.systems.process.argparser.Argument;
import fr.wonder.commons.systems.process.argparser.EntryPoint;
import fr.wonder.commons.systems.process.argparser.ProcessArguments;
import fr.wonder.commons.systems.process.argparser.ProcessOption;
import io.methvin.watcher.DirectoryWatcher;

public class Main {

	private static DirectoryWatcher directoryWatcher;
	private static File displayedFile;
	private static Thread watcherThread;
	
	private static AtomicReference<String> nextShaderSource = new AtomicReference<>(null);
	
	public static void main(String[] args) {
		ProcessArguments.runHere("dsd", args);
	}
	
	@Argument(name = "file", desc = "The file in which to write snippets")
	@EntryPoint(path = "snippets")
	public static void writeSnippets(String file) {
		File f = new File(file);
		if(f.exists()) {
			System.err.println("File already exists.");
			exit();
		}
		
		try {
			if(!f.createNewFile())
				throw new IOException("Could not create file");
			Files.writeString(f.toPath(), readResource("/snippets.fs"));
			System.out.println("Wrote snippets to " + f.getPath());
		} catch (IOException e) {
			System.err.println("Could not write snippets: " + e.getMessage());
			exit();
		}
	}
	
	public static class RunOptions {
		@ProcessOption(name = "--screenshot", shortand = "-s", valueName = "file", desc = "Take a screenshot and save it to file")
		public File screenshotFile;
		@ProcessOption(name = "--screenshotdelay", shortand = "-d", valueName = "delay", desc = "When used with --screenshot, sets a delay before taking the screenshot")
		public float screenshotDelay = 0;
	}
	
	@Argument(name = "file", desc = "The fragment shader file", defaultValue = "shader.fs")
	@EntryPoint(path = "run")
	public static void runDisplay(RunOptions options, File file) {
		System.out.println("running display");
		
		displayedFile = file;
		displayedFile = displayedFile.getAbsoluteFile();
		String filePath = displayedFile.getAbsolutePath();
		
		if(!displayedFile.isFile()) {
			try {
				Files.writeString(displayedFile.toPath(), readResource("/defaultFragment.fs"));
			} catch (IOException e) {
				System.err.println("Unable to create file " + filePath + ": " + e.getMessage());
				exit();
			}
		}
		
		try {
			directoryWatcher = DirectoryWatcher
					.builder()
					.path(displayedFile.getParentFile().toPath())
					.listener((ev) -> {
						if(ev.path().equals(displayedFile.toPath())) {
							if(!displayedFile.exists() || !displayedFile.isFile()) {
								System.out.println("Shader file does not exist anymore");
								exit();
							}
							try {
								String nextSrc = Files.readString(displayedFile.toPath());
								nextShaderSource.set(nextSrc);
							} catch (IOException e) {
								System.err.println("Unable to read shader file");
								exit();
							}
						}
					}).build();
		} catch (IOException e) {
			System.err.println("Unable to watch file " + filePath + ": " + e.getMessage());
			exit();
		}
		
		watcherThread = new Thread(Main::watchFile, "FileWatcher");
		watcherThread.start();

		String firstShaderSource = null;
		try {
			firstShaderSource = Files.readString(displayedFile.toPath());
		} catch (IOException e) {
			System.err.println("Unable to read shader file " + filePath);
			exit();
		}
		
		long firstFrame = System.nanoTime();
		
		long lastFrame = System.nanoTime();
		long lastSec = System.nanoTime();
		int frames = 0;
		long workTime = 0;
		final int targetFPS = 60;

		GLWindow.createWindow(500, 500, firstShaderSource, readResource("/vertex.vs"));
		
		while (!GLWindow.shouldDispose()) {
			long current = System.nanoTime();

			String nextSrc = nextShaderSource.getAndSet(null);
			if(nextSrc != null)
				GLWindow.compileNewShader(nextSrc);
			GLWindow.render();
			
			workTime += System.nanoTime() - current;
			current = System.nanoTime();
			
			if(options.screenshotFile != null && (current - firstFrame)/1E9 > options.screenshotDelay) {
				try {
					File outFile = options.screenshotFile.getCanonicalFile();
					if(!outFile.getName().endsWith(".png"))
						outFile = new File(outFile.getPath() + ".png");
					if(outFile.isFile()) {
						System.err.println("The screenshot file already exists");
					} else {
						GLWindow.makeScreenshot(outFile);
					}
				} catch (IOException e) {
					System.err.println("Could not access file " + options.screenshotFile + ": " + e);
				} finally {
					options.screenshotFile = null;
				}
			}
			
			if (current < lastFrame) {
				try {
					Thread.sleep((lastFrame - current) / (int) 1E6);
				} catch (InterruptedException x) {
				}
			}
			lastFrame += 1E9 / targetFPS;
			frames++;
			if (current > lastSec + 1E9) {
				GLWindow.setWindowTitle(String.format("%s - %d fps - %f millis per frame - %d expected fps",
						displayedFile, frames, workTime / 1E6 / frames, (int) (1E9 * frames / workTime)));
				lastSec = current;
				workTime = 0;
				frames = 0;
			}
		}
		exit();
	}
	
	private static String readResource(String path) {
		try (InputStream is = Main.class.getResourceAsStream(path)) {
			if(is == null)
				throw new IOException("Resource " + path + " does not exist");
			return new String(is.readAllBytes());
		} catch (IOException e) {
			System.err.println("Could not read resource " + path + ": " + e.getMessage());
			return "";
		}
	}
	
	private static void watchFile() {
		try {
			directoryWatcher.watch();
		} catch (ClosedWatchServiceException x) {}
	}
	
	private static synchronized void exit() {
		if(watcherThread != null) {
			watcherThread.interrupt();
			watcherThread = null;
		}
		if(directoryWatcher != null) {
			try {
				directoryWatcher.close();
			} catch (IOException x) {}
			directoryWatcher = null;
		}
		GLWindow.dispose();
		System.out.println("Disposing...");
		System.exit(0);
	}
}

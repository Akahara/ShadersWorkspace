package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.types.Unit;
import io.methvin.watcher.DirectoryWatcher;

class ShaderFileWatcher {
	
	private static final int PATH_WARNING_LIMIT = 50;
	
	private final Path[] shaderFiles = new Path[Resources.SHADERS_COUNT];
	private final String[] shaderSources = new String[Resources.SHADERS_COUNT];
	private File scriptFile;
	private boolean needShaderRecompilation;
	
	public void setScriptFile(File scriptFile) {
		this.scriptFile = scriptFile;
	}
	
	public void addShaderFile(File file, int type) throws IOException {
		shaderFiles[type] = file.toPath();
		if(!file.isFile()) {
			String source = Resources.readDefaultSource(type);
			shaderSources[type] = source;
			Files.writeString(shaderFiles[type], source);
		} else {
			shaderSources[type] = Files.readString(shaderFiles[type]);
		}
	}
	
	public void completeWithDefaultSources() throws IOException {
		for(int i = 0; i < shaderSources.length; i++) {
			if(shaderSources[i] == null && Resources.REQUIRED_SHADERS[i])
				shaderSources[i] = Resources.readDefaultSource(i);
		}
	}
	
	public void startWatching() throws IOException {
		List<Path> paths = new ArrayList<>();
		for(Path f : shaderFiles)
			addWatchedPath(paths, f);
		if(scriptFile != null)
			addWatchedPath(paths, scriptFile.toPath());
		logWarningIfTooManySubDirs(paths);
		DirectoryWatcher watcher = DirectoryWatcher
				.builder()
				.paths(paths)
				.listener((ev) -> {
					synchronized (ShaderFileWatcher.this) {
						for(int i = 0; i < shaderFiles.length; i++) {
							if(shaderFiles[i] == null || (!Main.options.hardReload && !ev.path().equals(shaderFiles[i])))
								continue;
							File f = shaderFiles[i].toFile();
							if(!f.exists() || !f.isFile()) {
								Main.logger.err("Shader file does not exist anymore '" + f + "'");
								Main.exit();
							}
							try {
								Main.logger.debug("Reloading " + f);
								shaderSources[i] = FilesUtils.read(f);
								needShaderRecompilation = true;
							} catch (IOException e) {
								Main.logger.err("Unable to read shader file '" + f + "' " + e.getMessage());
								Main.exit();
							}
						}
						if(ev.path().equals(scriptFile.toPath())) {
							needShaderRecompilation = true;
							Main.logger.debug("Reloading script " + scriptFile);
						}
					}
				}).build();
		Main.logger.debug("Watching files for changes...");
		new Thread(watcher::watch, "Shader file watcher").start();
	}
	
	private void addWatchedPath(List<Path> watchedDirectories, Path toAdd) {
		if(toAdd == null)
			return;
		Path parent = toAdd.getParent();
		if(!watchedDirectories.contains(parent)) {
			watchedDirectories.add(parent);
			Main.logger.debug("Watching directory " + parent + " for file " + toAdd);
		} else {
			Main.logger.debug("Already watching directory " + parent + " for file " + toAdd);
		}
	}
	
	private void logWarningIfTooManySubDirs(List<Path> paths) {
		Unit<Integer> pathCount = new Unit<>(0);
		for(Path p : paths)
			countFiles(pathCount, p.toFile());
		if(pathCount.val > PATH_WARNING_LIMIT) {
			Main.logger.warn("The file watcher can only watch recursively, lots of directories"
					+ " detected, prefer running dsd in a somewhat empty directory");
		}
	}
	
	private void countFiles(Unit<Integer> pathCount, File dir) {
		for(File f : dir.listFiles()) {
			if(f.isDirectory()) {
				pathCount.val++;
				if(pathCount.val > PATH_WARNING_LIMIT)
					return;
				countFiles(pathCount, f);
				if(pathCount.val > PATH_WARNING_LIMIT)
					return;
			}
		}
	}
	
	/**
	 * When using this method, the caller must synchronize on the watcher instance,
	 * the returned array must not be read from while not in a synchronized block.
	 * The returned array must not be written to.
	 */
	public String[] pollShadersSources() {
		needShaderRecompilation = false;
		return shaderSources;
	}
	
	public synchronized boolean needShaderRecompilation() {
		return needShaderRecompilation;
	}

	public File getScriptFile() {
		return scriptFile;
	}
	
}

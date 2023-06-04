package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.wonder.commons.files.FilesUtils;
import io.methvin.watcher.DirectoryWatcher;

public class ShaderFileWatcher extends ShaderFiles {
	
	private static final int PATH_WARNING_LIMIT = 50;
	
	private boolean needShaderRecompilation;
	
	public void startWatching(boolean hardReload) throws IOException {
		List<Path> paths = new ArrayList<>();
		for(Path f : shaderFiles)
			addWatchedPath(paths, f);
		for(Path f : additionalFiles)
			addWatchedPath(paths, f);
		logWarningIfTooManySubDirs(paths);
		DirectoryWatcher watcher = DirectoryWatcher
				.builder()
				.paths(paths)
				.listener((ev) -> {
					synchronized (ShaderFileWatcher.this) {
						for(int i = 0; i < shaderFiles.length; i++) {
							if(shaderFiles[i] == null || (!hardReload && !ev.path().equals(shaderFiles[i])))
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
						if(additionalFiles.contains(ev.path())) {
							needShaderRecompilation = true;
							Main.logger.debug("Reloading file " + ev.path());
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
		int count = 0;
		for(Path p : paths)
			count = countFiles(p.toFile(), count);
		if(count > PATH_WARNING_LIMIT) {
			Main.logger.warn("The file watcher can only watch recursively, lots of directories"
					+ " detected, prefer running dsd in a somewhat empty directory");
		}
	}
	
	private int countFiles(File dir, int current) {
		if(current > PATH_WARNING_LIMIT)
			return current;
		for(File f : dir.listFiles()) {
			if(f.isDirectory()) {
				current++;
				current = countFiles(f, current);
				if(current > PATH_WARNING_LIMIT)
					return current;
			}
		}
		return current;
	}
	
	public synchronized boolean needShaderRecompilation() {
		return needShaderRecompilation;
	}
	
	@Override
	public synchronized String[] pollShadersSources() {
		needShaderRecompilation = false;
		return Arrays.copyOf(shaderSources, shaderSources.length);
	}
	
}

package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import fr.wonder.commons.files.FilesUtils;
import io.methvin.watcher.DirectoryWatcher;

class ShaderFileWatcher {
	
	private final Path[] shaderFiles = new Path[Resources.SHADERS_COUNT];
	private final String[] shaderSources = new String[Resources.SHADERS_COUNT];
	private boolean needShaderRecompilation;
	
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
		for(Path f : shaderFiles) {
			if(f == null) continue;
			Path parent = f.getParent();
			if(!paths.contains(parent)) {
				paths.add(parent);
				Main.logger.debug("Watching directory " + parent + " for file " + f);
			} else {
				Main.logger.debug("Already watching directory " + parent + " for file " + f);
			}
		}
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
					}
				}).build();
		new Thread(watcher::watch, "Shader File Watcher").start();
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
	
	public boolean needShaderRecompilation() {
		return needShaderRecompilation;
	}
	
}

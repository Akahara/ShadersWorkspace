package wonder.shaderdisplay;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.display.Renderer;
import wonder.shaderdisplay.scene.Macro;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FileWatcher {
	
	private static final int PATH_WARNING_LIMIT = 50;

	private final Scene scene;
	private final boolean hardReload;

	private final List<File> watchedDirectories = new ArrayList<>();
	private final Map<File, List<WatchableResourceAssociation>> watchedFiles = new HashMap<>();

	private final Set<SceneLayer> pendingShaderRecompilations = new HashSet<>();
	private boolean isSceneFileUpdatePending = false;

	private DirectoryWatcher watcher;

	public FileWatcher(Scene scene, boolean hardReload) {
		this.scene = scene;
		this.hardReload = hardReload;
	}
	
	public void startWatching() throws IOException {
		if (scene.sourceFile != null)
			addWatchedPath(scene.sourceFile, new WatchableSceneFile());

		for (SceneLayer layer : scene.layers) {
			for (ShaderType type : ShaderType.TYPES) {
				File shaderFile = layer.fileSet.getFile(type);
				if (shaderFile != null)
					addWatchedPath(shaderFile, new WatchableShaderFiles(layer));
			}
		}

		logWarningIfTooManySubDirs(watchedDirectories);

		watcher = DirectoryWatcher
				.builder()
				.paths(watchedDirectories.stream().map(File::toPath).collect(Collectors.toList()))
				.listener(this::onFileUpdate)
				.build();
		Main.logger.debug("Watching files for changes...");
		new Thread(watcher::watch, "File watcher").start();
	}

	public void stopWatching() {
		try {
			watcher.close();
		} catch (IOException e) {
			Main.logger.err("An error occurred while stopping watching files: " + e.getMessage());
		}
	}

	private synchronized void onFileUpdate(DirectoryChangeEvent ev) {
		if (ev.isDirectory()) return;

		List<WatchableResourceAssociation> associations = watchedFiles.get(ev.path().toFile());
		if (associations == null) return;

		File updatedFile = ev.path().toFile();
		if(!updatedFile.exists()) {
			Main.logger.err("File does not exist anymore '" + updatedFile + "'");
			Main.exit();
		}

		if (hardReload) {
			isSceneFileUpdatePending = true;
			return;
		}

		for (WatchableResourceAssociation association : associations) {
			if (association instanceof WatchableShaderFiles) {
				pendingShaderRecompilations.add(((WatchableShaderFiles) association).affectedLayer);
			} else if (association instanceof WatchableSceneFile) {
				isSceneFileUpdatePending = true;
			}
		}
	}
	
	private void addWatchedPath(File toAdd, WatchableResourceAssociation association) {
		toAdd = toAdd.getAbsoluteFile();
		File parent = toAdd.getParentFile();
		if (toAdd.isDirectory()) {
			throw new IllegalStateException(toAdd + " is a directory!");
		} else if (!toAdd.exists()) {
			throw new IllegalStateException(toAdd + " does not exist!");
		}

		List<WatchableResourceAssociation> associations = watchedFiles.get(toAdd);
		if (associations != null) {
			associations.add(association);
			return;
		}
		watchedFiles.put(toAdd, associations = new ArrayList<>());
		associations.add(association);

		if (!watchedDirectories.contains(parent)) {
			watchedDirectories.add(parent);
			Main.logger.debug("Watching directory " + parent + " for file " + toAdd);
		} else {
			Main.logger.debug("Already watching directory " + parent + " for file " + toAdd);
		}
	}
	
	private void logWarningIfTooManySubDirs(List<File> paths) {
		int count = 0;
		for(File f : paths)
			count = countFiles(f, count);
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

	public boolean requiresSceneRecompilation() {
		return isSceneFileUpdatePending;
	}

	public synchronized boolean processShaderRecompilation() {
		if (pendingShaderRecompilations.isEmpty()) return false;

		for (SceneLayer pendingLayer : pendingShaderRecompilations) {
			try {
				pendingLayer.fileSet.readSources();
				Renderer.compileShaders(scene, pendingLayer);
			} catch (IOException e) {
				Main.logger.err("Could not recompile a shader: " + e.getMessage());
			}
		}

		pendingShaderRecompilations.clear();

		return true;
	}
}

interface WatchableResourceAssociation {
}

class WatchableShaderFiles implements WatchableResourceAssociation {
	final SceneLayer affectedLayer;

	WatchableShaderFiles(SceneLayer affectedLayer) {
		this.affectedLayer = Objects.requireNonNull(affectedLayer);
	}
}

class WatchableSceneFile implements WatchableResourceAssociation {
}

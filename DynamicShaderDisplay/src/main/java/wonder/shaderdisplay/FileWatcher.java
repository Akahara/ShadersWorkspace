package wonder.shaderdisplay;

import fr.wonder.commons.exceptions.ErrorWrapper;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.ShaderCompiler;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;
import wonder.shaderdisplay.scene.SceneStandardLayer;
import wonder.shaderdisplay.scene.CompilableLayer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FileWatcher {
	
	private static final int PATH_WARNING_LIMIT = 50;
	// After a file change, wait that long before reloading the file just in case
	// it gets updated multiple times in a short amount of time
	private static final float DEBOUNCING_DURATION = .1f;

	private final Scene scene;
	private final boolean hardReload;

	private final List<File> watchedDirectories = new ArrayList<>();
	private final Map<File, List<WatchableResourceAssociation>> watchedFiles = new HashMap<>();

	private final Set<CompilableLayer> pendingShaderRecompilations = new HashSet<>();
	private final Set<SceneStandardLayer> pendingMeshReloads = new HashSet<>();
	private boolean isSceneFileUpdatePending = false;
	private long latestChangeTimestamp = 0;

	private DirectoryWatcher watcher;

	public FileWatcher(Scene scene, boolean hardReload) {
		this.scene = scene;
		this.hardReload = hardReload;
	}
	
	public void startWatching() throws IOException {
		if (scene.sourceFile != null)
			addWatchedPath(new WatchableSceneFile(scene.sourceFile));

		for (SceneLayer layer : scene.layers) {
			layer.collectResourceFiles().forEach(this::addWatchedPath);
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
		if (!updatedFile.exists()) {
			Main.logger.err("File does not exist anymore '" + updatedFile + "'");
			return;
		}

		latestChangeTimestamp = System.nanoTime();

		if (hardReload) {
			isSceneFileUpdatePending = true;
			return;
		}

		for (WatchableResourceAssociation association : associations) {
			if (association instanceof WatchableShaderFiles) {
				pendingShaderRecompilations.add(((WatchableShaderFiles) association).affectedLayer);
			} else if (association instanceof WatchableSceneFile) {
				isSceneFileUpdatePending = true;
			} else if (association instanceof WatchableMeshFile) {
				pendingMeshReloads.add(((WatchableMeshFile) association).affectedLayer);
			}
		}
	}
	
	private void addWatchedPath(WatchableResourceAssociation association) {
		File toAdd = association.watchedFile.getAbsoluteFile();
		File parent;
		try {
			parent = toAdd.getParentFile().getCanonicalFile();
		} catch (IOException e) {
			throw new IllegalStateException("Could not resolve the parent of " + toAdd.getAbsolutePath());
		}
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

	public synchronized boolean isDebouncingRecompilation() {
		return System.nanoTime() - latestChangeTimestamp < 1e9 * DEBOUNCING_DURATION;
	}

	public synchronized boolean hasPendingChanges() {
		return isSceneFileUpdatePending || !pendingMeshReloads.isEmpty() || !pendingShaderRecompilations.isEmpty();
	}

	public boolean requiresSceneRecompilation() {
		return isSceneFileUpdatePending;
	}

	public ShaderCompiler.ShaderCompilationResult processShaderRecompilation() {
		if (pendingShaderRecompilations.isEmpty())
			return ShaderCompiler.ShaderCompilationResult.success();

		ShaderCompiler compiler = new ShaderCompiler(scene);
		ShaderCompiler.ShaderCompilationResult result = new ShaderCompiler.ShaderCompilationResult();

		for (CompilableLayer pendingLayer : pendingShaderRecompilations) {
			ErrorWrapper errors = new ErrorWrapper("Could not compile shader " + pendingLayer.getCompilationFileset().getPrimaryFileName());
			ShaderCompiler.ShaderCompilationResult r = compiler.compileShaders(errors, pendingLayer);
			result.success &= r.success;
			result.fileDependenciesUpdated |= r.fileDependenciesUpdated;
			if (!r.success)
				errors.dump(Main.logger);
		}

		pendingShaderRecompilations.clear();

		return result;
	}

	public void processDummyFilesRecompilation() {
		for (SceneStandardLayer pendingLayer : pendingMeshReloads) {
			try {
				pendingLayer.mesh = Mesh.parseFile(pendingLayer.mesh.getSourceFile());
			} catch (IOException e) {
				Main.logger.err("Could not reload mesh '" + pendingLayer.mesh.getSourceFile() + "': " + e.getMessage());
			}
		}

		pendingMeshReloads.clear();
	}


	public static class WatchableResourceAssociation {

		private final File watchedFile;

        public WatchableResourceAssociation(File watchedFile) {
            this.watchedFile = watchedFile;
        }

    }

	public static class WatchableShaderFiles extends WatchableResourceAssociation {
		private final CompilableLayer affectedLayer;

		public WatchableShaderFiles(File shaderFile, CompilableLayer affectedLayer) {
			super(shaderFile);
			this.affectedLayer = Objects.requireNonNull(affectedLayer);
		}
	}

	public static class WatchableSceneFile extends WatchableResourceAssociation {
		public WatchableSceneFile(File sceneFile) {
			super(sceneFile);
		}
	}

	public static class WatchableMeshFile extends WatchableResourceAssociation {

		private final SceneStandardLayer affectedLayer;

		public WatchableMeshFile(File shaderFile, SceneStandardLayer affectedLayer) {
			super(shaderFile);
			this.affectedLayer = Objects.requireNonNull(affectedLayer);
		}
	}
}

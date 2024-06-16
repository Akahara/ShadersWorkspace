package wonder.shaderdisplay.display;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Resources;

import java.io.File;
import java.io.IOException;

public class ShaderFileSet {

	private final File[][] filePaths = new File[ShaderType.COUNT][];
	private final String[][] cachedSources;

	public ShaderFileSet() {
		cachedSources = new String[ShaderType.COUNT][];
		for (ShaderType type : ShaderType.STANDARD_TYPES)
			cachedSources[type.ordinal()] = new String[] { Resources.DEFAULT_SHADER_SOURCES[type.ordinal()] };
	}

	public boolean isCompute() {
		return hasCustomShader(ShaderType.COMPUTE);
	}

	public File[] getFiles(ShaderType type) {
		return filePaths[type.ordinal()];
	}

	public boolean hasCustomShader(ShaderType type) {
		return filePaths[type.ordinal()] != null;
	}

	public String[] getSources(ShaderType type) {
		return cachedSources[type.ordinal()];
	}

	public ShaderFileSet setFile(ShaderType type, File file) {
		return setFiles(type, file == null ? null : new File[] { file });
	}

	public ShaderFileSet setFiles(ShaderType type, File[] files) {
		if (hasCustomShader(type))
			throw new IllegalArgumentException("A " + type.name() + " shader is already specified");
		if (files == null)
			return this;
		if (files.length == 0)
			throw new IllegalArgumentException("Cannot specify an empty set of shader files");
		if (type == ShaderType.COMPUTE
				? (filePaths[ShaderType.VERTEX.ordinal()] != null || filePaths[ShaderType.FRAGMENT.ordinal()] != null || filePaths[ShaderType.GEOMETRY.ordinal()] != null)
				: (filePaths[ShaderType.COMPUTE.ordinal()] != null))
			throw new IllegalArgumentException("A compute shader cannot be specified with other types of shaders");

		filePaths[type.ordinal()] = files;
		cachedSources[type.ordinal()] = new String[files.length];
		return this;
	}

	public ShaderFileSet readSources() throws IOException {
		for (int i = 0; i < ShaderType.COUNT; i++) {
			if (filePaths[i] == null)
				continue;
			for (int j = 0; j < filePaths[i].length; j++) {
				try {
					cachedSources[i][j] = FilesUtils.read(filePaths[i][j]);
				} catch (IOException e) {
					throw new IOException("Could not read " + ShaderType.TYPES[i].name() + " shader " + filePaths[i][j] + ": " + e.getMessage());
				}
			}
		}
		return this;
	}

	public String getFinalFileName(ShaderType type) {
		if (hasCustomShader(type))
			return filePaths[type.ordinal()][0].getName();
		if (type == ShaderType.GEOMETRY)
			throw new IllegalArgumentException("No geometry shader specified, that one should not be assumed to exist");
		return "default-" + type.name().toLowerCase();
	}

	public String getPrimaryFileName() {
		return isCompute() ? getFinalFileName(ShaderType.COMPUTE) : getFinalFileName(ShaderType.FRAGMENT);
	}
}

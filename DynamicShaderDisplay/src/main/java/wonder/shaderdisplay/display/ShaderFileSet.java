package wonder.shaderdisplay.display;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ShaderFileSet {

	private final File[] filePaths = new File[ShaderType.values().length];
	private final String[] cachedSources = Resources.getDefaultShaderSources();

	public boolean isCompute() {
		return hasCustomShader(ShaderType.COMPUTE);
	}

	public File getFile(ShaderType type) {
		return filePaths[type.ordinal()];
	}

	public boolean hasCustomShader(ShaderType type) {
		return filePaths[type.ordinal()] != null;
	}

	public String getSource(ShaderType type) {
		return cachedSources[type.ordinal()];
	}

	public ShaderFileSet setFile(ShaderType type, File file) {
		if (hasCustomShader(type))
			throw new IllegalArgumentException("A " + type.name() + " shader is already specified");
		if (file == null)
			return this;
		if (type == ShaderType.COMPUTE
				? (filePaths[ShaderType.VERTEX.ordinal()] != null || filePaths[ShaderType.FRAGMENT.ordinal()] != null || filePaths[ShaderType.GEOMETRY.ordinal()] != null)
				: (filePaths[ShaderType.COMPUTE.ordinal()] != null))
			throw new IllegalArgumentException("A compute shader cannot be specified with other types of shaders");

		filePaths[type.ordinal()] = file;
		return this;
	}

	public ShaderFileSet readSources() throws IOException {
		for (int i = 0; i < filePaths.length; i++) {
			if (filePaths[i] != null) {
				try {
					cachedSources[i] = FilesUtils.read(filePaths[i]);
				} catch (IOException e) {
					throw new IOException("Could not read " + ShaderType.values()[i].name() + " shader " + filePaths[i] + ": " + e.getMessage());
				}
			}
		}
		return this;
	}

	public String getFinalFileName(ShaderType type) {
		if (hasCustomShader(type))
			return filePaths[type.ordinal()].getName();
		if (type == ShaderType.GEOMETRY)
			throw new IllegalArgumentException("No geometry shader specified, that one should not be assumed to exist");
		return "default-" + type.name().toLowerCase();
	}

	public String getPrimaryFileName() {
		return isCompute() ? getFinalFileName(ShaderType.COMPUTE) : getFinalFileName(ShaderType.FRAGMENT);
	}
}

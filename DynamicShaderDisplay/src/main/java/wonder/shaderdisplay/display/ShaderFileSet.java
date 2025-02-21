package wonder.shaderdisplay.display;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.serial.Resources;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

public class ShaderFileSet {

    public static class ShaderSource {
		private final File fileSource;
		private final String rawSource;

		ShaderSource(File fileSource) {
			this.fileSource = fileSource;
			this.rawSource = null;
		}

		ShaderSource(String rawSource) {
			this.fileSource = null;
			this.rawSource = rawSource;
		}

		public boolean isRawSource() {
			return rawSource != null;
		}

		public boolean isFileSource() {
			return !isRawSource();
		}

		public String getRawSource() {
			if (!isRawSource())
				throw new IllegalStateException("Not a raw source");
			return rawSource;
		}

		public File getFileSource() {
			if (!isFileSource())
				throw new IllegalStateException("Not a file source");
			return fileSource;
		}

	}

	private final ShaderSource[] sources = new ShaderSource[ShaderType.COUNT];
	private String fixedPrimarySourceName;

	public boolean isCompute() {
		return hasCustomShader(ShaderType.COMPUTE);
	}

	public ShaderSource getSource(ShaderType type) {
		return sources[type.ordinal()];
	}

	public boolean hasCustomShader(ShaderType type) {
		return sources[type.ordinal()] != null;
	}

	public ShaderFileSet setFixedPrimarySourceName(String name) {
		this.fixedPrimarySourceName = Objects.requireNonNull(name);
		return this;
	}

	public ShaderFileSet setFile(ShaderType type, File file) {
		if (hasCustomShader(type))
			throw new IllegalArgumentException("A " + type.name() + " shader is already specified");
		if (file == null)
			return this;
		if (type == ShaderType.COMPUTE
				? (sources[ShaderType.VERTEX.ordinal()] != null || sources[ShaderType.FRAGMENT.ordinal()] != null || sources[ShaderType.GEOMETRY.ordinal()] != null)
				: (sources[ShaderType.COMPUTE.ordinal()] != null))
			throw new IllegalArgumentException("A compute shader cannot be specified with other types of shaders");

		sources[type.ordinal()] = new ShaderSource(file);
		return this;
	}

	public ShaderFileSet setRawSource(ShaderType type, String rawSource) {
		if (fixedPrimarySourceName == null)
			throw new IllegalStateException("Raw source can only be specified for named shader sets");
		if (hasCustomShader(type))
			throw new IllegalArgumentException("A " + type.name() + " shader is already specified");
		if (rawSource == null)
			return this;
		if (type == ShaderType.COMPUTE
				? Stream.of(ShaderType.NON_COMPUTE_TYPES).anyMatch(t -> sources[t.ordinal()] != null)
				: (sources[ShaderType.COMPUTE.ordinal()] != null))
			throw new IllegalArgumentException("A compute shader cannot be specified with other types of shaders");

		sources[type.ordinal()] = new ShaderSource(rawSource);
		return this;
	}

	public ShaderFileSet completeWithDefaultSources() {
		if (isCompute())
			return this;

		if (!hasCustomShader(ShaderType.VERTEX))
			sources[ShaderType.VERTEX.ordinal()] = new ShaderSource(Resources.DEFAULT_SHADER_SOURCES[ShaderType.VERTEX.ordinal()]);

		return this;
	}

	public String getPrimaryFileName() {
		return fixedPrimarySourceName != null ? fixedPrimarySourceName
			: isCompute() ? getSource(ShaderType.COMPUTE).getFileSource().getName()
			: getSource(ShaderType.FRAGMENT).getFileSource().getName();
	}

	public ShaderFileSet createMissingFilesFromTemplate() {
		for (ShaderType type : ShaderType.values()) {
			ShaderSource source = sources[type.ordinal()];
			if (source != null && source.isFileSource() && !source.getFileSource().exists()) {
				File shaderFile = source.getFileSource();
                try {
                    FilesUtils.write(shaderFile, Resources.DEFAULT_SHADER_SOURCES[type.ordinal()]);
					Main.logger.warn("Shader file " + shaderFile + " does not exist, created it from a template");
                } catch (IOException e) {
					Main.logger.err(e, "Could not create missing file " + shaderFile);
                }
            }
		}
		return this;
	}
}

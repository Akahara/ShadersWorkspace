package wonder.shaderdisplay.display;

import wonder.shaderdisplay.Resources;

import java.io.File;
import java.util.Objects;

public class ShaderFileSet {

	public static class ShaderSource {
		private final File fileSource;
		private final String rawSource;
		private String cachedResolvedSource;

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

		public String getRawSource() {
			return rawSource;
		}

		public File getFileSource() {
			if (isRawSource())
				throw new IllegalStateException("Not a file source");
			return fileSource;
		}

		public void updateCachedResolvedSource(String resolvedSource) {
			this.cachedResolvedSource = resolvedSource;
		}

		public String getCachedResolvedSource() {
			return cachedResolvedSource;
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
				? (sources[ShaderType.VERTEX.ordinal()] != null || sources[ShaderType.FRAGMENT.ordinal()] != null || sources[ShaderType.GEOMETRY.ordinal()] != null)
				: (sources[ShaderType.COMPUTE.ordinal()] != null))
			throw new IllegalArgumentException("A compute shader cannot be specified with other types of shaders");

		sources[type.ordinal()] = new ShaderSource(rawSource);
		return this;
	}

	public ShaderFileSet completeWithDefaultSources() {
		if (!hasCustomShader(ShaderType.VERTEX))
			sources[ShaderType.VERTEX.ordinal()] = new ShaderSource(Resources.DEFAULT_SHADER_SOURCES[ShaderType.VERTEX.ordinal()]);
		return this;
	}

	public String getPrimaryFileName() {
		return fixedPrimarySourceName != null ? fixedPrimarySourceName
			: isCompute() ? getSource(ShaderType.COMPUTE).getFileSource().getName()
			: getSource(ShaderType.FRAGMENT).getFileSource().getName();
	}
}

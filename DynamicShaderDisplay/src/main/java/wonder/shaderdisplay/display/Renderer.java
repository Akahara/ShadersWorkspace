package wonder.shaderdisplay.display;

import fr.wonder.commons.exceptions.GenerationException;
import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.scene.Macro;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;

public class Renderer {

	public void render(Scene scene) {
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		for (SceneLayer layer : scene.layers) {
			glUseProgram(layer.compiledShaders.program);
			setupRenderState(layer.renderState);
			layer.shaderUniforms.apply();
			layer.mesh.makeDrawCall();
		}
		setupRenderState(SceneLayer.RenderState.DEFAULT);
	}

	private void setupRenderState(SceneLayer.RenderState rs) {
		if (rs.isBlendingEnabled)
			glEnable(GL_BLEND);
		else
			glDisable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		glEnable(GL_DEPTH_TEST);
		glDepthFunc(rs.isDepthTestEnabled ? GL_LESS : GL_ALWAYS);
		glDepthMask(rs.isDepthWriteEnabled);

		if (rs.culling != SceneLayer.RenderState.Culling.NONE) {
			glEnable(GL_CULL_FACE);
			glCullFace(rs.culling == SceneLayer.RenderState.Culling.FRONT ? GL_FRONT : GL_BACK);
		} else {
			glDisable(GL_CULL_FACE);
		}
	}

	public static boolean compileShaders(Scene scene, SceneLayer layer) {
		int[] newFragments = null, newVertices = null, newGeometries = null;
		int newStandardProgram = 0;

		ShaderFileSet shadersFiles = layer.fileSet;
		SceneLayer.ShaderSet shaders = layer.compiledShaders;

		boolean hasGeometry = shadersFiles.hasCustomShader(ShaderType.GEOMETRY);

		try {
			if (shadersFiles.isCompute())
				throw new GenerationException("Unimplemented: compute pass");
			verifyGeometryShaderInputType(shadersFiles, GL_TRIANGLES);

			newVertices = buildShaders(scene, layer, ShaderType.VERTEX, GL_VERTEX_SHADER);
			newFragments = buildShaders(scene, layer, ShaderType.FRAGMENT, GL_FRAGMENT_SHADER);
			if(hasGeometry)
				newGeometries = buildShaders(scene, layer, ShaderType.GEOMETRY, GL_GEOMETRY_SHADER);

			if(newFragments == null || newVertices == null || (hasGeometry && newGeometries == null))
				throw new GenerationException("Could not compile a shader");
			
			newStandardProgram = glCreateProgram();
			for (int fs : newFragments) glAttachShader(newStandardProgram, fs);
			for (int vs : newVertices) glAttachShader(newStandardProgram, vs);
			if (hasGeometry) for (int vs : newGeometries) glAttachShader(newStandardProgram, vs);
			glLinkProgram(newStandardProgram);
			
			if (glGetProgrami(newStandardProgram, GL_LINK_STATUS) == GL_FALSE)
				throw new GenerationException("Linking error: " + glGetProgramInfoLog(newStandardProgram).strip());
			glValidateProgram(newStandardProgram);
			if (glGetProgrami(newStandardProgram, GL_VALIDATE_STATUS) == GL_FALSE)
				throw new GenerationException("Unexpected error: " + glGetProgramInfoLog(newStandardProgram).strip());
		} catch (GenerationException e) {
			Main.logger.warn(e.getMessage());
			deleteShaders(newVertices);
			deleteShaders(newGeometries);
			deleteShaders(newFragments);
			deletePrograms(newStandardProgram);
			return false;
		}
		
		Main.logger.info("Compiled successfully " + layer.fileSet.getPrimaryFileName() + " : " + getCompilationTimestampString());

		shaders.disposeAll();
		shaders.program = newStandardProgram;
		shaders.vertex = newVertices;
		shaders.geometry = newGeometries;
		shaders.fragment = newFragments;
		shaders.compute = 0;

		StringBuilder pseudoTotalSource = new StringBuilder();
		for (ShaderType type : ShaderType.STANDARD_TYPES) {
			if (shadersFiles.hasCustomShader(type)) {
				for (String source : shadersFiles.getSources(type))
					pseudoTotalSource.append(source);
			}
		}

		layer.shaderUniforms.rescan(newStandardProgram, pseudoTotalSource.toString());
		return true;
	}
	
	private static void verifyGeometryShaderInputType(ShaderFileSet shaders, int glDrawMode) throws GenerationException {
		if (!shaders.hasCustomShader(ShaderType.GEOMETRY))
			return;

		String expected =
			glDrawMode == GL_LINES ? "lines" :
			glDrawMode == GL_POINTS ? "points" :
			glDrawMode == GL_TRIANGLES ? "triangles" :
			null;
		if (expected == null)
			throw new IllegalArgumentException("Unknown draw mode");

		for (String gs : shaders.getSources(ShaderType.GEOMETRY)) {
			Matcher m = Pattern.compile("layout\\((\\w+)\\) in;").matcher(gs);
			if (!m.find())
				continue;
			String in = m.group(1);
			if (!in.equals(expected))
				throw new GenerationException("The geometry shader input type does not match the provided type, expected '" + expected + "', got '" + in + "'");
			return;
		}
		throw new GenerationException("The geometry shader is missing its 'layout(...) in;' directive");
	}
	
	private static String getCompilationTimestampString() {
		LocalDateTime time = LocalDateTime.now();
		int hour = time.getHour();
		int minute = time.getMinute();
		int second = time.getSecond();
		int millis = time.get(ChronoField.MILLI_OF_SECOND);
		return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millis);
	}

	private static String patchShaderSource(Scene scene, SceneLayer layer, String originalSource) {
		StringBuilder sb = new StringBuilder();
		for (Macro macro : Stream.concat(scene.macros.stream(), Stream.of(layer.macros)).toList()) {
			sb.append("#define ").append(macro.name).append(' ').append(macro.value).append('\n');
		}
		return originalSource.replaceFirst("\n", sb.toString());
	}

	public static int[] buildShaders(Scene scene, SceneLayer layer, ShaderType type, int glType) {
		String[] sources = layer.fileSet.getSources(type);
		int[] ids = new int[sources.length];
		for (int i = 0; i < sources.length; i++) {
			int id = ids[i] = glCreateShader(glType);
			String source = patchShaderSource(scene, layer, sources[i]);
			glShaderSource(id, source);
			glCompileShader(id);
			if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
				String patchInfo = (scene.macros.isEmpty() && layer.macros.length == 0) ? "" :
						(Main.logger.getLogLevel() > Logger.LEVEL_DEBUG) ? "(patched)" : "(Line number information patched, numbers might not be accurate)";
				Main.logger.warn("Compilation error in '" + layer.fileSet.getFinalFileName(type) + "': " + patchInfo);
				String errorMessage = glGetShaderInfoLog(id);
				errorMessage = patchErrorMessage(errorMessage, scene, layer, sources[i]);
				for(String line : errorMessage.strip().split("\n"))
					Main.logger.warn("  " + line);
				deleteShaders(ids);
				return null;
			}
		}
		return ids;
	}

	public static int buildRawShader(String source, int glType) {
		int id = glCreateShader(glType);
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
			throw new RuntimeException("Compilation error: " + glGetShaderInfoLog(id));
		return id;
	}

	public static String patchErrorMessage(String errorMessage, Scene scene, SceneLayer failedLayer, String failedShaderSource) {
		// Because we insert macros in the source code of each shader error messages contain messed
		// up line information, we offset those by the number of macros we inserted to get back the
		// real line numbers
		// Because glGetShaderInfoLog is implementation dependant and that we do not know which line
		// is broken, we offset *every* number that could be a line number

		int macroLinesDiff = scene.macros.size() + failedLayer.macros.length;
		if (macroLinesDiff == 0) return errorMessage;

		Matcher m = Pattern.compile("[^.](\\d+)[^.]").matcher(errorMessage);
		StringBuilder sb = new StringBuilder(errorMessage);
		int lineCount = 0;
		for (int o = -1; (o = failedShaderSource.indexOf("\n", o+1)) != -1; )
			lineCount++;

		int nextStart = 0;
		while (m.find(nextStart)) {
			nextStart = m.start(1);
			int lineNumber = Integer.parseInt(m.group(1));
			if (lineNumber < macroLinesDiff || lineNumber > lineCount)
				continue; // probably not a line number
			int patchedLineNumber = lineNumber - macroLinesDiff;
			int replacementOffset = sb.length() - errorMessage.length();
			sb.replace(m.start(1) + replacementOffset, m.end(1) + replacementOffset, String.valueOf(patchedLineNumber));
		}
		return sb.toString();
	}
	
	public static void deleteShaders(int... shaders) {
		if (shaders == null) return;
		for(int s : shaders) {
			if(s > 0)
				glDeleteShader(s);
		}
	}
	
	public static void deletePrograms(int... programs) {
		for(int s : programs) {
			if(s > 0)
				glDeleteProgram(s);
		}
	}
	
}

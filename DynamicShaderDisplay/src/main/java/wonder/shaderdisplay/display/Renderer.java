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
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

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
		int newFragment = 0, newVertex = 0, newGeometry = 0;
		int newStandardProgram = 0;

		ShaderFileSet shadersFiles = layer.fileSet;
		SceneLayer.ShaderSet shaders = layer.compiledShaders;

		try {
			if (shadersFiles.isCompute())
				throw new GenerationException("Unimplemented: compute pass");
			verifyGeometryShaderInputType(shadersFiles, GL_TRIANGLES);

			newVertex = buildShader(scene, layer, ShaderType.VERTEX, GL_VERTEX_SHADER);
			newFragment = buildShader(scene, layer, ShaderType.FRAGMENT, GL_FRAGMENT_SHADER);
			if(shadersFiles.hasCustomShader(ShaderType.GEOMETRY))
				newGeometry = buildShader(scene, layer, ShaderType.GEOMETRY, GL_GEOMETRY_SHADER);

			if(newFragment == -1 || newVertex == -1 || newGeometry == -1)
				throw new GenerationException("Could not compile a shader");
			
			newStandardProgram = glCreateProgram();
			glAttachShader(newStandardProgram, newFragment);
			glAttachShader(newStandardProgram, newVertex);
			if (newGeometry != 0) glAttachShader(newStandardProgram, newGeometry);
			glLinkProgram(newStandardProgram);
			
			if (glGetProgrami(newStandardProgram, GL_LINK_STATUS) == GL_FALSE)
				throw new GenerationException("Linking error: " + glGetProgramInfoLog(newStandardProgram).strip());
			glValidateProgram(newStandardProgram);
			if (glGetProgrami(newStandardProgram, GL_VALIDATE_STATUS) == GL_FALSE)
				throw new GenerationException("Unexpected error: " + glGetProgramInfoLog(newStandardProgram).strip());
		} catch (GenerationException e) {
			Main.logger.warn(e.getMessage());
			deleteShaders(newVertex, newGeometry, newFragment);
			deletePrograms(newStandardProgram);
			return false;
		}
		
		Main.logger.info("Compiled successfully " + getCompilationTimestampString());
		
		deleteShaders(shaders.vertex, shaders.geometry, shaders.fragment, shaders.compute);
		deletePrograms(shaders.program);
		glUseProgram(newStandardProgram);
		shaders.program = newStandardProgram;
		shaders.vertex = newVertex;
		shaders.geometry = newGeometry;
		shaders.fragment = newFragment;
		shaders.compute = 0;

		if(!Texture.isUsingCache())
			Texture.unloadTextures();

		StringBuilder pseudoTotalSource = new StringBuilder();
		for (ShaderType type : ShaderType.STANDARD_TYPES) {
			if (shadersFiles.hasCustomShader(type))
				pseudoTotalSource.append(shadersFiles.getSource(type));
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

		Matcher m = Pattern.compile("layout\\((\\w+)\\) in;").matcher(shaders.getSource(ShaderType.GEOMETRY));
		if (!m.find())
			throw new GenerationException("The geometry shader is missing its 'layout(...) in;' directive");
		String in = m.group(1);
		if (!in.equals(expected))
			throw new GenerationException("The geometry shader input type does not match the provided type, expected '" + expected + "', got '" + in + "'");
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

	public static int buildShader(Scene scene, SceneLayer layer, ShaderType type, int glType) {
		int id = glCreateShader(glType);
		String source = patchShaderSource(scene, layer, layer.fileSet.getSource(type));
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
			String patchInfo = (scene.macros.isEmpty() && layer.macros.length == 0) ? "" :
					(Main.logger.getLogLevel() > Logger.LEVEL_DEBUG) ? "(patched)" : "(Line number information patched, numbers might not be accurate)";
			Main.logger.warn("Compilation error in '" + layer.fileSet.getFinalFileName(type) + "': " + patchInfo);
			String errorMessage = glGetShaderInfoLog(id);
			errorMessage = patchErrorMessage(errorMessage, scene, layer);
			for(String line : errorMessage.strip().split("\n"))
				Main.logger.warn("  " + line);
			glDeleteShader(id);
			return -1;
		}
		return id;
	}

	public static int buildRawShader(String source, int glType) {
		int id = glCreateShader(glType);
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
			throw new RuntimeException("Compilation error: " + glGetShaderInfoLog(id));
		return id;
	}

	public static String patchErrorMessage(String errorMessage, Scene scene, SceneLayer failedLayer) {
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
		for (int o = -1; (o = sb.indexOf("\n", o+1)) != -1; )
			lineCount++;

		while (m.find()) {
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

package wonder.shaderdisplay.display;

import fr.wonder.commons.exceptions.GenerationException;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.BufferUtils;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

public class Renderer {

	public void render(Scene scene) {
		glClear(GL_COLOR_BUFFER_BIT);

		for (SceneLayer layer : scene.layers) {
			glUseProgram(layer.compiledShaders.program);
			layer.shaderUniforms.apply();
			layer.mesh.makeDrawCall();
		}
	}

	public static boolean compileShaders(SceneLayer layer) {
		int newFragment = 0, newVertex = 0, newGeometry = 0;
		int newStandardProgram = 0;

		ShaderFileSet shadersFiles = layer.fileSet;
		SceneLayer.ShaderSet shaders = layer.compiledShaders;

		try {
			if (shadersFiles.isCompute())
				throw new GenerationException("Unimplemented: compute pass");
			verifyGeometryShaderInputType(shadersFiles, GL_TRIANGLES);

			newVertex = buildShader(shadersFiles.getFinalFileName(ShaderType.VERTEX), shadersFiles.getSource(ShaderType.VERTEX), GL_VERTEX_SHADER);
			newFragment = buildShader(shadersFiles.getFinalFileName(ShaderType.FRAGMENT), shadersFiles.getSource(ShaderType.FRAGMENT), GL_FRAGMENT_SHADER);
			if(shadersFiles.hasCustomShader(ShaderType.GEOMETRY))
				newGeometry = buildShader(shadersFiles.getFinalFileName(ShaderType.GEOMETRY), shadersFiles.getSource(ShaderType.GEOMETRY), GL_GEOMETRY_SHADER);

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
	
	public static int buildShader(String shaderName, String source, int glType) {
		int id = glCreateShader(glType);
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
			Main.logger.warn("Compilation error in '" + shaderName + "': ");
			for(String line : glGetShaderInfoLog(id).strip().split("\n"))
				Main.logger.warn("  " + line);
			glDeleteShader(id);
			return -1;
		}
		return id;
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

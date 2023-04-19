package wonder.shaderdisplay.renderers;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.wonder.commons.exceptions.GenerationException;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.Texture;
import wonder.shaderdisplay.uniforms.UniformsContext;

public abstract class Renderer {
	
	protected UniformsContext standardShaderUniforms = new UniformsContext(), computeShaderUniforms = new UniformsContext();
	protected int standardShaderProgram, computeShaderProgram;
	protected int vertexShader, geometryShader, fragmentShader, computeShader;
	
	public abstract void loadResources();
	public abstract void render();
	public abstract void renderControls();
	
	public boolean compileShaders(String[] shaders) {
		int newFragment = 0, newVertex = 0, newGeometry = 0, newCompute = 0;
		int newStandardProgram = 0, newComputeProgram = 0;
		try {
			newVertex = buildShader(shaders[Resources.TYPE_VERTEX], GL_VERTEX_SHADER);
			newFragment = buildShader(shaders[Resources.TYPE_FRAGMENT], GL_FRAGMENT_SHADER);
			if(shaders[Resources.TYPE_GEOMETRY] != null)
				newGeometry = buildShader(shaders[Resources.TYPE_GEOMETRY], GL_GEOMETRY_SHADER);
			if(shaders[Resources.TYPE_COMPUTE] != null)
				newCompute = buildShader(shaders[Resources.TYPE_COMPUTE], GL_COMPUTE_SHADER);
			
			if(newFragment == -1 || newVertex == -1 || newGeometry == -1 || newCompute == -1)
				throw new GenerationException("Could not compile a shader");
			
			newStandardProgram = glCreateProgram();
			glAttachShader(newStandardProgram, newFragment);
			glAttachShader(newStandardProgram, newVertex);
			if(newGeometry != 0) glAttachShader(newStandardProgram, newGeometry);
			glLinkProgram(newStandardProgram);
			
			if(glGetProgrami(newStandardProgram, GL_LINK_STATUS) == GL_FALSE)
				throw new GenerationException("Linking error: " + glGetProgramInfoLog(newStandardProgram).strip());
			glValidateProgram(newStandardProgram);
			if(glGetProgrami(newStandardProgram, GL_VALIDATE_STATUS) == GL_FALSE)
				throw new GenerationException("Unexpected error: " + glGetProgramInfoLog(newStandardProgram).strip());
		
			if(newCompute != 0) {
				newComputeProgram = glCreateProgram();
				glAttachShader(newComputeProgram, newCompute);
				glLinkProgram(newComputeProgram);
				if(glGetProgrami(newComputeProgram, GL_LINK_STATUS) == GL_FALSE)
					throw new GenerationException("Linking error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
				glValidateProgram(newComputeProgram);
				if(glGetProgrami(newComputeProgram, GL_VALIDATE_STATUS) == GL_FALSE)
					throw new GenerationException("Unexpected error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
			}
		} catch (GenerationException e) {
			Main.logger.warn(e.getMessage());
			deleteShaders(newVertex, newGeometry, newFragment, newCompute);
			deletePrograms(newStandardProgram, newComputeProgram);
			return false;
		}
		
		Main.logger.info("Compiled successfully " + getCompilationTimestampString());
		
		deleteShaders(vertexShader, geometryShader, fragmentShader, computeShader);
		deletePrograms(standardShaderProgram, computeShaderProgram);
		glUseProgram(newStandardProgram);
		standardShaderProgram = newStandardProgram;
		computeShaderProgram = newComputeProgram;
		vertexShader = newVertex;
		geometryShader = newGeometry;
		fragmentShader = newFragment;
		computeShader = newCompute;
		
		if(!Texture.isUsingCache())
			Texture.unloadTextures();
		
		String pseudoTotalSource = Resources.concatStandardShaderSource(shaders);
		standardShaderUniforms.scan(newStandardProgram, pseudoTotalSource);
		
		String pseudoComputeSource = Resources.concatComputeShaderSource(shaders);
		computeShaderUniforms.scan(newComputeProgram, pseudoComputeSource);
		
		return true;
	}
	
	protected void checkGeometryShaderInputType(String geometrySource, int glDrawMode) {
		if(geometrySource == null)
			return;
		Matcher m = Pattern.compile("layout\\((\\w+)\\) in;").matcher(geometrySource);
		if(m.find()) {
			String in = m.group(1);
			String expected =
					glDrawMode == GL_LINES ? "lines" :
					glDrawMode == GL_POINTS ? "points" :
					glDrawMode == GL_TRIANGLES ? "triangles" :
					null;
			if(expected == null)
				throw new IllegalArgumentException("Unknown draw mode");
			if(!in.equals(expected))
				Main.logger.err("The geometry shader input type does not match the provided type, expected '" + expected + "', got '" + in + "'");
		} else {
			Main.logger.err("The geometry shader is missing its 'layout(...) in;' directive");
		}
	}
	
	private String getCompilationTimestampString() {
		LocalDateTime time = LocalDateTime.now();
		int hour = time.getHour();
		int minute = time.getMinute();
		int second = time.getSecond();
		int millis = time.get(ChronoField.MILLI_OF_SECOND);
		return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millis);
	}
	
	private static int buildShader(String source, int glType) {
		int id = glCreateShader(glType);
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
			Main.logger.warn("Compilation error: ");
			for(String line : glGetShaderInfoLog(id).strip().split("\n"))
				Main.logger.warn("  " + line);
			glDeleteShader(id);
			return -1;
		}
		return id;
	}
	
	private static void deleteShaders(int... shaders) {
		for(int s : shaders) {
			if(s > 0)
				glDeleteShader(s);
		}
	}
	
	private static void deletePrograms(int... programs) {
		for(int s : programs) {
			if(s > 0)
				glDeleteProgram(s);
		}
	}
	
}

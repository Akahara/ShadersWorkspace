package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import fr.wonder.commons.loggers.AnsiLogger;
import fr.wonder.commons.loggers.Logger;

public class ScriptRenderer extends Renderer {
	
	public static final Logger scriptLogger = new AnsiLogger("Script");
	
	private int shaderStorageVertices, shaderStorageIndices;
	private int verticesDrawCount = 0, drawMode = GL_LINES;
	
	@Override
	public void loadResources() {
		ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(0, new float[0]);
		ByteBuffer shaderStorageIndicesData = BufferUtils.fromInts(0, new int[0]);
		
		shaderStorageVertices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageVertices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageVertices);
		glBindBuffer(GL_ARRAY_BUFFER, shaderStorageVertices);
		glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_STATIC_DRAW);
		
		shaderStorageIndices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageIndices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageIndices);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndices);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_STATIC_DRAW);
		
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, NULL);
	}
	
	public void rerunScriptFile(File scriptFile) {
		String scriptOutput = runScriptFile(scriptFile);
		if(scriptOutput != null)
			rebuildBuffersInput(scriptOutput);
	}
	
	private void rebuildBuffersInput(String scriptOutput) {
		String[] parts = scriptOutput.strip().split("\\s+");
		if(parts.length == 0) {
			scriptLogger.err("Invalid script output (use --script-log)");
			return;
		}
		
		int geometrySize; // number of points per geometry fragment
		
		switch(parts[0]) {
		case "lines":
			drawMode = GL_LINES;
			geometrySize = 2;
			break;
		case "points":
			drawMode = GL_POINTS;
			geometrySize = 1;
			break;
		default:
			scriptLogger.err("Invalid draw mode: " + parts[0]);
			return;
		}
		
		final int vertexSize = 4; // a single vertex is defined by 4 floats
		final int batchSize = vertexSize*geometrySize;
		
		int dataLength = parts.length-1;
		
		if(dataLength % batchSize != 0) {
			int validLength = dataLength/batchSize*batchSize;
			scriptLogger.warn("Invalid data length: " + dataLength + " using " + validLength);
			dataLength = validLength;
		}
		
		float[] vertices = new float[dataLength];
		for(int i = 0; i < vertices.length; i++) {
			try {
				vertices[i] = Float.parseFloat(parts[i+1]);
			} catch (NumberFormatException e) {
				scriptLogger.err("Invalid script output: found '" + parts[i+1] + "'");
			}
		}
		
		verticesDrawCount = dataLength / vertexSize;
		
		int[] indices = new int[verticesDrawCount];
		for(int i = 0; i < indices.length; i++)
			indices[i] = i;
		
		ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(vertices.length, vertices);
		ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(indices.length, indices);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageIndices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageVertices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);
	}
	
	private static String runScriptFile(File scriptFile) {
		if(!scriptFile.exists()) {
			Main.logger.err("The script file does not exist: " + scriptFile.getAbsolutePath());
			return null;
		}
		if(!scriptFile.isFile()) {
			Main.logger.err("Invalid script file: " + scriptFile.getAbsolutePath());
			return null;
		}
		try {
			String command = scriptFile.getAbsolutePath();
			long scriptTimestamp = System.currentTimeMillis();
			ProcessBuilder pb = new ProcessBuilder(command);
			ByteArrayOutputStream errbao = new ByteArrayOutputStream();
			ByteArrayOutputStream stdbao = new ByteArrayOutputStream();
			scriptLogger.debug("Running script: " + command);
			Process process = pb.start();
			redirectStreamInAnotherProcess(process.getInputStream(), stdbao);
			redirectStreamInAnotherProcess(process.getErrorStream(), errbao);
			int status = process.waitFor();
			scriptLogger.debug("Ran script in " + (System.currentTimeMillis() - scriptTimestamp) + "ms");
			String stderr = readProcessOutput(errbao);
			String stdout = readProcessOutput(stdbao);
			if(Main.options.scriptLogLength != 0) {
				String verboseOuput;
				if(stdout.length() > Main.options.scriptLogLength)
					verboseOuput = stdout.substring(0, Main.options.scriptLogLength) + "... (+" + (stdout.length()-Main.options.scriptLogLength) + " bytes)";
				else
					verboseOuput = stdout;
				scriptLogger.debug("Script output:\n" + verboseOuput);
			}
			if(!stderr.isBlank()) {
				scriptLogger.err("Script error:\n" + stderr);
				return null;
			}
			if(status != 0) {
				scriptLogger.err("The script exited with error: " + status);
				return null;
			}
			return stdout;
		} catch (IOException | InterruptedException e) {
			scriptLogger.err("Unexpected error while running script file: " + e.getMessage());
			return null;
		}
	}
	
	private static void redirectStreamInAnotherProcess(InputStream processStream, OutputStream redirection) {
		new Thread(() -> {
			try {
				processStream.transferTo(redirection);
			} catch (IOException e) {
				scriptLogger.err("An error occured in process stream redirection: " + e.getMessage());
			}
		}).start();
	}
	
	private static String readProcessOutput(ByteArrayOutputStream out) throws IOException {
		byte[] bytes = out.toByteArray();
		if(bytes.length == 0)
			return "";
		if(bytes[bytes.length-1] == '\n')
			return new String(bytes, 0, bytes.length-1);
		return new String(bytes);
	}
	
	@Override
	public void render() {
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glUseProgram(standardShaderProgram);
			standardShaderUniforms.reapply();
			glDrawElements(drawMode, verticesDrawCount, GL_UNSIGNED_INT, 0);
		}
		
		glfwSwapBuffers(GLWindow.getWindow());
		glfwPollEvents();
	}
	
}

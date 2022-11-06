package wonder.shaderdisplay.renderers;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;

import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;

public abstract class FormatedInputRenderer extends Renderer {
	
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
	
	/** Called before {@link #compileShaders(String[])}, will likely call {@link #rebuildBuffersInput(String)} */
	public abstract void reloadInputFile();
	
	@Override
	public boolean compileShaders(String[] shaders) {
		boolean compiled = super.compileShaders(shaders);
		if(compiled)
			checkGeometryShaderInputType(shaders[Resources.TYPE_GEOMETRY], drawMode);
		return compiled;
	}
	
	protected void rebuildBuffersInput(String scriptOutput) {
		String[] parts = scriptOutput.strip().split("(?:\\s|;)+");
		if(parts.length == 0) {
			Main.logger.err("Invalid script output (use --script-log)");
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
			Main.logger.err("Invalid draw mode: " + parts[0]);
			return;
		}
		
		final int vertexSize = 4; // a single vertex is defined by 4 floats
		final int batchSize = vertexSize*geometrySize;
		
		int dataLength = parts.length-1;
		
		if(dataLength % batchSize != 0) {
			int validLength = dataLength/batchSize*batchSize;
			Main.logger.warn("Invalid data length: " + dataLength + " using " + validLength);
			dataLength = validLength;
		}
		
		float[] vertices = new float[dataLength];
		for(int i = 0; i < vertices.length; i++) {
			try {
				vertices[i] = Float.parseFloat(parts[i+1]);
			} catch (NumberFormatException e) {
				Main.logger.err("Invalid script output: found '" + parts[i+1] + "'");
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

	@Override
	public void render() {
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glUseProgram(standardShaderProgram);
			standardShaderUniforms.apply();
			glDrawElements(drawMode, verticesDrawCount, GL_UNSIGNED_INT, 0);
		}
	}
	
	@Override
	public void renderControls() {
		if(standardShaderProgram > 0)
			standardShaderUniforms.renderControls("Uniforms");
	}

}

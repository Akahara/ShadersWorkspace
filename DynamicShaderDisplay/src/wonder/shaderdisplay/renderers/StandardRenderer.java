package wonder.shaderdisplay.renderers;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glFinish;
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
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;

import wonder.shaderdisplay.Resources;

public class StandardRenderer extends Renderer {
	
	private static final int SHADER_STORAGE_DATA_SIZE = 128;
	
	@Override
	public void loadResources() {
		float[] vertices = {
				-1, -1, 0, 1,
				 1, -1, 0, 1,
				 1,  1, 0, 1,
				-1,  1, 0, 1,
				};
		int[] indices = { 6, 0, 1, 2, 2, 3, 0 };
		
		ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(SHADER_STORAGE_DATA_SIZE, vertices);
		ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(1+SHADER_STORAGE_DATA_SIZE, indices );
		
		int shaderStorageVertices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageVertices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageVertices);

		int shaderStorageIndices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageIndices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, shaderStorageIndices);
		
		glBindBuffer(GL_ARRAY_BUFFER, shaderStorageVertices);
		glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_STATIC_DRAW);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndices);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_STATIC_DRAW);
		
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, NULL);
	}
	
	@Override
	public boolean compileShaders(String[] shaders) {
		boolean compiled = super.compileShaders(shaders);
		if(compiled)
			checkGeometryShaderInputType(shaders[Resources.TYPE_GEOMETRY], GL_TRIANGLES);
		return compiled;
	}
	
	@Override
	public void render() {
		if(computeShaderProgram > 0) {
			glUseProgram(computeShaderProgram);
			computeShaderUniforms.apply();
			glDispatchCompute(1, 1, 1);
		}
		
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glFinish();
			glUseProgram(standardShaderProgram);
			standardShaderUniforms.apply();
			int triangleCount = BufferUtils.readBufferInt(GL_SHADER_STORAGE_BUFFER, 0);
			glDrawElements(GL_TRIANGLES, 3*triangleCount, GL_UNSIGNED_INT, 4);
		}
	}
	
	@Override
	public void renderControls() {
		if(computeShaderProgram > 0)
			computeShaderUniforms.renderControls("Compute shader uniforms");
		if(standardShaderProgram > 0)
			standardShaderUniforms.renderControls("Standard shader uniforms");
	}

}

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
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;

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
	public void render() {
		if(computeShaderProgram > 0) {
			glUseProgram(computeShaderProgram);
			computeShaderUniforms.reapply();
			glDispatchCompute(1, 1, 1);
		}
		
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glFinish();
			glUseProgram(standardShaderProgram);
			standardShaderUniforms.reapply();
			int triangleCount = BufferUtils.readBufferInt(GL_SHADER_STORAGE_BUFFER, 0);
			glDrawElements(GL_TRIANGLES, 3*triangleCount, GL_UNSIGNED_INT, 4);
		}
		
		glfwSwapBuffers(GLWindow.getWindow());
		glfwPollEvents();
	}

}

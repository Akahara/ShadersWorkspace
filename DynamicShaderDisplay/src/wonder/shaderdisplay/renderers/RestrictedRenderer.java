package wonder.shaderdisplay.renderers;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;

import wonder.shaderdisplay.GLWindow;

public class RestrictedRenderer extends Renderer {

	@Override
	public void loadResources() {
		float[] vertices = {
				-1, -1, 0, 1,
				 1, -1, 0, 1,
				 1,  1, 0, 1,
				-1,  1, 0, 1,
				};
		int[] indices = { 0, 1, 2, 2, 3, 0 };
		
		ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(vertices.length, vertices);
		ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(indices.length, indices );
		
		int shaderStorageVertices = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, shaderStorageVertices);
		glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_STATIC_DRAW);
		
		int shaderStorageIndices = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndices);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_STATIC_DRAW);
		
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, NULL);
	}

	@Override
	public void render() {
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			standardShaderUniforms.reapply();
			glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
		}
		
		glfwSwapBuffers(GLWindow.getWindow());
		glfwPollEvents();
	}
	
}

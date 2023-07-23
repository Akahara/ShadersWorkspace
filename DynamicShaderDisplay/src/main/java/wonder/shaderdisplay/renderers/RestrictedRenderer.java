package wonder.shaderdisplay.renderers;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;

public class RestrictedRenderer extends Renderer {
	
	private int vao;

	@Override
	public void loadResources() {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);
		
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
		
		glBindVertexArray(0);
	}

	@Override
	public void render() {
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glBindVertexArray(vao);
			standardShaderUniforms.apply();
			glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
			glBindVertexArray(0);
		}
	}
	
	@Override
	public void renderControls() {
		if(standardShaderProgram > 0)
			standardShaderUniforms.renderControls("Uniforms");
	}
	
}

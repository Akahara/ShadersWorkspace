package wonder.shaderdisplay.display;

import wonder.shaderdisplay.Main.DisplayOptions.BackgroundType;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.renderers.Renderer;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class TexturesSwapChain {
	
	/*
	 * Texture slots, starting from 0:
	 * - RENDER_TARGET_COUNT-1 are for render targets
	 * - 1 is for the input texture if the process is run in image processing mode
	 * - remaining slots are used for texture uniforms
	 */
	
	public static final int RENDER_TARGET_COUNT = 5;
	private static final int SWAP_COUNT = 2;
	
	private final FrameBuffer[] fbos;
	private final Texture[][] textures;
	private int currentSwap = 0;
	
	public TexturesSwapChain(int width, int height) {
		this.textures = new Texture[SWAP_COUNT][RENDER_TARGET_COUNT];
		this.fbos = new FrameBuffer[SWAP_COUNT];
		
		for(int i = 0; i < SWAP_COUNT; i++)
			fbos[i] = new FrameBuffer();
		resizeTextures(width, height);
	}
	
	/** Should be immediately followed by bind() */
	public void swap() {
		currentSwap++;
		currentSwap %= SWAP_COUNT;
	}
	
	public void bind() {
		fbos[currentSwap].bind();
		for(int i = 0; i < RENDER_TARGET_COUNT; i++) {
			textures[(currentSwap+1)%SWAP_COUNT][i].bind(i);
		}
	}
	
	public void blitToScreen(boolean drawBackground) {
		WindowBlit.blitToScreen(textures[currentSwap][0], drawBackground);
	}

	public int getDisplayWidth() {
		return textures[0][0].getWidth();
	}
	
	public int getDisplayHeight() {
		return textures[0][0].getHeight();
	}
	
	public int[] readColorAttachment(int attachmentId, int[] outBuffer, BackgroundType background) {
		int[] pixels = fbos[currentSwap].readColorAttachment(attachmentId, outBuffer);
		switch(background) {
		case NORMAL:
			break;
		case NO_ALPHA:
			for(int i = 0; i < pixels.length; i++)
				pixels[i] |= 0xff << 24;
			break;
		case BLACK:
			for(int i = 0; i < pixels.length; i++) {
				float r = ((pixels[i] >> 0) & 0xff) / (float)0xff;
				float g = ((pixels[i] >> 8) & 0xff) / (float)0xff;
				float b = ((pixels[i] >> 16) & 0xff) / (float)0xff;
				float a = ((pixels[i] >> 24) & 0xff) / (float)0xff;
				pixels[i] =
						((int)(r*a*0xff) << 0) |
						((int)(g*a*0xff) << 8) |
						((int)(b*a*0xff) << 16) |
						(0xff << 24);
			}
			break;
		}
		return pixels;
	}

	public void resizeTextures(int w, int h) {
		for(int i = 0; i < SWAP_COUNT; i++) {
			fbos[i].clearAttachments();
			for(int j = 0; j < textures[i].length; j++) {
				if(textures[i][j] != null)
					textures[i][j].dispose();
				textures[i][j] = new Texture(w, h);
				fbos[i].addAttachment(textures[i][j]);
			}
		}
	}

	public void clearTextures() {
		glClear(GL_COLOR_BUFFER_BIT);
	}

	public Texture getOffscreenTexture(int renderTargetId) {
		return textures[(currentSwap+1)%SWAP_COUNT][renderTargetId];
	}
	
}

class WindowBlit {
	
	private static final int shader;
	private static final int vao;
	
	static {
		try {
			vao = glGenVertexArrays();
			glBindVertexArray(vao);
			int vbo = glGenBuffers();
			int ibo = glGenBuffers();
			int[] indices = { 0,1,2, 2,3,0 };
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
			glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
			glBindVertexArray(0);
			
			shader = glCreateProgram();
			int vertex = Renderer.buildShader("default-blit.vs", Resources.readResource("/blit.vs"), GL_VERTEX_SHADER);
			int fragment = Renderer.buildShader("default-blit.fs", Resources.readResource("/blit.fs"), GL_FRAGMENT_SHADER);
			glAttachShader(shader, vertex);
			glAttachShader(shader, fragment);
			glLinkProgram(shader);
			glValidateProgram(shader);
			if(glGetProgrami(shader, GL_LINK_STATUS) == GL_FALSE || glGetProgrami(shader, GL_VALIDATE_STATUS) == GL_FALSE)
				throw new RuntimeException("Failed to build the blit shader");
		} catch (IOException e) {
			throw new RuntimeException("Could not initialize the blit shader", e);
		}
	}
	
	public static void blitToScreen(Texture source, boolean drawBackground) {
		source.bind(0);
		glBindVertexArray(vao);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		glClear(GL_COLOR_BUFFER_BIT);
		glUseProgram(shader);
		glUniform1i(glGetUniformLocation(shader, "u_background"), drawBackground ? 1 : 0);
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
		glBindVertexArray(0);
	}
	
}

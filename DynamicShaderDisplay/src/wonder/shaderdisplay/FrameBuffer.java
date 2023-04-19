package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

/** Minimal implementation */
public class FrameBuffer {

	private final int id;
	private final Texture colorAttachment;
	
	public FrameBuffer(int width, int height) {
		this.id = glGenFramebuffers();
		this.colorAttachment = new Texture(width, height, null);
		bind();
		colorAttachment.bind(0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorAttachment.getId(), 0);
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("Incomplete frame buffer " + glCheckFramebufferStatus(GL_FRAMEBUFFER));
	}
	
	public void bind() {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		glViewport(0, 0, colorAttachment.getWidth(), colorAttachment.getHeight());
	}
	
	public void dispose() {
		glDeleteFramebuffers(id);
	}
	
	public void readColorAttachment(ByteBuffer buffer) {
		int w = colorAttachment.getWidth();
		int h = colorAttachment.getHeight();
		if(buffer.remaining() != w*h*3*Float.BYTES)
			throw new IllegalArgumentException("Invalid buffer size, got " + buffer.remaining() + " expected " + w*h*3*Float.BYTES);
		glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buffer);
	}
	
	public void readColorAttachment(int[] buffer) {
		int w = colorAttachment.getWidth();
		int h = colorAttachment.getHeight();
		if(buffer.length != w*h)
			throw new IllegalArgumentException("Invalid buffer size, got " + buffer.length + " expected " + w*h);
		glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
//		glFinish();
	}
	
}

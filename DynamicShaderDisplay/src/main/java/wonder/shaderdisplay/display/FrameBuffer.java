package wonder.shaderdisplay.display;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.IntStream;

public class FrameBuffer {

	private final int id;
	private final List<Texture> attachments = new ArrayList<>();
	private Texture depthAttachment;

	private static Stack<FrameBuffer> frameBufferStack = new Stack<>();
	
	public FrameBuffer() {
		this.id = glGenFramebuffers();
	}
	
	public void addAttachment(Texture texture) {
		if (isBound())
			throw new IllegalStateException("Cannot modify a bound FBO");
		if (!attachments.isEmpty() && (texture.getWidth() != attachments.get(0).getWidth() || texture.getHeight() != attachments.get(0).getHeight()))
			throw new IllegalArgumentException("Another texture with different size is already bound to an FBO");

		glBindFramebuffer(GL_FRAMEBUFFER, id);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, texture.getId(), 0);
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("Incomplete frame buffer " + glCheckFramebufferStatus(GL_FRAMEBUFFER));
		attachments.add(texture);
	}

	public void addDepthAttachment(Texture depthTexture) {
		if (isBound())
			throw new IllegalStateException("Cannot modify a bound FBO");

		depthAttachment = depthTexture;
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture.getId(), 0);
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("Incomplete frame buffer " + glCheckFramebufferStatus(GL_FRAMEBUFFER));
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}
	
	public void clearAttachments() {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		for(int i = 0; i < attachments.size(); i++)
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, 0, 0);
		attachments.clear();
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}
	
	public void bind() {
		if (isBound()) throw new IllegalStateException("FBO already bound");
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		int w = attachments.get(0).getWidth();
		int h = attachments.get(0).getHeight();
		int[] drawBuffers = IntStream.range(0, attachments.size()).map(i -> GL_COLOR_ATTACHMENT0+i).toArray();
		glViewport(0, 0, w, h);
		glDrawBuffers(drawBuffers);
		frameBufferStack.push(this);
	}

	public boolean isBound() {
		return frameBufferStack.contains(this);
	}

	public boolean isActive() {
		return frameBufferStack.peek() == this;
	}

	public void unbind() {
		if (!isActive()) throw new IllegalStateException("Cannot unbind a FBO that is not active");
		frameBufferStack.pop();
		glDrawBuffers(0);
	}
	
	public void dispose() {
		if (isBound()) throw new IllegalStateException("Cannot dispose of a bound FBO");
		glDeleteFramebuffers(id);
	}

}

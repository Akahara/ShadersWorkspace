package wonder.shaderdisplay.display;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glViewport;
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

	private static final Stack<FrameBuffer> frameBufferStack = new Stack<>();
	
	public FrameBuffer() {
		this.id = glGenFramebuffers();
	}
	
	public void addAttachment(Texture texture) {
		if (isBound())
			throw new IllegalStateException("Cannot modify a bound FBO");
		Texture anyTexture = attachments.isEmpty() ? depthAttachment : attachments.get(0);
		if (anyTexture != null && (texture.getWidth() != anyTexture.getWidth() || texture.getHeight() != anyTexture.getHeight()))
			throw new IllegalArgumentException("Another texture with different size is already bound to an FBO");

		glBindFramebuffer(GL_FRAMEBUFFER, id);
		if (texture.isDepth()) {
			if (depthAttachment != null)
				throw new IllegalStateException("Cannot add two depth attachments");
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, texture.getId(), 0);
		} else {
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, texture.getId(), 0);
			attachments.add(texture);
		}
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("Incomplete frame buffer " + glCheckFramebufferStatus(GL_FRAMEBUFFER));
	}
	
	public void clearAttachments() {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		for(int i = 0; i < attachments.size(); i++)
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, 0, 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
		attachments.clear();
		depthAttachment = null;
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}
	
	public void bind() {
		if (isBound()) throw new IllegalStateException("FBO already bound");
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		Texture anyTexture = attachments.isEmpty() ? depthAttachment : attachments.get(0);
		int w = anyTexture.getWidth();
		int h = anyTexture.getHeight();
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

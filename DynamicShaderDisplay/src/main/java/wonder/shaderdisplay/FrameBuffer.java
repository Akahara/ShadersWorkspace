package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class FrameBuffer {

	private final int id;
	private final List<Texture> attachments = new ArrayList<>();
	
	public FrameBuffer() {
		this.id = glGenFramebuffers();
	}
	
	public void addAttachment(Texture texture) {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, texture.getId(), 0);
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("Incomplete frame buffer " + glCheckFramebufferStatus(GL_FRAMEBUFFER));
		attachments.add(texture);
	}
	
	public void clearAttachments() {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		for(int i = 0; i < attachments.size(); i++)
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachments.size(), GL_TEXTURE_2D, 0, 0);
		attachments.clear();
	}
	
	public void bind() {
		glBindFramebuffer(GL_FRAMEBUFFER, id);
		int w = attachments.stream().mapToInt(Texture::getWidth ).max().getAsInt();
		int h = attachments.stream().mapToInt(Texture::getHeight).max().getAsInt();
		int[] drawBuffers = IntStream.range(0, attachments.size()).map(i -> GL_COLOR_ATTACHMENT0+i).toArray();
		glViewport(0, 0, w, h);
		glDrawBuffers(drawBuffers);
	}
	
	public void dispose() {
		glDeleteFramebuffers(id);
	}
	
	public int[] readColorAttachment(int attachmentId, int[] outBuffer) {
		int w = attachments.get(attachmentId).getWidth();
		int h = attachments.get(attachmentId).getHeight();
		if(outBuffer.length != w*h)
			throw new IllegalArgumentException("Invalid buffer size, got " + outBuffer.length + " expected " + w*h);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, id);
		glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_BYTE, outBuffer);
		return outBuffer;
	}

}

package wonder.shaderdisplay.display;

import fr.wonder.commons.utils.ArrayOperator;
import wonder.shaderdisplay.Main.DisplayOptions.BackgroundType;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.scene.SceneLayer;
import wonder.shaderdisplay.scene.SceneRenderTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.glCopyTextureSubImage2D;
import static org.lwjgl.opengl.GL45.glGetTextureImage;

public class TexturesSwapChain {

	private final FrameBuffer fbo;
	private final Map<String, SwapTexture> textures = new HashMap<>();

	public TexturesSwapChain(List<SceneRenderTarget> targets, int winWidth, int winHeight) {
		this.fbo = new FrameBuffer();
		for (SceneRenderTarget rt : targets)
			textures.put(rt.name, new SwapTexture(rt));
		resizeTextures(winWidth, winHeight);
	}
	
	public void blitToScreen(String renderTargetName, boolean drawBackground) {
		WindowBlit.blitToScreen(getColorAttachment(renderTargetName), drawBackground);
	}

	public Texture getColorAttachment(String renderTargetName) {
		return textures.get(renderTargetName).mainTexture;
	}

	public int[] readColorAttachment(String renderTargetName, BackgroundType background) {
		return readColorAttachment(renderTargetName, null, background);
	}

	public int[] readColorAttachment(String renderTargetName, int[] outBuffer, BackgroundType background) {
		Texture texture = getColorAttachment(renderTargetName);
		if (outBuffer == null) {
			outBuffer = new int[texture.getWidth() * texture.getHeight()];
		} else if (outBuffer.length != texture.getWidth() * texture.getHeight()) {
			throw new IllegalArgumentException("Invalid buffer size, got " + outBuffer.length + " expected " + texture.getWidth() * texture.getHeight());
		}
		glGetTextureImage(texture.getId(), 0, GL_BGRA, GL_UNSIGNED_BYTE, outBuffer);
		switch(background) {
		case NORMAL:
			break;
		case NO_ALPHA:
			for(int i = 0; i < outBuffer.length; i++)
				outBuffer[i] |= 0xff << 24;
			break;
		case BLACK:
			for(int i = 0; i < outBuffer.length; i++) {
				float r = ((outBuffer[i] >> 0) & 0xff) / (float)0xff;
				float g = ((outBuffer[i] >> 8) & 0xff) / (float)0xff;
				float b = ((outBuffer[i] >> 16) & 0xff) / (float)0xff;
				float a = ((outBuffer[i] >> 24) & 0xff) / (float)0xff;
				outBuffer[i] =
						((int)(r*a*0xff) << 0) |
						((int)(g*a*0xff) << 8) |
						((int)(b*a*0xff) << 16) |
						(0xff << 24);
			}
			break;
		}
		return outBuffer;
	}

	public void resizeTextures(int screenWidth, int screenHeight) {
		fbo.clearAttachments();
		for (SwapTexture swap : textures.values()) {
			if (swap.mainTexture != null) swap.mainTexture.dispose();
			if (swap.copyForWRPassTexture != null) swap.copyForWRPassTexture.dispose();

			int texW = (int)(swap.base.screenRelative ? screenWidth * swap.base.width : swap.base.width);
			int texH = (int)(swap.base.screenRelative ? screenHeight * swap.base.height : swap.base.height);
			swap.mainTexture = new Texture(texW, texH);
			swap.copyForWRPassTexture = new Texture(texW, texH);
		}
	}

	public void clearTextures() {
		for (SwapTexture swap : textures.values()) {
			fbo.clearAttachments();
			fbo.addAttachment(swap.mainTexture);
			fbo.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			fbo.unbind();
		}
	}

	public void dispose() {
		fbo.clearAttachments();
		for (SwapTexture swap : textures.values()) {
			swap.mainTexture.dispose();
			swap.copyForWRPassTexture.dispose();
		}
		fbo.dispose();
	}

	public void preparePass(SceneLayer layer) {
		/*
		 Copy each RT used as both WRITE (out render target) and READ (a sampler2D uniform) to another texture that will be used for sampler uniforms.
		 That way the texture can be read through the whole pass without being affected
		 */
		for (String rtName : layer.outRenderTargets) {
			if (!isTextureUsedAsReadWrite(layer, rtName))
				continue;
			SwapTexture swap = textures.get(rtName);
			fbo.clearAttachments();
			fbo.addAttachment(swap.mainTexture);
			fbo.bind();
			glCopyTextureSubImage2D(swap.copyForWRPassTexture.getId(), 0, 0, 0, 0, 0, swap.mainTexture.getWidth(), swap.mainTexture.getHeight());
			fbo.unbind();
		}
		fbo.clearAttachments();
		for (String rtName : layer.outRenderTargets) {
			SwapTexture swap = textures.get(rtName);
			fbo.addAttachment(swap.mainTexture);
		}
		fbo.bind();
	}

	public Texture getRenderTargetReadableTexture(SceneLayer accessingLayer, String renderTargetName) {
		SwapTexture swap = textures.get(renderTargetName);
		if (swap == null) return null;
		return isTextureUsedAsReadWrite(accessingLayer, renderTargetName) ? swap.copyForWRPassTexture : swap.mainTexture;
	}

	public void endPass() {
		fbo.unbind();
	}

	private static boolean isTextureUsedAsReadWrite(SceneLayer layer, String renderTargetName) {
		if (!ArrayOperator.contains(layer.outRenderTargets, renderTargetName))
			return false; // not used as write
		if (Stream.of(layer.uniforms).noneMatch(u -> u.value.equals(renderTargetName)))
			return false; // not used as read
		return true;
	}
}

class SwapTexture {

	Texture mainTexture;
	Texture copyForWRPassTexture;

	SceneRenderTarget base;

	SwapTexture(SceneRenderTarget base) {
		this.base = Objects.requireNonNull(base);
	}

}

class WindowBlit {
	
	private static final int shader;
	private static final int vao;
	
	static {
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
		int vertex = Renderer.buildRawShader(Resources.readResource("/blit.vs"), GL_VERTEX_SHADER);
		int fragment = Renderer.buildRawShader(Resources.readResource("/blit.fs"), GL_FRAGMENT_SHADER);
		glAttachShader(shader, vertex);
		glAttachShader(shader, fragment);
		glLinkProgram(shader);
		glValidateProgram(shader);
		if(glGetProgrami(shader, GL_LINK_STATUS) == GL_FALSE || glGetProgrami(shader, GL_VALIDATE_STATUS) == GL_FALSE)
			throw new RuntimeException("Failed to build the blit shader");
	}
	
	public static void blitToScreen(Texture source, boolean drawBackground) {
		source.bind(0);
		glBindVertexArray(vao);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glUseProgram(shader);
		glViewport(0, 0, GLWindow.getWinWidth(), GLWindow.getWinHeight());
		glUniform1i(glGetUniformLocation(shader, "u_background"), drawBackground ? 1 : 0);
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
		glBindVertexArray(0);
	}
	
}

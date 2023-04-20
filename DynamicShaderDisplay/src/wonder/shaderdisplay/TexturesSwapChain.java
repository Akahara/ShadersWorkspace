package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

public class TexturesSwapChain {
	
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
	
	public void blitToScreen() {
		fbos[currentSwap].blitToScreen();
	}

	public int getDisplayWidth() {
		return textures[0][0].getWidth();
	}
	
	public int getDisplayHeight() {
		return textures[0][0].getHeight();
	}
	
	public int[] readColorAttachment(int attachmentId, int[] outBuffer) {
		return fbos[currentSwap].readColorAttachment(attachmentId, outBuffer);
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
	
}

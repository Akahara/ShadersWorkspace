package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class Texture {
	
	private static final Map<String, Texture> cachedTextures = new HashMap<>();
	
	public final int id;
	
	public static Texture loadTexture(String path) {
		if(Main.options.noTextureCache) {
			Main.logger.debug("Loading texture: " + path);
			return new Texture(path);
		}
		if(cachedTextures.containsKey(path)) {
			Main.logger.debug("Loading texture: " + path + " (cached)");
			return cachedTextures.get(path);
		}
		Main.logger.debug("Loading texture: " + path);
		Texture tex = new Texture(path);
		cachedTextures.put(path, tex);
		return tex;
	}
	
	public static void unloadTextures() {
		for(Texture texture : cachedTextures.values())
			texture.dispose();
		cachedTextures.clear();
		Main.logger.debug("Unloaded textures");
	}

	private Texture(String path) {
		int id;
		
		try {
			BufferedImage image = ImageIO.read(Paths.get(path).toFile());
			
			int width = image.getWidth(), height = image.getHeight();
			
			int size = width*height;
			int[] data = new int[size];
			
			image.getRGB(0, 0, width, height, data, 0, width);
			
			int[] px = new int[size];
			for(int i = 0; i < height; i++) {
				for(int j = 0; j < width; j++) {
					int pos = i*width+j;
					int a = (data[pos] & 0xff000000) >> 24;
					int r = (data[pos] & 0x00ff0000) >> 16;
					int g = (data[pos] & 0x0000ff00) >> 8;
					int b = (data[pos] & 0x000000ff);
					px[(height-1-i)*width+j] =
							a << 24 |
							b << 16 |
							g << 8 |
							r;
				}
			}
			
			id = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, id);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
			glBindTexture(GL_TEXTURE_2D, 0);
			
		} catch(IOException | InvalidPathException e) {
			e.printStackTrace();
			id = 0;
		}
		
		this.id = id;
	}

	Texture(int width, int height, ByteBuffer buffer) {
		this.id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		glBindTexture(GL_TEXTURE_2D, 0);
	}
	
	public void dispose() {
		glDeleteTextures(id);
	}
	
}

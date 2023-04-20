package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

public class Texture {
	
	private static final Map<String, Texture> cachedTextures = new HashMap<>();
	private static Texture MISSING_TEXTURE;
	private static boolean useCache;
	
	private final int id;
	private final int width, height;
	
	@SuppressWarnings("unused")
	private static int aliveTextureCount = 0;
	
	public static Texture loadTexture(String path) {
		return loadOrUseCachedTexture("file_" + path, () -> loadFromFiles(path));
	}
	
	public static Texture loadTextureFromResources(int resourceId) {
		return loadOrUseCachedTexture("resource_" + resourceId, () -> loadFromResources(resourceId));
	}
	
	private static Texture loadOrUseCachedTexture(String name, Supplier<Texture> cacheMissSupplier) {
		if(MISSING_TEXTURE == null)
			MISSING_TEXTURE = loadFromResources(0);
		
		if(cachedTextures.containsKey(name)) {
			Main.logger.debug("Loading texture: " + name + " (cached)");
			return cachedTextures.get(name);
		}
		
		Main.logger.debug("Loading texture: " + name);
		Texture tex = cacheMissSupplier.get();
		
		if(useCache && tex != MISSING_TEXTURE)
			cachedTextures.put(name, tex);
		return tex;
	}
	
	private static Texture loadFromFiles(String file) {
		try {
			BufferedImage image = ImageIO.read(Paths.get(file).toFile());
			return new Texture(image);
		} catch(IOException | InvalidPathException e) {
			Main.logger.err(e, "Could not load texture '" + file + "'");
			return MISSING_TEXTURE;
		}
	}
	
	private static Texture loadFromResources(int resourceId) {
		try (InputStream is = Texture.class.getResourceAsStream("/images/res_" + resourceId + ".png")) {
			if(is == null)
				throw new IOException("No resource image with id " + resourceId);
			BufferedImage image = ImageIO.read(is);
			return new Texture(image);
		} catch (IOException e) {
			Main.logger.err(e, "Could not read resource texture " + resourceId);
			return MISSING_TEXTURE;
		}
	}
	
	public static void setUseCache(boolean useCache) {
		Texture.useCache = useCache;
	}
	
	public static boolean isUsingCache() {
		return useCache;
	}
	
	public static void unloadTextures() {
		for(Texture texture : cachedTextures.values())
			texture.dispose();
		cachedTextures.clear();
		Main.logger.debug("Unloaded textures");
	}

	public Texture(BufferedImage image) {
		this.width = image.getWidth();
		this.height = image.getHeight();
		
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
		
		this.id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
		glBindTexture(GL_TEXTURE_2D, 0);
		
		aliveTextureCount++;
	}

	public Texture(int width, int height, ByteBuffer rgba8Buffer) {
		this.id = glGenTextures();
		this.width = width;
		this.height = height;
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba8Buffer);
		glBindTexture(GL_TEXTURE_2D, 0);
		
		aliveTextureCount++;
	}
	
	public Texture(int width, int height) {
		this(width, height, null);
	}
	
	public void dispose() {
		glDeleteTextures(id);
		
		aliveTextureCount--;
	}
	
	public void bind(int slot) {
		glActiveTexture(GL_TEXTURE0 + slot);
		glBindTexture(GL_TEXTURE_2D, id);
	}

	public int getId() {
		return id;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
}

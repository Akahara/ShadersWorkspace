package wonder.shaderdisplay.display;

import wonder.shaderdisplay.Main;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
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
	private final boolean isDepth;
	
	@SuppressWarnings("unused")
	private static int aliveTextureCount = 0;
	
	public static Texture loadTexture(File file) {
		return loadOrUseCachedTexture("file_" + file.getAbsolutePath(), () -> loadFromFiles(file));
	}
	
	public static Texture loadTextureFromResources(int resourceId) {
		return loadOrUseCachedTexture("resource_" + resourceId, () -> loadFromResources(resourceId));
	}
	
	private static Texture loadOrUseCachedTexture(String name, Supplier<Texture> cacheMissSupplier) {
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
	
	private static Texture loadFromFiles(File file) {
		try {
			BufferedImage image = ImageIO.read(file);
			return new Texture(image);
		} catch(IOException | InvalidPathException e) {
			Main.logger.err(e, "Could not load texture '" + file + "'");
			return getMissingTexture();
		}
	}

	public static Texture getMissingTexture() {
		if(MISSING_TEXTURE == null)
			MISSING_TEXTURE = loadFromResources(0);
		return MISSING_TEXTURE;
	}
	
	private static Texture loadFromResources(int resourceId) {
		try (InputStream is = Texture.class.getResourceAsStream("/images/res_" + resourceId + ".png")) {
			if(is == null)
				throw new IOException("No resource image with id " + resourceId);
			BufferedImage image = ImageIO.read(is);
			return new Texture(image);
		} catch (IOException e) {
			Main.logger.err(e, "Could not read resource texture " + resourceId);
			if (resourceId == 0) throw new RuntimeException("Could not load the missing texture");
			return getMissingTexture();
		}
	}

	public static Texture createDepthTexture(int width, int height) {
		int texId = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texId);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
		glBindTexture(GL_TEXTURE_2D, 0);

		return new Texture(width, height, texId, true);
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

	public Texture(int width, int height, int[] data) {
		this.width = width;
		this.height = height;
		this.isDepth = false;
		this.id = glGenTextures();

		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, data);
		glBindTexture(GL_TEXTURE_2D, 0);

		aliveTextureCount++;
	}

	public Texture(BufferedImage image) {
		this(image.getWidth(), image.getHeight(), loadTextureData(image, false));
	}

	private Texture(int width, int height, int id, boolean isDepth) {
		this.id = id;
		this.width = width;
		this.height = height;
		this.isDepth = isDepth;
	}

	public enum InternalTextureFormat {

		RGBA8(GL_RGBA8),
		RGBA32F(GL_RGBA32F);

		final int glId;

		InternalTextureFormat(int glId) {
			this.glId = glId;
		}
	}
	
	public Texture(int width, int height, InternalTextureFormat format) {
		this.width = width;
		this.height = height;
		this.id = glGenTextures();
		this.isDepth = false;

		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, format.glId, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		glBindTexture(GL_TEXTURE_2D, 0);
	}
	
	public static int[] loadTextureData(BufferedImage image, boolean flipVertically) {
		int width = image.getWidth();
		int height = image.getHeight();
		
		int[] data = new int[width*height];
		
		if(flipVertically)
			image.getRGB(0, 0, width, height, data, 0, width);
		else
			image.getRGB(0, 0, width, height, data, width*(height-1), -width);
		
		int[] pixelsData = new int[width*height];
		for(int i = 0; i < height; i++) {
			for(int j = 0; j < width; j++) {
				int pos = i*width+j;
				int a = (data[pos] & 0xff000000) >> 24;
				int r = (data[pos] & 0x00ff0000) >> 16;
				int g = (data[pos] & 0x0000ff00) >> 8;
				int b = (data[pos] & 0x000000ff);
				pixelsData[i*width+j] =
						a << 24 |
						b << 16 |
						g << 8 |
						r;
			}
		}
		
		return pixelsData;
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

    public boolean isDepth() {
		return isDepth;
	}
}

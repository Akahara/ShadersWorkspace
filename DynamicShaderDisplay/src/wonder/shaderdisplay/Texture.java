package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL12.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

class Texture {
	
	final int id;

	Texture(String name) {
		int id;
		
		if(name == null) {
			this.id = 0;
			return;
		}
		
		try {
			BufferedImage image = ImageIO.read(Paths.get("res/textures/" + name).toFile());
			
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
			
		} catch(IOException e) {
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
	
}

package wonder.shaderdisplay.display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL30.*;

public class PixelBuffer {

    private final int id;
    private final int size;

    public PixelBuffer(int size) {
        this.id = glGenBuffers();
        this.size = size;

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, size, GL_STREAM_DRAW);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    public void copyToTexture(Texture texture) {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id);
        glBindTexture(GL_TEXTURE_2D, texture.getId());
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture.getWidth(), texture.getHeight(), GL_BGRA, GL_UNSIGNED_BYTE, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    public ByteBuffer map() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, size, GL_STREAM_DRAW);
        return glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
    }

    public void unmap() {
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

}

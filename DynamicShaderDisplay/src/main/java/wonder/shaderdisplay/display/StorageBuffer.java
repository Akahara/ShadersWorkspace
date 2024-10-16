package wonder.shaderdisplay.display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.glCreateBuffers;

public class StorageBuffer {

    private final int id;
    private final int size;

    public StorageBuffer(int sizeInBytes) {
        this.id = glCreateBuffers();
        this.size = sizeInBytes;
        setToZero();
    }

    public void bind(int bindingPoint, int offset, int size) {
        if (offset < 0) offset = 0;
        if (size < 0) size = this.size - offset;
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, bindingPoint, id, offset, size);
    }

    public void dispose() {
        glDeleteBuffers(id);
    }

    public void setToZero() {
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
        glBufferData(GL_SHADER_STORAGE_BUFFER, buf, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getSizeInBytes() {
        return size;
    }
}

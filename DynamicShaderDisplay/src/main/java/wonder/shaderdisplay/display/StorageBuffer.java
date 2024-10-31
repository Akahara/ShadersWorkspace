package wonder.shaderdisplay.display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL31.*;
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

    public void bind(int bindingPoint) {
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, bindingPoint, id, 0, size);
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

    public void setData(int offset, ByteBuffer buf) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, buf);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void copyFrom(StorageBuffer source) {
        if (size != source.size)
            throw new IllegalArgumentException("Buffers of different size");
        glBindBuffer(GL_COPY_READ_BUFFER, source.id);
        glBindBuffer(GL_COPY_WRITE_BUFFER, id);
        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, 0, 0, size);
        glBindBuffer(GL_COPY_READ_BUFFER, 0);
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
    }

    public int getSizeInBytes() {
        return size;
    }

    public void read(ByteBuffer dst, int offset, int size) {
        dst.position(0);
        dst.limit(size);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, dst);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }
}

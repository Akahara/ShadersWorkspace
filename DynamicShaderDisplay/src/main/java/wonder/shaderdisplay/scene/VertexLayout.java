package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

public class VertexLayout {

    public static final VertexLayout DEFAULT_LAYOUT = new VertexLayout();
    public static final VertexLayout EMPTY_LAYOUT = new VertexLayout();

    static {
        EMPTY_LAYOUT.name = "empty";
        DEFAULT_LAYOUT.name = "default";
        DEFAULT_LAYOUT.items = new VertexLayoutItem[] {
            new VertexLayoutItem(GLDataType.FLOAT, 4, -1, -1, false) /*position*/,
            new VertexLayoutItem(GLDataType.FLOAT, 3, -1, -1, false) /*normal*/,
            new VertexLayoutItem(GLDataType.FLOAT, 2, -1, -1, false) /*uv*/,
        };
    }

    @JsonProperty(required = true)
    public String name;
    @JsonProperty(required = true)
    public VertexLayoutItem[] items = new VertexLayoutItem[0];

    private int totalStride = -1;

    private int getTotalStride() {
        if (totalStride < 0) {
            totalStride = 0;
            for (VertexLayoutItem it : items) {
                totalStride += it.type.byteCount * it.count;
            }
        }
        return totalStride;
    }

    public void bind() {
        int totalStride = getTotalStride();
        int currentOffset = 0;
        for (int i = 0; i < items.length; i++) {
            VertexLayoutItem item = items[i];
            glEnableVertexAttribArray(i);
            int stride = item.stride < 0 ? totalStride : item.stride;
            int pointer = item.offset < 0 ? currentOffset : item.offset;
            glVertexAttribPointer(i, item.count, item.type.glType, item.normalized, stride, pointer);
            currentOffset += item.count * item.type.byteCount;
        }
    }

    public static class VertexLayoutItem {

        @JsonProperty(required = true)
        public GLDataType type;
        @JsonProperty(required = true, value = "count")
        public int count;
        public int stride = -1;
        public int offset = -1;
        public boolean normalized = false;

        VertexLayoutItem() {}
        VertexLayoutItem(GLDataType type, int count, int stride, int offset, boolean normalized) {
            this.type = type;
            this.count = count;
            this.stride = stride;
            this.offset = offset;
            this.normalized = normalized;
        }
        
    }

    public enum GLDataType {

        FLOAT(4, GL_FLOAT);

        public final int byteCount;
        public final int glType;

        GLDataType(int byteCount, int glType) {
            this.byteCount = byteCount;
            this.glType = glType;
        }

        @JsonValue
        public String serialName() { return name().toLowerCase(); }

    }

}

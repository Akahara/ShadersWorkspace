package wonder.shaderdisplay.display;

import static org.lwjgl.opengl.GL15.glGetBufferSubData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferUtils {
	
	private static final ByteOrder GPU_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	
	public static ByteBuffer fromInts(int intCapacity, int[] initialData) {
		ByteBuffer buf = ByteBuffer.allocateDirect(4*intCapacity);
		buf.order(GPU_BYTE_ORDER);
		buf.asIntBuffer().put(initialData, 0, Math.min(initialData.length, intCapacity));
		buf.position(0);
		return buf;
	}

	public static ByteBuffer fromInts(int[] data) {
		return fromInts(data.length, data);
	}

	public static ByteBuffer fromFloats(int floatCapacity, float[] initialData) {
		ByteBuffer buf = ByteBuffer.allocateDirect(4*floatCapacity);
		buf.order(GPU_BYTE_ORDER);
		buf.asFloatBuffer().put(initialData, 0, Math.min(initialData.length, floatCapacity));
		buf.position(0);
		return buf;
	}

	public static ByteBuffer fromFloats(float[] data) {
		return fromFloats(data.length, data);
	}

	public static int readBufferInt(int target, int byteOffset) {
		int[] data = new int[1];
		glGetBufferSubData(target, byteOffset, data);
		return data[0];
	}

    public static ByteBuffer fromString(String string) {
//		ByteBuffer buf = ByteBuffer.allocateDirect(absolutePath.length());
//		buf.asCharBuffer().put(absolutePath);
//		buf.position(0);
//		return buf;
		return ByteBuffer.wrap(string.getBytes());
    }
}

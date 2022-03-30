package wonder.shaderdisplay.renderers;

import static org.lwjgl.opengl.GL15.glGetBufferSubData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferUtils {
	
	private static final ByteOrder GPU_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	
	public static ByteBuffer fromInts(int intCapacity, int[] data) {
		ByteBuffer buf = ByteBuffer.allocateDirect(4*intCapacity);
		buf.order(GPU_BYTE_ORDER);
		buf.asIntBuffer().put(data, 0, Math.min(data.length, intCapacity));
		buf.position(0);
		return buf;
	}

	public static ByteBuffer fromFloats(int floatCapacity, float[] data) {
		ByteBuffer buf = ByteBuffer.allocateDirect(4*floatCapacity);
		buf.order(GPU_BYTE_ORDER);
		buf.asFloatBuffer().put(data, 0, Math.min(data.length, floatCapacity));
		buf.position(0);
		return buf;
	}
	
	public static int readBufferInt(int target, int byteOffset) {
		int[] data = new int[1];
		glGetBufferSubData(target, 0, data);
		return data[0];
	}
	
	public static int[] readBufferInts(int target, int count) {
		int[] data = new int[count];
		glGetBufferSubData(target, 0, data);
		return data;
	}
	
}

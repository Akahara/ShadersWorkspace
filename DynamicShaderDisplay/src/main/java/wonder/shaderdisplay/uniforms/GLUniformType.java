package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_INT;
import static org.lwjgl.opengl.GL20.*;

import java.util.Map;

import org.lwjgl.opengl.GL40;

public enum GLUniformType {
	
	FLOAT(GL_FLOAT,      "float", 1),
	VEC2 (GL_FLOAT_VEC2, "vec2",  2),
	VEC3 (GL_FLOAT_VEC3, "vec3",  3),
	VEC4 (GL_FLOAT_VEC4, "vec4",  4),
	MAT2 (GL_FLOAT_MAT2, "mat2",  4),
	MAT3 (GL_FLOAT_MAT3, "mat3",  9),
	MAT4 (GL_FLOAT_MAT4, "mat4", 16),
	INT  (GL_INT,        "int",   1),
	IVEC2(GL_INT_VEC2,   "ivec2", 2),
	IVEC3(GL_INT_VEC3,   "ivec3", 3),
	IVEC4(GL_INT_VEC4,   "ivec4", 4),
	BOOL (GL_BOOL,       "bool",  1),
	SAMPLER2D(GL_SAMPLER_2D, "sampler2D", -1);
	
	public final int glType;
	public final String name;
	public final int typeSize;
	public GlUniformFloatFunction floatSetterFunction;
	public GlUniformIntFunction intSetterFunction;
	
	static {
		FLOAT.floatSetterFunction = GL40::glUniform1fv;
		VEC2.floatSetterFunction = GL40::glUniform2fv;
		VEC3.floatSetterFunction = GL40::glUniform3fv;
		VEC4.floatSetterFunction = GL40::glUniform4fv;
		MAT2.floatSetterFunction = (loc, value) -> GL40.glUniformMatrix2fv(loc, false, value);
		MAT3.floatSetterFunction = (loc, value) -> GL40.glUniformMatrix3fv(loc, false, value);
		MAT4.floatSetterFunction = (loc, value) -> GL40.glUniformMatrix4fv(loc, false, value);
		INT.intSetterFunction = GL40::glUniform1iv;
		IVEC2.intSetterFunction = GL40::glUniform2iv;
		IVEC3.intSetterFunction = GL40::glUniform3iv;
		IVEC4.intSetterFunction = GL40::glUniform4iv;
	}
	
	private static final GLUniformType[] ALL_TYPES = values();
	
	GLUniformType(int glType, String typeName, int typeSize) {
		this.glType = glType;
		this.name = typeName;
		this.typeSize = typeSize;
	}
	
	public static GLUniformType getFromGLTypeId(int glType) {
		for(GLUniformType type : ALL_TYPES) {
			if(type.glType == glType)
				return type;
		}
		return null;
	}
	
	public boolean isStandardFloatType() {
		return floatSetterFunction != null;
	}
	
	public boolean isStandardIntType() {
		return intSetterFunction != null;
	}

	public static final Map<GLUniformType, FloatUniformControl> COLOR_CONTROLS = Map.of(
			VEC3, new ControlColorN(3),
			VEC4, new ControlColorN(4));

	public static final Map<GLUniformType, FloatUniformControl> STANDARD_FLOAT_CONTROLS = Map.of(
			FLOAT, new ControlFloatVecN(1),
			VEC2,  new ControlFloatVecN(2),
			VEC3,  new ControlFloatVecN(3),
			VEC4,  new ControlFloatVecN(4),
			MAT2,  new ControlMatN(2),
			MAT3,  new ControlMatN(3),
			MAT4,  new ControlMatN(4)
	);
	
	public static final Map<GLUniformType, IntUniformControl> STANDARD_INT_CONTROLS = Map.of(
			INT,   new ControlIntVecN(1),
			IVEC2, new ControlIntVecN(2),
			IVEC3, new ControlIntVecN(3),
			IVEC4, new ControlIntVecN(4)
	);
	
	public interface FloatUniformControl {
		void renderControl(String name, float[] value);
	}
	
	public interface IntUniformControl {
		void renderControl(String name, int[] value);
	}
	
	public interface GlUniformFloatFunction {
		void setUniform(int location, float[] value);
	}
	
	public interface GlUniformIntFunction {
		void setUniform(int location, int[] value);
	}
	
}

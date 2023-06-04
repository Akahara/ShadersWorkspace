package wonder.shaderdisplay.uniforms;

import java.util.Map;

import org.lwjgl.opengl.GL40;

public enum GLUniformType {
	
	FLOAT("float", 1, GL40::glUniform1fv                                        ),
	VEC2 ("vec2",  2, GL40::glUniform2fv                                        ),
	VEC3 ("vec3",  3, GL40::glUniform3fv                                        ),
	VEC4 ("vec4",  4, GL40::glUniform4fv                                        ),
	MAT2 ("mat2",  4, (loc, value) -> GL40.glUniformMatrix2fv(loc, false, value)),
	MAT3 ("mat3",  9, (loc, value) -> GL40.glUniformMatrix3fv(loc, false, value)),
	MAT4 ("mat4", 16, (loc, value) -> GL40.glUniformMatrix4fv(loc, false, value)),
	INT  ("int",  -1, null),
	SAMPLER2D("sampler2D", -1, null);
	
	public final String name;
	public final int typeSize;
	public final GlUniformFloatFunction setterFunction;
	
	private GLUniformType(String typeName, int typeSize, GlUniformFloatFunction setterFunction) {
		this.name = typeName;
		this.typeSize = typeSize;
		this.setterFunction = setterFunction;
	}
	
	public boolean isStandardFloatType() {
		return setterFunction != null;
	}

	public static final Map<GLUniformType, UniformControl> COLOR_CONTROLS = Map.of(
			VEC3, new ControlColorN(3),
			VEC4, new ControlColorN(4));
	
	public static final Map<GLUniformType, UniformControl> STANDARD_CONTROLS = Map.of(
			FLOAT, new ControlVecN(1),
			VEC2,  new ControlVecN(2),
			VEC3,  new ControlVecN(3),
			VEC4,  new ControlVecN(4),
			MAT2,  new ControlMatN(2),
			MAT3,  new ControlMatN(3),
			MAT4,  new ControlMatN(4)
	);
	
}

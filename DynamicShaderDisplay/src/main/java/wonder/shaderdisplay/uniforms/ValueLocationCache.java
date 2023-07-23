package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

class ValueLocationCache implements UniformLocationCache {
	
	private final int location;
	
	public ValueLocationCache(int program, String name) {
		this.location = getLocation(program, name);
	}
	
	@Override
	public int getLocation(int arrayIndex) {
		return location;
	}
	
	public static int getLocation(int program, String uniformName) {
		return glGetUniformLocation(program, uniformName);
	}
}
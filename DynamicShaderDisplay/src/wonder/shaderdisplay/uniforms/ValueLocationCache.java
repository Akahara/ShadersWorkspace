package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

class ValueLocationCache implements UniformLocationCache {
	
	private final int location;
	
	public ValueLocationCache(int program, String name) {
		this.location = glGetUniformLocation(program, name);
	}
	
	@Override
	public int getLocation(int arrayIndex) {
		return location;
	}
}
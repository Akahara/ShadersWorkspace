package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

import java.util.ArrayList;
import java.util.List;

class ArrayLocationCache implements UniformLocationCache {
	
	private final int program;
	private final String name;
	
	private final List<Integer> locationCache = new ArrayList<>();
	
	public ArrayLocationCache(int program, String name) {
		this.program = program;
		this.name = name;
	}
	
	@Override
	public int getLocation(int arrayIndex) {
		while(locationCache.size() <= arrayIndex)
			locationCache.add(glGetUniformLocation(program, name + '[' + locationCache.size() + ']'));
		return locationCache.get(arrayIndex);
	}
}
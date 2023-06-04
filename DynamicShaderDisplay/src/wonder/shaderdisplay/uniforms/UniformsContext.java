package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_INT;
import static org.lwjgl.opengl.GL20.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Texture;
import wonder.shaderdisplay.TexturesSwapChain;
import wonder.shaderdisplay.Time;

public class UniformsContext {
	
	private static final List<RawBuiltinUniform> BUILTIN_UNIFORMS = List.of(
			new RawBuiltinUniform(GLUniformType.FLOAT, "iTime",        TimeUniform::new      ),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_time",       TimeUniform::new      ),
			new RawBuiltinUniform(GLUniformType.INT,   "iFrame",       FrameUniform::new     ),
			new RawBuiltinUniform(GLUniformType.INT,   "u_frame",      FrameUniform::new     ),
			new RawBuiltinUniform(GLUniformType.VEC2,  "iResolution",  ResolutionUniform::new),
			new RawBuiltinUniform(GLUniformType.VEC2,  "u_resolution", ResolutionUniform::new)
	);
	
	private List<Uniform> uniforms = new ArrayList<>();
	// uniforms that had values at some point, values are kept in memory
	// to be able to be retrieved later (opengl gets rid of uniforms that
	// are not used in code)
	private Map<String, Uniform> oldUniforms = new HashMap<>();
	
	public UniformsContext() {}
	
	
	public void rescan(int program, String code) {
		uniforms = new ArrayList<>();
		
		List<RawUniform> newUniforms = scanForUniforms(program, code);
		
		// the first N binding points are reserved for rendertargets, the remaining ones are standard textures
		IntRef nextTextureSlot = new IntRef(TexturesSwapChain.RENDER_TARGET_COUNT);
		// the next slot is used for the input texture
		if(Main.isImagePass) nextTextureSlot.value++;
		
		for(RawUniform u : newUniforms) {
			// find built-in uniforms (iTime/iFrame...)
			Uniform uniformAsBuiltin = tryGetBuiltinUniform(program, u);
			if(uniformAsBuiltin != null) {
				uniforms.add(uniformAsBuiltin);
				continue;
			}
			
			// handle textures
			if(u.type == GLUniformType.SAMPLER2D) {
				Uniform uniformAsTexture = tryGetTextureUniform(program, code, u, nextTextureSlot);
				if(uniformAsTexture != null)
					uniforms.add(uniformAsTexture);
				continue;
			}
			
			// handle normal uniforms
			if(u.type.isStandardFloatType()) {
				UniformControl control = null;
				if(u.name.startsWith("c_"))
					control = GLUniformType.COLOR_CONTROLS.get(u.type);
				if(control == null)
					control = GLUniformType.STANDARD_CONTROLS.get(u.type);
				float[][] initialValues = getUniformValues(program, u);
				
				ArbitraryUniform uniform = new ArbitraryUniform(
						program, u.name, u.type,
						control, initialValues);
				uniform.copy(oldUniforms.get(u.name));
				uniforms.add(uniform);
				continue;
			}
			
			Main.logger.warn("Could not create control for uniform '" + u.name + "'");
		}
		
		for(Uniform u : uniforms)
			oldUniforms.put(u.name, u);
		
		if(!uniforms.isEmpty()) {
			Iterable<String> boundNames = () -> uniforms.stream().map(u -> u.name).iterator();
			Main.logger.debug("Bound uniforms: " + String.join(" ", boundNames));
		}
	}
	
	private static Uniform tryGetBuiltinUniform(int program, RawUniform u) {
		for(RawBuiltinUniform builtin : BUILTIN_UNIFORMS) {
			if(u.type == builtin.type && u.name.equals(builtin.name))
				return builtin.generator.create(builtin.name, program);
		}
		return null;
	}
	
	private static Uniform tryGetTextureUniform(int program, String code, RawUniform u, IntRef nextTextureSlot) {
		Pattern texturePattern = Pattern.compile("\nuniform sampler2D " + Pattern.quote(u.name) + ";\\s+//[ \t]*(.+)");
		Matcher matcher = texturePattern.matcher(code);
		
		if(!matcher.find()) {
			Main.logger.warn("Unset texture uniform '" + u.name + "'");
			return null;
		}
		
		String path = matcher.group(1);
		
		if(path.startsWith("input or ")) { // if in image processing mode, the input texture
			if(Main.isImagePass)
				return new InputTextureUniform(program, u.name);
			path = path.substring("input or ".length());
		}
		
		if(path.matches("target \\d+")) { // rendering target texture
			int target = Integer.parseInt(path.substring("target ".length()));
			if(target < 0 || target >= TexturesSwapChain.RENDER_TARGET_COUNT) {
				Main.logger.err("Invalid render target '" + target + "' for uniform '" + u.name + "', available are 0.." + (TexturesSwapChain.RENDER_TARGET_COUNT-1));
				target = 0;
			}
			return new TargetTextureUniform(program, target, u.name, target);
		} else if(path.matches("builtin \\d+")) { // default texture, loaded from resources
			int buitinId = Integer.parseInt(path.substring("builtin ".length()));
			Texture texture = Texture.loadTextureFromResources(buitinId);
			return new TextureUniform(program, nextTextureSlot.value++, u.name, texture, path);
		} else { // normal texture, loaded from user files
			Texture texture = Texture.loadTexture(path);
			return new TextureUniform(program, nextTextureSlot.value++, u.name, texture, path);
		}
	}
	
	private static List<RawUniform> scanForUniforms(int program, String code) {
		int uniformCount = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
		List<RawUniform> uniforms = new ArrayList<>();
		Map<RawUniform, Integer> firstOccurences = new HashMap<>();
		int[] _nameLength = new int[1],
				_size = new int[1],
				_type = new int[1];
		ByteBuffer _name = ByteBuffer.allocateDirect(128);
		byte[] nameBytes = new byte[_name.capacity()];
		for(int i = 0; i < uniformCount; i++) {
			glGetActiveUniform(program, i, _nameLength, _size, _type, _name);
			int nameLength = _nameLength[0];
			int size = _size[0];
			int type = _type[0];
			_name.get(nameBytes, 0, nameLength);
			_name.position(0);
			String name = new String(nameBytes, 0, nameLength);
			if(name.endsWith("[0]")) name = name.substring(0, name.length()-3);
			RawUniform u = new RawUniform();
			u.arrayLength = size;
			u.name = name;
			switch(type) {
			case GL_FLOAT:      u.type = GLUniformType.FLOAT; break;
			case GL_FLOAT_VEC2: u.type = GLUniformType.VEC2;  break;
			case GL_FLOAT_VEC3: u.type = GLUniformType.VEC3;  break;
			case GL_FLOAT_VEC4: u.type = GLUniformType.VEC4;  break;
			case GL_FLOAT_MAT2: u.type = GLUniformType.MAT2;  break;
			case GL_FLOAT_MAT3: u.type = GLUniformType.MAT3;  break;
			case GL_FLOAT_MAT4: u.type = GLUniformType.MAT4;  break;
			case GL_INT:        u.type = GLUniformType.INT;   break;
			case GL_SAMPLER_2D: u.type = GLUniformType.SAMPLER2D; break;
			default: Main.logger.warn("Unsupported uniform type for '" + name + "'"); continue;
			}
			uniforms.add(u);
			Pattern namePattern = Pattern.compile(Pattern.quote(name) + "\\W");
			Matcher nameMatcher = namePattern.matcher(code);
			firstOccurences.put(u, nameMatcher.find() ? nameMatcher.start() : code.length());
		}
		uniforms.sort((u1, u2) -> firstOccurences.get(u1)-firstOccurences.get(u2));
		return uniforms;
	}
	
	private float[][] getUniformValues(int program, RawUniform uniform) {
		float[][] values = new float[uniform.arrayLength][uniform.type.typeSize];
		for(int i = 0; i < uniform.arrayLength; i++)
			glGetUniformfv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), values[i]);
		return values;
	}
	
	public void apply() {
		for(Uniform u : uniforms)
			u.apply();
	}
	
	public void renderControls(String name) {
		ImGui.setNextWindowCollapsed(true, ImGuiCond.Once);
		if(ImGui.begin(name)) {
			if(ImGui.button("copy all uniforms")) {
				StringBuilder clipboardText = new StringBuilder();
				for(Uniform u : uniforms) {
					clipboardText.append(u.toUniformString());
					clipboardText.append('\n');
				}
				ImGui.setClipboardText(clipboardText.toString());
			}
			Time.renderControls();
			for(Uniform u : uniforms)
				u.renderControl();
		}
		ImGui.end();
	}
	
}

class IntRef {
	
	public int value;
	
	IntRef(int value) {
		this.value = value;
	}
}

class RawBuiltinUniform {
	
	public final GLUniformType type;
	public final String name;
	public final BuiltinUniformGenerator generator;
	
	public RawBuiltinUniform(GLUniformType type, String name, BuiltinUniformGenerator generator) {
		this.type = type;
		this.name = name;
		this.generator = generator;
	}
	
}

@FunctionalInterface
interface BuiltinUniformGenerator {
	
	Uniform create(String name, int program);
	
}

class RawUniform {
	
	GLUniformType type;
	String name;
	int arrayLength; // -1 for non-arrays and undefined-length arrays
	
	@Override
	public String toString() {
		return String.format("%s%s %s = %s",
				type.name, (arrayLength > 1) ? "["+arrayLength+"]":"", name);
	}
}
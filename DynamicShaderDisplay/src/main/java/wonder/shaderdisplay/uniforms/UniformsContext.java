package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.GL_ACTIVE_UNIFORMS;
import static org.lwjgl.opengl.GL20.glGetActiveUniform;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glGetUniformfv;
import static org.lwjgl.opengl.GL20.glGetUniformiv;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Texture;
import wonder.shaderdisplay.TexturesSwapChain;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.uniforms.GLUniformType.FloatUniformControl;
import wonder.shaderdisplay.uniforms.GLUniformType.IntUniformControl;

public class UniformsContext {
	
	private static final List<RawBuiltinUniform> BUILTIN_UNIFORMS = List.of(
			new RawBuiltinUniform(GLUniformType.FLOAT, "iTime",        TimeUniform::new),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_time",       TimeUniform::new),
			new RawBuiltinUniform(GLUniformType.INT,   "iFrame",       (p,n) -> new FrameUniform(p,n,false)),
			new RawBuiltinUniform(GLUniformType.INT,   "u_frame",      (p,n) -> new FrameUniform(p,n,false)),
			new RawBuiltinUniform(GLUniformType.FLOAT, "iFrame",       (p,n) -> new FrameUniform(p,n,true)),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_frame",      (p,n) -> new FrameUniform(p,n,true)),
			new RawBuiltinUniform(GLUniformType.IVEC2, "iResolution",  (p,n) -> new ResolutionUniform(p,n,false)),
			new RawBuiltinUniform(GLUniformType.IVEC2, "u_resolution", (p,n) -> new ResolutionUniform(p,n,false)),
			new RawBuiltinUniform(GLUniformType.VEC2,  "iResolution",  (p,n) -> new ResolutionUniform(p,n,true)),
			new RawBuiltinUniform(GLUniformType.VEC2,  "u_resolution", (p,n) -> new ResolutionUniform(p,n,true))
	);
	
	private List<Uniform> uniforms = new ArrayList<>();
	// uniforms that had values at some point, values are kept in memory
	// to be able to be retrieved later (opengl gets rid of uniforms that
	// are not used in code)
	private final Map<String, ArbitraryUniform> oldArbitraryUniforms = new HashMap<>();
	private final Map<String, Number[][]> originalUniformValues = new HashMap<>();
	
	public UniformsContext() {}
	
	
	public void rescan(int program, String code) {
		uniforms = new ArrayList<>();
		originalUniformValues.clear();
		
		List<RawUniform> newUniforms = scanForUniforms(program, code);
		
		// the first N binding points are reserved for rendertargets, the remaining ones are standard textures
		TexturesContext nextTextureSlot = new TexturesContext(TexturesSwapChain.RENDER_TARGET_COUNT);
		// the next slot is used for the input texture
		if(Main.isImagePass) nextTextureSlot.nextTextureSlot++;
		
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
				oldArbitraryUniforms.remove(u.name);
				continue;
			}
			
			// handle normal float uniforms
			if(u.type.isStandardFloatType()) {
				FloatUniformControl control = null;
				if(u.name.startsWith("c_"))
					control = GLUniformType.COLOR_CONTROLS.get(u.type);
				if(control == null)
					control = GLUniformType.STANDARD_FLOAT_CONTROLS.get(u.type);
				float[][] initialValues = getUniformFloatValues(program, u);
				
				ArbitraryFloatUniform uniform = new ArbitraryFloatUniform(
						program, u.name, u.type,
						control, initialValues);
				uniforms.add(uniform);
				originalUniformValues.put(u.name, uniform.getValues());
				tryRestorePreviousUniformValues(u.name, uniform);
				oldArbitraryUniforms.put(u.name, uniform);
				continue;
			}
			
			// handle normal int uniforms
			if(u.type.isStandardIntType()) {
				IntUniformControl control = GLUniformType.STANDARD_INT_CONTROLS.get(u.type);
				int[][] initialValues = getUniformIntValues(program, u);
				
				ArbitraryIntUniform uniform = new ArbitraryIntUniform(
						program, u.name, u.type,
						control, initialValues);
				uniforms.add(uniform);
				originalUniformValues.put(u.name, uniform.getValues());
				tryRestorePreviousUniformValues(u.name, uniform);
				oldArbitraryUniforms.put(u.name, uniform);
				continue;
			}
			
			// handle normal bool uniforms
			if(u.type == GLUniformType.BOOL) {
				boolean[] initialValues = getUniformBoolValues(program, u);
				ArbitraryBoolUniform uniform = new ArbitraryBoolUniform(program, u.name, initialValues);
				uniforms.add(uniform);
				originalUniformValues.put(u.name, uniform.getValues());
				tryRestorePreviousUniformValues(u.name, uniform);
				oldArbitraryUniforms.put(u.name, uniform);
				continue;
			}
			
			Main.logger.warn("Could not create control for uniform '" + u.name + "'");
		}
		
		if(!uniforms.isEmpty()) {
			Iterable<String> boundNames = () -> uniforms.stream().map(u -> u.name).iterator();
			Main.logger.debug("Bound uniforms: " + String.join(" ", boundNames));
		}
	}
	
	private void tryRestorePreviousUniformValues(String name, ArbitraryUniform currentUniform) {
		ArbitraryUniform oldUniform = oldArbitraryUniforms.get(name);
		if(oldUniform != null)
			currentUniform.copy(oldUniform.getValues());
	}
	
	private static Uniform tryGetBuiltinUniform(int program, RawUniform u) {
		for(RawBuiltinUniform builtin : BUILTIN_UNIFORMS) {
			if(u.type == builtin.type() && u.name.equals(builtin.name()))
				return builtin.generator().create(builtin.name(), program);
		}
		return null;
	}
	
	private static Uniform tryGetTextureUniform(int program, String code, RawUniform u, TexturesContext nextTextureSlot) {
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
			return new TextureUniform(program, nextTextureSlot.nextTextureSlot++, u.name, texture, path);
		} else { // normal texture, loaded from user files
			Texture texture = Texture.loadTexture(path);
			return new TextureUniform(program, nextTextureSlot.nextTextureSlot++, u.name, texture, path);
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
			int arrayLength = _size[0];
			int type = _type[0];
			_name.get(nameBytes, 0, nameLength);
			_name.position(0);
			String name = new String(nameBytes, 0, nameLength);
			if(name.endsWith("[0]")) name = name.substring(0, name.length()-3);
			RawUniform u = new RawUniform(GLUniformType.getFromGLTypeId(type), name, arrayLength);
			if(u.type == null) {
				Main.logger.warn("Unsupported uniform type for '" + name + "'");
				continue;
			}
			uniforms.add(u);
			Pattern namePattern = Pattern.compile(Pattern.quote(name) + "\\W");
			Matcher nameMatcher = namePattern.matcher(code);
			firstOccurences.put(u, nameMatcher.find() ? nameMatcher.start() : code.length());
		}
		uniforms.sort(Comparator.comparingInt(firstOccurences::get));
		return uniforms;
	}
	
	private float[][] getUniformFloatValues(int program, RawUniform uniform) {
		float[][] values = new float[uniform.arrayLength][uniform.type.typeSize];
		for(int i = 0; i < uniform.arrayLength; i++)
			glGetUniformfv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), values[i]);
		return values;
	}
	
	private int[][] getUniformIntValues(int program, RawUniform uniform) {
		int[][] values = new int[uniform.arrayLength][uniform.type.typeSize];
		for(int i = 0; i < uniform.arrayLength; i++)
			glGetUniformiv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), values[i]);
		return values;
	}
	
	private boolean[] getUniformBoolValues(int program, RawUniform uniform) {
		boolean[] values = new boolean[uniform.arrayLength];
		int[] cache = new int[1];
		for(int i = 0; i < uniform.arrayLength; i++) {
			glGetUniformiv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), cache);
			values[i] = cache[0] != 0;
		}
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
			if(ImGui.isItemHovered())
				ImGui.setTooltip("Copy all uniforms to clipboard, unused uniforms will be discarded");
			ImGui.sameLine();
			if(ImGui.button("reset uniforms")) {
				for(Uniform u : uniforms) {
					if(u instanceof ArbitraryUniform)
						((ArbitraryUniform) u).copy(originalUniformValues.get(u.name));
				}
			}
			Time.renderControls();
			for(Uniform u : uniforms)
				u.renderControl();
		}
		ImGui.end();
	}
	
}

class TexturesContext {
	
	public int nextTextureSlot;
	
	TexturesContext(int firstTextureSlot) {
		this.nextTextureSlot = firstTextureSlot;
	}
}

record RawBuiltinUniform(GLUniformType type, String name, BuiltinUniformGenerator generator) {

}

@FunctionalInterface
interface BuiltinUniformGenerator {
	
	Uniform create(String name, int program);
	
}

class RawUniform {

	RawUniform(GLUniformType type, String name, int arrayLength) {
		this.type = type;
		this.name = name;
		this.arrayLength = arrayLength;
	}

	final GLUniformType type;
	final String name;
	final int arrayLength; // -1 for non-arrays and undefined-length arrays

}
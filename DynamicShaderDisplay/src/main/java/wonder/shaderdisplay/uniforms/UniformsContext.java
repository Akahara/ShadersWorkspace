package wonder.shaderdisplay.uniforms;

import imgui.ImGui;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.scene.RenderableLayer;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.uniforms.GLUniformType.FloatUniformControl;
import wonder.shaderdisplay.uniforms.GLUniformType.IntUniformControl;
import wonder.shaderdisplay.uniforms.arbitrary.*;
import wonder.shaderdisplay.uniforms.predefined.*;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL20.*;

public class UniformsContext {
	
	private static final List<RawBuiltinUniform> BUILTIN_UNIFORMS = List.of(
			new RawBuiltinUniform(GLUniformType.FLOAT, "iTime",        TimeUniform::new),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_time",       TimeUniform::new),
			new RawBuiltinUniform(GLUniformType.INT,   "iFrame",       (n,p) -> new FrameUniform(n,p,false)),
			new RawBuiltinUniform(GLUniformType.INT,   "u_frame",      (n,p) -> new FrameUniform(n,p,false)),
			new RawBuiltinUniform(GLUniformType.FLOAT, "iFrame",       (n,p) -> new FrameUniform(n,p,true)),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_frame",      (n,p) -> new FrameUniform(n,p,true)),
			new RawBuiltinUniform(GLUniformType.IVEC2, "iResolution",  (n,p) -> new ResolutionUniform(n,p,false)),
			new RawBuiltinUniform(GLUniformType.IVEC2, "u_resolution", (n,p) -> new ResolutionUniform(n,p,false)),
			new RawBuiltinUniform(GLUniformType.VEC2,  "iResolution",  (n,p) -> new ResolutionUniform(n,p,true)),
			new RawBuiltinUniform(GLUniformType.VEC2,  "u_resolution", (n,p) -> new ResolutionUniform(n,p,true)),
			new RawBuiltinUniform(GLUniformType.MAT4,  "u_view",       ViewUniforms.ViewMatrixUniform::new),
			new RawBuiltinUniform(GLUniformType.VEC3,  "u_viewPosition", ViewUniforms.ViewPositionUniform::new),
			new RawBuiltinUniform(GLUniformType.VEC3,  "u_viewDirection", ViewUniforms.ViewDirectionUniform::new),
			new RawBuiltinUniform(GLUniformType.FLOAT, "u_framerate",  FramerateUniform::new)
	);
	
	private List<Uniform> uniforms = new ArrayList<>();
	// uniforms that had values at some point, values are kept in memory
	// to be able to be retrieved later (opengl gets rid of uniforms that
	// are not used in code)
	private final Map<String, ArbitraryUniform> oldArbitraryUniforms = new HashMap<>();
	private final Map<String, Number[][]> originalUniformValues = new HashMap<>();

	private final RenderableLayer layer;

	public UniformsContext(RenderableLayer layer) {
		this.layer = Objects.requireNonNull(layer);
	}

	public void rescan(int program, String code) {
		uniforms = new ArrayList<>();
		originalUniformValues.clear();
		
		List<RawUniform> newUniforms = scanForUniforms(program, code);
		
		for(RawUniform u : newUniforms) {
			// find built-in uniforms (iTime/iFrame...)
			Uniform uniformAsBuiltin = tryGetBuiltinUniform(program, u);
			if(uniformAsBuiltin != null) {
				uniforms.add(uniformAsBuiltin);
				continue;
			}
			
			// handle textures
			if(u.type == GLUniformType.SAMPLER2D) {
				uniforms.add(getTextureUniform(program, code, u));
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
	
	private static Uniform getTextureUniform(int program, String code, RawUniform u) {
		Pattern texturePattern = Pattern.compile("\nuniform sampler2D " + Pattern.quote(u.name) + ";\\s+//(.+)");
		Matcher matcher = texturePattern.matcher(code);

		if (!matcher.find())
			return new TextureUniform(program, u.name);
		
		String path = matcher.group(1).trim();
		
		if(path.matches("builtin \\d+")) { // default texture, loaded from resources
			int buitinId = Integer.parseInt(path.substring("builtin ".length()));
			Texture texture = Texture.loadTextureFromResources(buitinId);
			return new TextureUniform(program, u.name, texture, path);
		} else if (path.matches("input \\d+")) {
			return new TextureUniform(program, u.name, Integer.parseInt(path.substring("input ".length())));
		} else if (!path.isEmpty()) { // normal texture, loaded from user files
			Texture texture = Texture.loadTexture(Paths.get(path).toFile());
			return new TextureUniform(program, u.name, texture, path);
		} else { // no texture specified, might use render targets if specified in the scene file
			return new TextureUniform(program, u.name);
		}
	}
	
	private static List<RawUniform> scanForUniforms(int program, String code) {
		int uniformCount = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
		List<RawUniform> uniforms = new ArrayList<>();
		Map<RawUniform, Integer> firstOccurrences = new HashMap<>();
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
			firstOccurrences.put(u, nameMatcher.find() ? nameMatcher.start() : code.length());
		}
		uniforms.sort(Comparator.comparingInt(firstOccurrences::get));
		return uniforms;
	}
	
	private float[][] getUniformFloatValues(int program, RawUniform uniform) {
		float[][] values = new float[uniform.arrayLength][uniform.type.typeSize];
		if (uniform.arrayLength == 1) {
			glGetUniformfv(program, glGetUniformLocation(program, uniform.name), values[0]);
		} else {
			for(int i = 0; i < uniform.arrayLength; i++)
				glGetUniformfv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), values[i]);
		}
		return values;
	}
	
	private int[][] getUniformIntValues(int program, RawUniform uniform) {
		int[][] values = new int[uniform.arrayLength][uniform.type.typeSize];
		if (uniform.arrayLength == 1) {
			glGetUniformiv(program, glGetUniformLocation(program, uniform.name), values[0]);
		} else {
			for(int i = 0; i < uniform.arrayLength; i++)
				glGetUniformiv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), values[i]);
		}
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
	
	public void apply(Scene scene) {
		UniformApplicationContext context = new UniformApplicationContext(scene, layer);
		for(Uniform u : uniforms)
			u.apply(context);
	}
	
	public void renderControls() {
		if (uniforms.stream().anyMatch(Uniform::isUserEditable)) {
			ImGui.sameLine();
			if(ImGui.button("Copy")) {
				StringBuilder clipboardText = new StringBuilder();
				for(Uniform u : uniforms) {
					if (u.isUserEditable()) {
						clipboardText.append(u.toUniformString());
						clipboardText.append('\n');
					}
				}
				ImGui.setClipboardText(clipboardText.toString());
			}
			if(ImGui.isItemHovered())
				ImGui.setTooltip("Copy all editable uniforms to clipboard, unused uniforms will be discarded");
			ImGui.sameLine();
			if(ImGui.button("Reset")) {
				for(Uniform u : uniforms) {
					if(u instanceof ArbitraryUniform)
						((ArbitraryUniform) u).copy(originalUniformValues.get(u.name));
				}
			}
		}
		for(Uniform u : uniforms)
			u.renderControl();
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
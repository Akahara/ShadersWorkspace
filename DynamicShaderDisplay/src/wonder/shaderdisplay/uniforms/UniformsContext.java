package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glGetUniformfv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.lwjgl.opengl.GL40;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Texture;
import wonder.shaderdisplay.TexturesSwapChain;
import wonder.shaderdisplay.Time;

public class UniformsContext {
	
	private static final Map<String, Integer> TYPE_SIZES = Map.of(
			"float", 1, "vec2", 2, "vec3", 3, "vec4", 4,
			"mat2", 4, "mat3", 9, "mat4", 16);
	
	private static final Map<String, UniformControl> COLOR_CONTROLS = Map.of(
			"vec3", new ControlColorN(3),
			"vec4", new ControlColorN(4));
	
	private static final Map<String, UniformControl> STANDARD_CONTROLS = Map.of(
			"float", new ControlVecN(1),
			"vec2", new ControlVecN(2),
			"vec3", new ControlVecN(3),
			"vec4", new ControlVecN(4),
			"mat2", new ControlMatN(2),
			"mat3", new ControlMatN(3),
			"mat4", new ControlMatN(4)
	);
	
	private static final Map<String, GlUniformFloatFunction> UNIFORM_FUNCTIONS = Map.of(
			"float", GL40::glUniform1fv,
			"vec2", GL40::glUniform2fv,
			"vec3", GL40::glUniform3fv,
			"vec4", GL40::glUniform4fv,
			"mat2", (loc, value) -> GL40.glUniformMatrix2fv(loc, false, value),
			"mat3", (loc, value) -> GL40.glUniformMatrix3fv(loc, false, value),
			"mat4", (loc, value) -> GL40.glUniformMatrix4fv(loc, false, value)
	);

	
	private List<Uniform> uniforms = new ArrayList<>();
	
	public UniformsContext() {}
	
	
	
	public void rescan(int program, String code) {
		Map<String, Uniform> oldUniforms = uniforms.stream().collect(Collectors.toMap(u->u.name, u->u));
		uniforms = new ArrayList<>();
		
		// find iTime
		if(code.contains("uniform float iTime;"))
			uniforms.add(new TimeUniform(program));
		// find iFrame
		if(code.contains("uniform float iFrame;"))
			uniforms.add(new FrameUniform(program)); // TODO implement int uniforms
		// find iResolution
		if(code.contains("uniform vec2 iResolution;"))
			uniforms.add(new ResolutionUniform(program));
		
		{ // find textures
			Pattern texturePattern = Pattern.compile("\nuniform sampler2D (\\w+);\\s+//[ \t]*(.+)");
			Matcher matcher = texturePattern.matcher(code);
			
			// the first N binding points are reserved for rendertargets, the remaining ones are standard textures
			int nextTextureSlot = TexturesSwapChain.RENDER_TARGET_COUNT;
			
			while(matcher.find()) {
				String name = matcher.group(1);
				String path = matcher.group(2);
				
				if(path.matches("target \\d+")) { // rendering target texture
					int target = Integer.parseInt(path.substring("target ".length()));
					if(target < 0 || target >= TexturesSwapChain.RENDER_TARGET_COUNT) {
						Main.logger.err("Invalid render target '" + target + "' for uniform '" + name + "', available are 0.." + (TexturesSwapChain.RENDER_TARGET_COUNT-1));
						target = 0;
					}
					uniforms.add(new TargetTextureUniform(program, target, name, target));
				} else if(path.matches("builtin \\d+")) { // default texture, loaded from resources
					int buitinId = Integer.parseInt(path.substring("builtin ".length()));
					Texture texture = Texture.loadTextureFromResources(buitinId);
					uniforms.add(new TextureUniform(program, nextTextureSlot++, name, texture, path));
				} else { // normal texture, loaded from user files
					Texture texture = Texture.loadTexture(path);
					uniforms.add(new TextureUniform(program, nextTextureSlot++, name, texture, path));
				}
			}
		}
		
		List<RawUniform> foundUniforms = new ArrayList<>();
		{ // find other uniforms
			Pattern uniformPattern = Pattern.compile("\nuniform (float|vec[234]|mat[234])(\\[\\d*\\])? ([^;]+);");
			Pattern declarationPattern = Pattern.compile("([a-zA-Z_0-9]+)\\s*(?:=([^;]*))?");
			Matcher matcher = uniformPattern.matcher(code);
			while(matcher.find()) {
				String type = matcher.group(1);
				String arrayMarker = matcher.group(2);
				String declarations = matcher.group(3);
				
				int arrayLength = arrayMarker != null && arrayMarker.length() > 2 ?
						Integer.parseInt(arrayMarker.substring(1, arrayMarker.length()-1)) : -1;
				
				List<String> declaredVariables = parseDeclaredVariables(declarations);
				for(String decl : declaredVariables) {
					Matcher declMatcher = declarationPattern.matcher(decl);
					if(!declMatcher.find()) {
						Main.logger.err("Could not read uniform '" + decl + "'");
						continue;
					}
					String name = declMatcher.group(1).strip();
					String defaultValue = declMatcher.group(2);
					defaultValue = defaultValue == null ? null : defaultValue.strip();
					
					RawUniform uniform = new RawUniform();
					uniform.name = name;
					uniform.typeName = type;
					uniform.defaultValue = defaultValue;
					uniform.isArray = arrayMarker != null;
					uniform.arrayLength = arrayLength;
					foundUniforms.add(uniform);
				}
			}
		}
		
		{ // find other uniforms
			for(RawUniform u : foundUniforms) {
				if(getUniform(u.name) != null)
					continue; // skip built-in uniforms (iTime...)
				if(u.isArray && u.arrayLength <= 0) {
					Main.logger.warn("Size of uniform '" + u.name + "' is unspecified, cannot create a control for it");
					continue;
				}
				String type = u.typeName;
				int valueSize = TYPE_SIZES.getOrDefault(type, -1);
				GlUniformFloatFunction uniformFunction = UNIFORM_FUNCTIONS.get(type);
				UniformControl control = (u.name.startsWith("c_") ? COLOR_CONTROLS : STANDARD_CONTROLS).get(type);
				List<float[]> initialValues = getUniformValues(program, u, valueSize);
				
				if(valueSize == -1 || control == null || uniformFunction == null) {
					Main.logger.err("Could not create a control for uniform '" + u.name + "' with type '" + u.typeName + "'");
					continue;
				}
				
				ArbitraryUniform uniform = new ArbitraryUniform(program, u.name, uniformFunction,
						control, u.isArray, valueSize, initialValues);
				uniform.copy(oldUniforms.get(u.name));
				uniforms.add(uniform);
			}
		}
		
		if(!uniforms.isEmpty()) {
			Iterable<String> iter = () -> uniforms.stream().map(u -> u.name).iterator();
			Main.logger.debug("Bound uniforms: " + String.join(" ", iter));
		}
	}
	
	private List<float[]> getUniformValues(int program, RawUniform uniform, int valueSize) {
		if(!uniform.isArray) {
			float[] value = new float[valueSize];
			glGetUniformfv(program, glGetUniformLocation(program, uniform.name), value);
			return new ArrayList<>(Arrays.asList(value));
		}
		
		if(uniform.arrayLength > 0) {
			List<float[]> values = new ArrayList<>();
			for(int i = 0; i < uniform.arrayLength; i++) {
				float[] value = new float[valueSize];
				glGetUniformfv(program, glGetUniformLocation(program, uniform.name+'['+i+']'), value);
				values.add(value);
			}
			return values;
		}
		
		return new ArrayList<>();
	}

	private static List<String> parseDeclaredVariables(String declarations) {
		List<String> splits = new ArrayList<>();
		int lastSplit = 0, openedParentheses = 0;
		for(int i = 0; i < declarations.length(); i++) {
			char c = declarations.charAt(i);
			if(c == ',' && openedParentheses == 0) {
				splits.add(declarations.substring(lastSplit, i));
				lastSplit = i+1;
			} else if(c == '(') {
				openedParentheses++;
			} else if(c == ')') {
				openedParentheses--;
			}
		}
		if(lastSplit != declarations.length())
			splits.add(declarations.substring(lastSplit));
		return splits;
	}
	
	private Uniform getUniform(String name) {
		for(Uniform u : uniforms)
			if(u.name.equals(name))
				return u;
		return null;
	}
	
	public void apply() {
		for(Uniform u : uniforms)
			u.apply();
	}
	
	public void renderControls(String name) {
		ImGui.setNextWindowCollapsed(true, ImGuiCond.Once);
		if(ImGui.begin(name)) {
			Time.renderControls();
			for(Uniform u : uniforms)
				u.renderControl();
		}
		ImGui.end();
	}
	
}

class RawUniform {
	
	String typeName;
	String name;
	String defaultValue;
	boolean isArray;
	int arrayLength; // -1 for non-arrays and undefined-length arrays
	
	@Override
	public String toString() {
		return String.format("%s%s %s = %s",
				typeName, isArray?"["+arrayLength+"]":"", name, defaultValue);
	}
}
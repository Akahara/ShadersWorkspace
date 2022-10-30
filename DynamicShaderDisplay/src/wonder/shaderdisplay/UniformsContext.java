package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import fr.wonder.commons.exceptions.UnreachableException;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;

public class UniformsContext {
	
	private List<Uniform> uniforms = new ArrayList<>();
	
	public UniformsContext() {}
	
	private abstract static class Uniform {
		
		protected final int loc;
		public final String name;
		
		Uniform(int program, String name) {
			this.name = name;
			this.loc = glGetUniformLocation(program, name);
		}
		
		abstract void apply();
		abstract void renderControl();
		void step(float delta) {}
		
	}
	
	public void scan(int program, String code) {
		Map<String, Uniform> oldUniforms = uniforms.stream().collect(Collectors.toMap(u->u.name, u->u));
		uniforms = new ArrayList<>();
		
		// find iTime
		if(code.contains("uniform float iTime;"))
			uniforms.add(new TimeUniform(program).copy(oldUniforms.get("iTime")));
		
		// find iResolution
		if(code.contains("uniform vec2 iResolution;"))
			uniforms.add(new ResolutionUniform(program));
		
		{ // find textures
			Pattern texturePattern = Pattern.compile("\nuniform sampler2D (\\w+);\\s+//[ \t]*(.+)");
			Matcher matcher = texturePattern.matcher(code);
			
			for(int i = 0; matcher.find(); i++) {
				String name = matcher.group(1);
				String path = matcher.group(2);
				uniforms.add(new TextureUniform(program, i, name, path));
			}
		}
		
		{ // find other uniforms
			Map<String, Integer> vecTypesSizes = Map.of("float", 1, "vec2", 2, "vec3", 3, "vec4", 4);
			Map<String, Integer> matTypesSizes = Map.of("mat2", 2, "mat3", 3, "mat4", 4);
			Pattern pattern = Pattern.compile("\nuniform (float|vec[234]|mat[234]) (\\w+);");
			Matcher matcher = pattern.matcher(code);
			
			while(matcher.find()) {
				String type = matcher.group(1);
				String name = matcher.group(2);
				if(getUniform(name) != null)
					continue;
				if(vecTypesSizes.containsKey(type)) {
					int size = vecTypesSizes.get(type);
					uniforms.add(new ArbitraryUniformNF(program, name, size).copy(oldUniforms.get(name)));
				} else {
					int size = matTypesSizes.get(type);
					uniforms.add(new ArbitraryUniformM(program, name, size).copy(oldUniforms.get(name)));
				}
			}
		}
		
		if(!uniforms.isEmpty()) {
			Iterable<String> iter = () -> uniforms.stream().map(u -> u.name).iterator();
			Main.logger.debug("Bound uniforms: " + String.join(" ", iter));
		}
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
		if(!ImGui.collapsingHeader(name))
			return;
		for(Uniform u : uniforms)
			u.renderControl();
	}
	
	public void step(float delta) {
		for(Uniform u : uniforms)
			u.step(delta);
	}
	
	private static class TimeUniform extends Uniform {
		
		private float currentTime;
		private ImBoolean paused = new ImBoolean();
		
		private TimeUniform(int program) {
			super(program, "iTime");
		}
		
		void step(float d) {
			if(!paused.get())
				currentTime += d;
		}
		
		@Override
		void apply() {
			glUniform1f(loc, currentTime);
		}
		
		@Override
		void renderControl() {
			ImGui.checkbox("Pause iTime", paused);
			
			if(ImGui.button("Reset iTime"))
				currentTime = 0;
			
			if(!paused.get()) ImGui.beginDisabled();
			float[] ptr = new float[] { currentTime };
			ImGui.dragFloat("iTime", ptr, .01f);
			currentTime = ptr[0];
			if(!paused.get()) ImGui.endDisabled();
		}
		
		TimeUniform copy(Uniform u) {
			if(u instanceof TimeUniform) {
				currentTime = ((TimeUniform) u).currentTime;
				paused.set(((TimeUniform) u).paused);
			}
			return this;
		}
	};
	
	private static class ResolutionUniform extends Uniform {
		
		int w, h;
		
		private ResolutionUniform(int program) {
			super(program, "iResolution");
		}
		
		@Override
		void apply() {
			if(w != GLWindow.winWidth || h != GLWindow.winHeight) {
				glUniform2f(loc, GLWindow.winWidth, GLWindow.winHeight);
				w = GLWindow.winWidth;
				h = GLWindow.winHeight;
			}
		}
		
		@Override
		void renderControl() {
			ImGui.beginDisabled();
			ImGui.dragFloat2(name, new float[] { GLWindow.winWidth, GLWindow.winHeight });
			ImGui.endDisabled();
		}
		
	}
	
	private static class TextureUniform extends Uniform {
		
		private final Texture texture;
		private final String texturePath;
		private final int textureIndex;
		
		TextureUniform(int program, int textureIndex, String name, String path) {
			super(program, name);
			this.texture = Texture.loadTexture(path);
			this.texturePath = path;
			this.textureIndex = textureIndex;
		}
		
		@Override
		void apply() {
			glUniform1i(loc, textureIndex);
			glActiveTexture(GL_TEXTURE0 + textureIndex);
			glBindTexture(GL_TEXTURE_2D, texture.id);
		}
		
		@Override
		void renderControl() {
			ImGui.beginDisabled();
			ImGui.inputText(name, new ImString(texturePath));
			ImGui.endDisabled();
		}
		
	}
	
	private static class ArbitraryUniformNF extends Uniform {
		
		private float[] ptr;
		
		public ArbitraryUniformNF(int program, String name, int size) {
			super(program, name);
			this.ptr = new float[size];
		}
		
		@Override
		void apply() {
			if(ptr.length == 1)      glUniform1f(loc, ptr[0]);
			else if(ptr.length == 2) glUniform2f(loc, ptr[0], ptr[1]);
			else if(ptr.length == 3) glUniform3f(loc, ptr[0], ptr[1], ptr[2]);
			else if(ptr.length == 4) glUniform4f(loc, ptr[0], ptr[1], ptr[2], ptr[3]);
			else throw new UnreachableException("Invalid uniform size " + ptr.length);
		}
		
		@Override
		void renderControl() {
			if(ptr.length == 1)      ImGui.dragFloat (name, ptr, .01f);
			else if(ptr.length == 2) ImGui.dragFloat2(name, ptr, .01f);
			else if(ptr.length == 3) ImGui.dragFloat3(name, ptr, .01f);
			else if(ptr.length == 4) ImGui.dragFloat4(name, ptr, .01f);
		}
		
		ArbitraryUniformNF copy(Uniform u) {
			if(u instanceof ArbitraryUniformNF && ptr.length == ((ArbitraryUniformNF) u).ptr.length)
				ptr = ((ArbitraryUniformNF) u).ptr;
			return this;
		}
		
	}
	
	private static class ArbitraryUniformM extends Uniform {
		
		private float[] ptr;
		private int size;
		
		public ArbitraryUniformM(int program, String name, int size) {
			super(program, name);
			this.size = size;
			this.ptr = new float[size*size];
		}
		
		@Override
		void apply() {
			switch(size) {
			case 2: glUniformMatrix2fv(loc, false, ptr); break;
			case 3: glUniformMatrix3fv(loc, false, ptr); break;
			case 4: glUniformMatrix4fv(loc, false, ptr); break;
			default: throw new UnreachableException("Invalid matrix uniform size " + size);
			}
		}
		
		@Override
		void renderControl() {
			float[] p = new float[1];
			ImGui.text(name);
			ImGui.beginTable(name, size);
			for(int i = 0; i < size; i++) {
				ImGui.tableNextRow();
				for(int j = 0; j < size; j++) {
					p[0] = ptr[i*size+j];
					ImGui.tableNextColumn();
					ImGui.dragFloat("##"+name+","+i+","+j, p, .1f);
					ptr[i*size+j] = p[0];
				}
			}
			ImGui.endTable();
		}
		
		ArbitraryUniformM copy(Uniform u) {
			if(u instanceof ArbitraryUniformM && size == ((ArbitraryUniformM) u).size)
				ptr = ((ArbitraryUniformM) u).ptr;
			return this;
		}
		
	}

}

package wonder.shaderdisplay;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniformsContext {
	
	private List<Uniform> uniforms = new ArrayList<>();
	
	private UniformsContext() {}
	
	private static class Uniform {
		
		final int loc;
		final String name;
		
		Uniform(int program, String name) {
			this.name = name;
			this.loc = glGetUniformLocation(program, name);
		}
		
		void apply() {}
		void reapply() {}
		
	}
	
	public static UniformsContext scan(int program, String code) {
		UniformsContext context = new UniformsContext();
		
		if(code.contains("uniform float iTime;"))
			context.uniforms.add(new TimeUniform(program));
		
		if(code.contains("uniform vec2 iResolution;"))
			context.uniforms.add(new ResolutionUniform(program));
		
		Pattern texturePattern = Pattern.compile("\nuniform sampler2D (\\w+);\\s+//\\s+(.+)");
		Matcher matcher = texturePattern.matcher(code);
		
		for(int i = 0; matcher.find(); i++) {
			String name = matcher.group(1);
			String path = matcher.group(2);
			context.uniforms.add(new TextureUniform(program, i, name, path));
		}
		
		if(!context.uniforms.isEmpty()) {
			Iterable<String> iter = () -> context.uniforms.stream().map(u -> u.name).iterator();
			Main.logger.debug("Bound uniforms: " + String.join(" ", iter));
		}
		
		return context;
	}
	
	public void apply() {
		for(Uniform u : uniforms)
			u.apply();
	}
	
	public void reapply() {
		for(Uniform u : uniforms)
			u.reapply();
	}
	
	public static void setTimeUniform(float time) {
		TimeUniform.firstNano = System.nanoTime()-(long)(time*1E9f);
	}
	
	public static float getTimeUniform() {
		return (System.nanoTime() - TimeUniform.firstNano) / 1E9f;
	}
	
	private static class TimeUniform extends Uniform {
		
		private static long firstNano = System.nanoTime();
		
		private TimeUniform(int program) {
			super(program, "iTime");
		}
		
		@Override
		public void apply() {
		}
		
		@Override
		public void reapply() {
			float time = (System.nanoTime()-firstNano)/1E9f;
			glUniform1f(loc, time);
		}
	};	
	
	private static class ResolutionUniform extends Uniform {
		
		int w, h;
		
		private ResolutionUniform(int program) {
			super(program, "iResolution");
		}
		
		@Override
		public void reapply() {
			if(w != GLWindow.winWidth || h != GLWindow.winHeight) {
				glUniform2f(loc, GLWindow.winWidth, GLWindow.winHeight);
				w = GLWindow.winWidth;
				h = GLWindow.winHeight;
			}
		}
		
	}
	
	private static class TextureUniform extends Uniform {
		
		private final Texture texture;
		private final int textureIndex;
		
		TextureUniform(int program, int textureIndex, String name, String path) {
			super(program, name);
			this.texture = Texture.loadTexture(path);
			this.textureIndex = textureIndex;
		}
		
		@Override
		public void apply() {
			glUniform1i(loc, textureIndex);
			glActiveTexture(GL_TEXTURE0 + textureIndex);
			glBindTexture(GL_TEXTURE_2D, texture.id);
		}
		
	}


	
}

package wonder.shaderdisplay;

import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL20.*;

class Uniforms {
	
	private static List<Uniform> uniforms = new ArrayList<>();
	
	private static class Uniform {
		
		protected final int loc;
		
		Uniform(int program, String name) {
			this.loc = glGetUniformLocation(program, name);
		}
		
		void apply() {}
		void reapply() {}
		
	}
	
	static void scan(int program, String code) {
		uniforms.clear();
		
		String bound = "";
		
		if(code.contains("uniform float iTime;")) {
			uniforms.add(new TimeUniform(program));
			bound += "iTime ";
		}
		
		if(code.contains("uniform vec2 iResolution;")) {
			uniforms.add(new ResolutionUniform(program));
			bound += "iResolution ";
		}
		
		for(int i = 0; i < 4; i++) {
			if(code.contains("uniform sampler2D tex"+i+";")) {
				uniforms.add(new TextureUniform(program, i));
				bound += "tex"+i + " ";
			}
		}
		
		if(!bound.isEmpty())
			System.out.println("Bound uniforms: " + bound);
	}
	
	static void apply() {
		for(Uniform u : uniforms)
			u.apply();
	}
	
	static void reapply() {
		for(Uniform u : uniforms)
			u.reapply();
	}
	
	private static class TimeUniform extends Uniform {
		
		private static final long firstNano = System.nanoTime();
		
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
		
		TextureUniform(int program, int sample) {
			super(program, "tex"+sample);
			this.texture = new Texture("sample-"+sample+".png");
		}
		
		@Override
		public void reapply() {
			glBindTexture(GL_TEXTURE_2D, texture.id);
		}
		
	}
	
}

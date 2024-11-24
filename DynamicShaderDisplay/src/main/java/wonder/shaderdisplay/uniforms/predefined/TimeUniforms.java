package wonder.shaderdisplay.uniforms.predefined;

import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;

import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.controls.Timeline;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

public class TimeUniforms {

	public static class TimeUniform extends NonEditableUniform {

		private final int location;

		public TimeUniform(String name, int program) {
			super(name);
			this.location = ValueLocationCache.getLocation(program, name);
		}

		@Override
		public void apply(UniformApplicationContext context) {
			glUniform1f(location, Time.getTime());
		}

		@Override
		public void renderControl() {
			Timeline.renderTimeControls();
		}

	}

	public static class TimePausedUniform extends NonEditableUniform {

		private final int location;

		public TimePausedUniform(String name, int program) {
			super(name);
			this.location = ValueLocationCache.getLocation(program, name);
		}

		@Override
		public void apply(UniformApplicationContext context) {
			glUniform1i(location, Time.isPaused() ? 1 : 0);
		}

		@Override
		public void renderControl() {
			Timeline.renderTimeControls();
		}

	}

}
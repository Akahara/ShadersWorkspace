package wonder.shaderdisplay.uniforms.predefined;

import org.joml.Vector2f;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

import static org.lwjgl.opengl.GL20.*;

public class UserInputUniforms {

    public static class ClickUniform extends NonEditableUniform {

        private final int location;

        public ClickUniform(String name, int program) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
        }

        @Override
        public void apply(UniformApplicationContext context) {
            if (userControls == null) return;
            glUniform1i(location, userControls.getClickState());
        }

        @Override
        public void renderControl() {}

    }

    public static class MousePositionUniform extends NonEditableUniform {

        private final int location;
        private final boolean isFloat;

        public MousePositionUniform(String name, int program, boolean isFloat) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
            this.isFloat = isFloat;
        }

        @Override
        public void apply(UniformApplicationContext context) {
            if (userControls == null) return;
            Vector2f p = userControls.getMousePosition();
            if (isFloat)
                glUniform2f(location, p.x, GLWindow.getWinHeight()-p.y);
            else
                glUniform2i(location, (int)p.x, (int)(GLWindow.getWinHeight()-p.y));
        }

        @Override
        public void renderControl() {}

    }
}

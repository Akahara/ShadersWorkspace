package wonder.shaderdisplay.uniforms.predefined;

import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import wonder.shaderdisplay.controls.UserControls;
import wonder.shaderdisplay.uniforms.EditableUniform;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

public class ViewUniforms {

    private static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    private static final Vector3fc UP = new Vector3f(0, 1, 0);

    public static class ViewPositionUniform extends NonEditableUniform {

        private final int location;

        public ViewPositionUniform(String name, int program) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
        }

        @Override
        public void apply(UniformApplicationContext context) {
            if (userControls == null) return;
            Vector3f pos = userControls.getViewPosition();
            glUniform3f(location, pos.x, pos.y, pos.z);
        }

        @Override
        public void renderControl() {}

    }

    public static class ViewDirectionUniform extends NonEditableUniform {

        private final int location;

        public ViewDirectionUniform(String name, int program) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
        }

        @Override
        public void apply(UniformApplicationContext context) {
            if (userControls == null) return;
            Vector3f dir = userControls.getViewRotation().transform(new Vector3f(FORWARD));
            glUniform3f(location, dir.x, dir.y, dir.z);
        }

        @Override
        public void renderControl() {}

    }

    public static class ViewMatrixUniform extends EditableUniform {

        private final float[] matrix = new float[16];
        private final int location;

        public ViewMatrixUniform(String name, int program) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
        }

        @Override
        public void apply(UniformApplicationContext context) {
            if (userControls != null) {
                Matrix4f mat = new Matrix4f();
                Vector3f pos = userControls.getViewPosition();
                Vector3f dir = userControls.getViewRotation().transform(new Vector3f(FORWARD));
                mat.lookAt(pos, dir.add(pos, new Vector3f()), UP);
                mat.get(matrix);
            }

            glUniformMatrix4fv(location, false, matrix);
        }

        @Override
        public void renderControl() {
            if (userControls == null) return;
            boolean needsUpdate = userControls.justMoved();
            Vector3f position = userControls.getViewPosition();
            Quaternionf rotation = userControls.getViewRotation();
            Quaternionf q = userControls.getViewRotation();
            final float r2d = (float)Math.PI / 180.f;
            float[] rawPosition = new float[] { position.x, position.y, position.z };
            float[] rawRotation = new float[] { rotation.x / r2d, rotation.y / r2d, rotation.z / r2d };
            var yaw = Math.atan2(2.0*(q.y*q.z + q.w*q.x), q.w*q.w - q.x*q.x - q.y*q.y + q.z*q.z);
            var pitch = Math.asin(-2.0*(q.x*q.z - q.w*q.y));
            var roll = Math.atan2(2.0*(q.x*q.y + q.w*q.z), q.w*q.w + q.x*q.x - q.y*q.y - q.z*q.z);
            needsUpdate |= ImGui.dragFloat3(name + ".position", rawPosition, .1f);
            needsUpdate |= ImGui.dragFloat3(name + ".rotation", rawRotation, 1.f);

            if (needsUpdate)
            {
                userControls.getViewPosition().set(rawPosition[0], rawPosition[1], rawPosition[2]);
                userControls.getViewRotation().rotateX(rawRotation[1] * r2d - rotation.y);
                userControls.getViewRotation().rotateLocalY(rawRotation[0] * r2d - rotation.x);
                userControls.getViewRotation().rotateZ(rawRotation[2] * r2d - rotation.z);
                userControls.setJustMoved();
            }
        }

        @Override
        public String toUniformString() {
            return String.format("uniform mat4 %s = mat4(%s);", name, Stream.of(matrix).map(Objects::toString).collect(Collectors.joining(", ")));
        }

    }
}

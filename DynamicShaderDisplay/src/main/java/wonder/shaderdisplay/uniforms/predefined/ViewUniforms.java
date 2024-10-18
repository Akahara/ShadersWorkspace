package wonder.shaderdisplay.uniforms.predefined;

import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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

    public static UserControls userControls;

    public static class ViewPositionUniform extends NonEditableUniform {

        private final int location;

        public ViewPositionUniform(String name, int program) {
            super(name);
            this.location = new ValueLocationCache(program, name).getLocation(0);
        }

        @Override
        public void apply(UniformApplicationContext context) {
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
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f dir = userControls.getViewRotation().transform(forward);
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
            updateMatrix();
        }

        @Override
        public void apply(UniformApplicationContext context) {
            glUniformMatrix4fv(location, false, matrix);
        }

        @Override
        public void renderControl() {
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
                updateMatrix();
            }
        }

        private void updateMatrix() {
            Matrix4f mat = new Matrix4f();
            if (userControls != null) {
                mat.rotate(userControls.getViewRotation().invert(new Quaternionf()));
                mat.translate(userControls.getViewPosition().negate(new Vector3f()));
            }
            //mat.rotate(-rotation[0] * r2d, 0, 1, 0);
            //mat.rotate(-rotation[1] * r2d, 1, 0, 0);
            //mat.rotate(-rotation[2] * r2d, 0, 0, 1);
            //mat.translate(-position[0], -position[1], -position[2]);
            mat.get(matrix);
        }

        @Override
        public String toUniformString() {
            return String.format("uniform mat4 %s = mat4(%s);", name, Stream.of(matrix).map(Objects::toString).collect(Collectors.joining(", ")));
        }

    }
}

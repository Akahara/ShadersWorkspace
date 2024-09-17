package wonder.shaderdisplay.display;

import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.scene.Macro;

import java.util.stream.Stream;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class WindowBlit {

    private static final int standardShader;
    private static final int depthInputShader;
    private static final int vao;

    static {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int vbo = glGenBuffers();
        int ibo = glGenBuffers();
        int[] indices = {0, 1, 2, 2, 3, 0};
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        glBindVertexArray(0);

        standardShader = glCreateProgram();
        depthInputShader = glCreateProgram();
        String source = Resources.readResource("/blit.fs");
        int vertex = ShaderCompiler.buildRawShader(Resources.readResource("/blit.vs"), GL_VERTEX_SHADER);
        int standardFragment = ShaderCompiler.buildRawShader(source, GL_FRAGMENT_SHADER);
        int depthInputFragment = ShaderCompiler.buildRawShader(ShaderCompiler.addMacroDefinitions(source, Stream.of(new Macro("DEPTH_INPUT"))), GL_FRAGMENT_SHADER);

        glAttachShader(standardShader, vertex);
        glAttachShader(standardShader, standardFragment);
        glLinkProgram(standardShader);
        glValidateProgram(standardShader);
        if (glGetProgrami(standardShader, GL_LINK_STATUS) == GL_FALSE || glGetProgrami(standardShader, GL_VALIDATE_STATUS) == GL_FALSE)
            throw new RuntimeException("Failed to build the blit shader");

        glAttachShader(depthInputShader, vertex);
        glAttachShader(depthInputShader, depthInputFragment);
        glLinkProgram(depthInputShader);
        glValidateProgram(depthInputShader);
        if (glGetProgrami(depthInputShader, GL_LINK_STATUS) == GL_FALSE || glGetProgrami(depthInputShader, GL_VALIDATE_STATUS) == GL_FALSE)
            throw new RuntimeException("Failed to build the depth blit shader");
    }

    public static void blitToScreen(Texture source, boolean drawBackground, float znear, float zfar) {
        source.bind(0);
        glBindVertexArray(vao);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        int shaderId = source.isDepth() ? depthInputShader : standardShader;
        glUseProgram(shaderId);
        glViewport(0, 0, GLWindow.getWinWidth(), GLWindow.getWinHeight());
        glUniform1i(glGetUniformLocation(shaderId, "u_background"), drawBackground ? 1 : 0);
        if (source.isDepth())
            glUniform2f(glGetUniformLocation(shaderId, "u_zrange"), znear, zfar);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public static void blitToScreen(Texture source, boolean drawBackground) {
        blitToScreen(source, drawBackground, 0, 1);
    }

}

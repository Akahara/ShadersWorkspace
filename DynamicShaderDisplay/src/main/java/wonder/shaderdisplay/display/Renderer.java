package wonder.shaderdisplay.display;

import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20.glUseProgram;

public class Renderer {

	public void render(Scene scene) {
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		for (SceneLayer layer : scene.layers) {
			scene.swapChain.preparePass(layer);
			glUseProgram(layer.compiledShaders.program);
			setupRenderState(layer.renderState);
			layer.shaderUniforms.apply(scene);
			layer.mesh.makeDrawCall();
			if (layer.builtinAddon != null) {
				switch (layer.builtinAddon) {
					case CLEAR_PASS -> glClear(GL_DEPTH_BUFFER_BIT);
				}
			}
			scene.swapChain.endPass();
		}

		setupRenderState(SceneLayer.RenderState.DEFAULT);
	}

	private void setupRenderState(SceneLayer.RenderState rs) {
		if (rs.isBlendingEnabled)
			glEnable(GL_BLEND);
		else
			glDisable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		glEnable(GL_DEPTH_TEST);
		glDepthFunc(rs.isDepthTestEnabled ? GL_LESS : GL_ALWAYS);
		glDepthMask(rs.isDepthWriteEnabled);

		if (rs.culling != SceneLayer.RenderState.Culling.NONE) {
			glEnable(GL_CULL_FACE);
			glCullFace(rs.culling == SceneLayer.RenderState.Culling.FRONT ? GL_FRONT : GL_BACK);
		} else {
			glDisable(GL_CULL_FACE);
		}
	}
	
}

package wonder.shaderdisplay.display;

import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20.glUseProgram;

public class Renderer {

	private final FrameBuffer clearFBO = new FrameBuffer();

	public void render(Scene scene) {
		for (SceneLayer layer : scene.layers) {
			switch (layer.sceneType) {
			case STANDARD_PASS:
				scene.swapChain.preparePass(layer);
				glUseProgram(layer.compiledShaders.program);
				setupRenderState(layer.renderState);
				layer.shaderUniforms.apply(scene);
				layer.mesh.makeDrawCall();
				scene.swapChain.endPass();
				break;
			case CLEAR_PASS:
				glClearColor(layer.clearColor[0], layer.clearColor[1], layer.clearColor[2], layer.clearColor[3]);
				glClearDepth(layer.clearDepth);
				for (String rt : layer.outRenderTargets) {
					Texture texture = scene.swapChain.getAttachment(rt);
					clearFBO.addAttachment(texture);
					clearFBO.bind();
					glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
					clearFBO.unbind();
					clearFBO.clearAttachments();
				}
				break;
			}
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

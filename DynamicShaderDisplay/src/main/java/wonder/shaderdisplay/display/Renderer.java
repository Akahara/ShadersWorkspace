package wonder.shaderdisplay.display;

import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;
import wonder.shaderdisplay.scene.SceneSSBOBinding;

import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

public class Renderer {

	private final FrameBuffer clearFBO = new FrameBuffer();

	public void render(Scene scene) {
		for (SceneLayer layer : scene.layers) {
			if (!layer.enabled)
				continue;

			switch (layer.sceneType) {
			case STANDARD_PASS:
				scene.swapChain.preparePass(layer);
				glUseProgram(layer.compiledShaders.program);
				setupRenderState(layer.renderState);
				setupBufferBindings(scene.storageBuffers, layer.storageBuffers);
				layer.shaderUniforms.apply(scene);
				layer.mesh.makeDrawCall();
				scene.swapChain.endPass();
				break;
			case COMPUTE_PASS:
				glUseProgram(layer.compiledShaders.program);
				setupBufferBindings(scene.storageBuffers, layer.storageBuffers);
				layer.shaderUniforms.apply(scene);
				glDispatchCompute(layer.computeDispatch.x, layer.computeDispatch.y, layer.computeDispatch.z);
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

	private void setupBufferBindings(Map<String, StorageBuffer> buffers, SceneSSBOBinding[] bindings) {
		for (int i = 0; i < bindings.length; i++) {
			SceneSSBOBinding binding = bindings[i];
			buffers.get(binding.name).bind(i, binding.offset, binding.size);
		}
	}

	private void setupRenderState(SceneLayer.RenderState rs) {
		if (rs.blendSrcA != null) {
			glEnable(GL_BLEND);
			glBlendFuncSeparate(
					rs.blendSrcRGB.glBlendMode,
					rs.blendDstRGB.glBlendMode,
					rs.blendSrcA.glBlendMode,
					rs.blendDstRGB.glBlendMode);

		} else {
			glDisable(GL_BLEND);
		}

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

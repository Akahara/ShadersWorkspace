package wonder.shaderdisplay.display;

import wonder.shaderdisplay.scene.*;

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

			if (layer instanceof SceneStandardLayer standardLayer) {
				scene.swapChain.preparePass(standardLayer);
				glUseProgram(standardLayer.compiledShaders.program);
				setupRenderState(standardLayer.renderState);
				setupBufferBindings(scene.storageBuffers, standardLayer.storageBuffers);
				standardLayer.shaderUniforms.apply(scene);
				standardLayer.mesh.makeDrawCall();
				scene.swapChain.endPass();
			} else if (layer instanceof SceneComputeLayer computeLayer) {
				glUseProgram(computeLayer.compiledShaders.program);
				setupBufferBindings(scene.storageBuffers, computeLayer.storageBuffers);
				computeLayer.shaderUniforms.apply(scene);
				glDispatchCompute(computeLayer.computeDispatch.x, computeLayer.computeDispatch.y, computeLayer.computeDispatch.z);
			} else if (layer instanceof SceneClearLayer clearLayer) {
				glClearColor(clearLayer.clearColor[0], clearLayer.clearColor[1], clearLayer.clearColor[2], clearLayer.clearColor[3]);
				glClearDepth(clearLayer.clearDepth);
				for (String rt : clearLayer.outRenderTargets) {
					Texture texture = scene.swapChain.getAttachment(rt);
					clearFBO.addAttachment(texture);
					clearFBO.bind();
					glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
					clearFBO.unbind();
					clearFBO.clearAttachments();
				}
			}
		}

		setupRenderState(RenderState.DEFAULT);
	}

	private void setupBufferBindings(Map<String, StorageBuffer> buffers, SceneSSBOBinding[] bindings) {
		for (int i = 0; i < bindings.length; i++) {
			SceneSSBOBinding binding = bindings[i];
			buffers.get(binding.name).bind(i, binding.offset, binding.size);
		}
	}

	private void setupRenderState(RenderState rs) {
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

		if (rs.culling != RenderState.Culling.NONE) {
			glEnable(GL_CULL_FACE);
			glCullFace(rs.culling == RenderState.Culling.FRONT ? GL_FRONT : GL_BACK);
		} else {
			glDisable(GL_CULL_FACE);
		}
	}
	
}

package wonder.shaderdisplay.display;

import wonder.shaderdisplay.controls.ShaderDebugTool;
import wonder.shaderdisplay.scene.*;

import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL43.*;

public class Renderer {

	private final FrameBuffer clearFBO = new FrameBuffer();
	private final int indirectDrawCallVAO = glGenVertexArrays();

	public void render(Scene scene, ShaderDebugTool debugTool) {
		if (debugTool != null)
			debugTool.reset();

		for (SceneLayer layer : scene.layers) {
			if (!layer.enabled)
				continue;

			if (layer instanceof SceneStandardLayer standardLayer) {
				scene.swapChain.preparePass(standardLayer);
				glUseProgram(standardLayer.compiledShaders.program);
				setupRenderState(standardLayer.renderState);
				setupBufferBindings(scene.storageBuffers, standardLayer.storageBuffers);
				if (debugTool != null)
					debugTool.tryBindToProgram(standardLayer.compiledShaders.program);
				standardLayer.shaderUniforms.apply(scene);
				if (standardLayer.mesh != null)
					standardLayer.mesh.makeDrawCall();
				if (standardLayer.indirectDraw != null)
					makeIndirectDrawCall(scene, standardLayer.indirectDraw);
				scene.swapChain.endPass();
			} else if (layer instanceof SceneComputeLayer computeLayer) {
				glUseProgram(computeLayer.compiledShaders.program);
				setupBufferBindings(scene.storageBuffers, computeLayer.storageBuffers);
				computeLayer.shaderUniforms.apply(scene);
				if (debugTool != null)
					debugTool.tryBindToProgram(computeLayer.compiledShaders.program);
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

	private void makeIndirectDrawCall(Scene scene, IndirectDrawDescription call) {
		glBindVertexArray(indirectDrawCallVAO);
		scene.storageBuffers.get(call.indirectArgsBuffer.name).bindToGLBindingPoint(GL_DRAW_INDIRECT_BUFFER);
		if (call.vertexBufferName != null) {
			scene.storageBuffers.get(call.vertexBufferName).bindToGLBindingPoint(GL_ARRAY_BUFFER);
			int stride = 4+3+2;
			glEnableVertexAttribArray(0);
			glEnableVertexAttribArray(1);
			glEnableVertexAttribArray(2);
			glVertexAttribPointer(0, 4, GL_FLOAT, false, stride*4, 4*0);
			glVertexAttribPointer(1, 3, GL_FLOAT, false, stride*4, 4*4);
			glVertexAttribPointer(2, 2, GL_FLOAT, false, stride*4, 4*(4+3));
		}
		if (call.indexBufferName != null) {
			scene.storageBuffers.get(call.indexBufferName).bindToGLBindingPoint(GL_ELEMENT_ARRAY_BUFFER);
			glMultiDrawElementsIndirect(call.topology.glType, GL_UNSIGNED_INT, call.indirectArgsBuffer.offset, call.indirectCallsCount, 0);
		} else {
			glMultiDrawArraysIndirect(call.topology.glType, call.indirectArgsBuffer.offset, call.indirectCallsCount, 0);
		}
		glBindVertexArray(0);
	}

	private void setupBufferBindings(Map<String, StorageBuffer> buffers, SSBOBinding[] bindings) {
		for (int i = 0; i < bindings.length; i++) {
			SSBOBinding binding = bindings[i];
			buffers.get(binding.name).bind(i, binding.offset, -1);
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

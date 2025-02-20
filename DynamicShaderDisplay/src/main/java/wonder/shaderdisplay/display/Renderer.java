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

	public void render(Scene scene, ShaderDebugTool debugTool, boolean hasReset) {
		if (debugTool != null)
			debugTool.reset();
		scene.sharedUniforms.reset();

		ExecutionCondition.ExecutionContext executionContext = new ExecutionCondition.ExecutionContext();
		executionContext.hasReset = hasReset;

		for (SceneLayer layer : scene.layers) {
			if (!layer.enabled)
				continue;

			for (ExecutionCondition cond : layer.executions) {
				if (!cond.isConditionPassing(executionContext))
					continue;
				for (int i = 0; i < cond.count; i++) {
					renderLayer(scene, debugTool, layer);
				}
			}
		}

		// TODO find out why shared uniforms don't keep their values
		// TODO add a button to reset render targets and trigger "on reset" layers

		setupRenderState(RenderState.DEFAULT);
	}

	private void renderLayer(Scene scene, ShaderDebugTool debugTool, SceneLayer layer) {
		if (layer instanceof SceneStandardLayer standardLayer) {
			scene.swapChain.preparePass(standardLayer);
			glUseProgram(standardLayer.compiledShaders.program);
			setupRenderState(standardLayer.renderState);
			setupBufferBindings(scene.storageBuffers, standardLayer.storageBuffers);
			if (debugTool != null)
				debugTool.tryBindToProgram(standardLayer.compiledShaders.program);
			standardLayer.shaderUniforms.apply(scene);
			if (standardLayer.mesh != null)
				standardLayer.mesh.makeDrawCall(standardLayer.vertexLayout);
			if (standardLayer.indirectDraw != null)
				makeIndirectDrawCall(scene, standardLayer.indirectDraw, standardLayer.vertexLayout);
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

	private void makeIndirectDrawCall(Scene scene, IndirectDrawDescription call, VertexLayout vertexLayout) {
		glBindVertexArray(indirectDrawCallVAO);
		scene.storageBuffers.get(call.indirectArgsBuffer.name).bindToGLBindingPoint(GL_DRAW_INDIRECT_BUFFER);
		if (call.vertexBufferName != null)
			scene.storageBuffers.get(call.vertexBufferName).bindToGLBindingPoint(GL_ARRAY_BUFFER);
		if (call.indexBufferName != null) {
			scene.storageBuffers.get(call.indexBufferName).bindToGLBindingPoint(GL_ELEMENT_ARRAY_BUFFER);
			vertexLayout.bind();
			glMultiDrawElementsIndirect(call.topology.glType, GL_UNSIGNED_INT, call.indirectArgsBuffer.offset, call.indirectCallsCount, 0);
		} else {
			vertexLayout.bind();
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

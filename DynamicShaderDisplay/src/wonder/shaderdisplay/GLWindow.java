package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glGetIntegeri;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import fr.wonder.commons.exceptions.GenerationException;

class GLWindow {

	private static final int SHADER_STORAGE_DATA_SIZE = 128;
	
	private static long window;
	private static int shaderStorageVertices, shaderStorageIndices;
	private static int standardShaderProgram, computeShaderProgram;
	private static int vertexShader, geometryShader, fragmentShader, computeShader;
	private static int bindableVAO;
	static int winWidth, winHeight;
	
	private static final List<Callback> closeableCallbacks = new ArrayList<>();

	static void createWindow(int width, int height) {
		winWidth = width;
		winHeight = height;

		GLFWErrorCallback.createPrint(System.err).set();
		
		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW !");

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
		
		window = glfwCreateWindow(width, height, "Shader Display", NULL, NULL);

		if (window == NULL)
			throw new IllegalStateException("Unable to create a window !");

		glfwMakeContextCurrent(window);
		glfwShowWindow(window);
		glfwFocusWindow(window);

		GL.createCapabilities();
		
		closeableCallbacks.add(GLUtil.setupDebugMessageCallback(System.err));
		
		glViewport(0, 0, width, height);
		glClearColor(0, 0, 0, 1);
		
		glBindVertexArray(bindableVAO = glGenVertexArrays());
		
		float[] vertices = {
				-1, -1, 0, 1,
				 1, -1, 0, 1,
				 1,  1, 0, 1,
				-1,  1, 0, 1,
				};
		int[] indices = { 6, 0, 1, 2, 2, 3, 0 };
		
		ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(SHADER_STORAGE_DATA_SIZE, vertices);
		ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(1+SHADER_STORAGE_DATA_SIZE, indices );
		
		shaderStorageVertices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageVertices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageVertices);

		shaderStorageIndices = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageIndices);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, shaderStorageIndices);
		
		glBindBuffer(GL_ARRAY_BUFFER, shaderStorageVertices);
		glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_STATIC_DRAW);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndices);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_STATIC_DRAW);
		
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, NULL);
		
		glfwSetWindowSizeCallback(window, (win, w, h) -> {
			glViewport(0, 0, w, h);
			winWidth = w;
			winHeight = h;
		});
		
		glfwSetKeyCallback(window, (win, key, scanCode, action, mods) -> {
			if(action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
				glfwSetWindowShouldClose(window, true);
			}
		});
	}

	public static boolean shouldDispose() {
		return glfwWindowShouldClose(window);
	}
	
	public static void dispose() {
		Callbacks.glfwFreeCallbacks(window);
		glfwSetErrorCallback(null).free();
		for(Callback callback : closeableCallbacks)
			callback.free();
		GL.setCapabilities(null);
		GL.destroy();
		glfwDestroyWindow(window);
		glfwTerminate();
		window = 0;
	}
	
	public static void setWindowTitle(String title) {
		glfwSetWindowTitle(window, title);
	}
	
	public static void render() {
		if(computeShaderProgram > 0) {
			glUseProgram(computeShaderProgram);
			glDispatchCompute(1, 1, 1);
		}
		
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram > 0) {
			glFinish();
			glUseProgram(standardShaderProgram);
			Uniforms.reapply();
			glBindVertexArray(bindableVAO);
			int triangleCount = BufferUtils.readBufferInt(GL_SHADER_STORAGE_BUFFER, 0);
			glDrawElements(GL_TRIANGLES, 3*triangleCount, GL_UNSIGNED_INT, 4);
		}
		
		glfwSwapBuffers(window);
		glfwPollEvents();
	}

	public static boolean compileShaders(String[] shaders) {
		int newFragment = 0, newVertex = 0, newGeometry = 0, newCompute = 0;
		int newStandardProgram = 0, newComputeProgram = 0;
		try {
			newVertex = buildShader(shaders[Resources.TYPE_VERTEX], GL_VERTEX_SHADER);
			newFragment = buildShader(shaders[Resources.TYPE_FRAGMENT], GL_FRAGMENT_SHADER);
			if(shaders[Resources.TYPE_GEOMETRY] != null)
				newGeometry = buildShader(shaders[Resources.TYPE_GEOMETRY], GL_GEOMETRY_SHADER);
			if(shaders[Resources.TYPE_COMPUTE] != null)
				newCompute = buildShader(shaders[Resources.TYPE_COMPUTE], GL_COMPUTE_SHADER);
			
			if(newFragment == -1 || newVertex == -1 || newGeometry == -1 || newCompute == -1)
				throw new GenerationException("Could not compile a shader");
			
			newStandardProgram = glCreateProgram();
			glAttachShader(newStandardProgram, newFragment);
			glAttachShader(newStandardProgram, newVertex);
			if(newGeometry != 0) glAttachShader(newStandardProgram, newGeometry);
			glLinkProgram(newStandardProgram);
			
			if(glGetProgrami(newStandardProgram, GL_LINK_STATUS) == GL_FALSE)
				throw new GenerationException("Linking error: " + glGetProgramInfoLog(newStandardProgram).strip());
			glValidateProgram(newStandardProgram);
			if(glGetProgrami(newStandardProgram, GL_VALIDATE_STATUS) == GL_FALSE)
				throw new GenerationException("Unexpected error: " + glGetProgramInfoLog(newStandardProgram).strip());
		
			if(newCompute != 0) {
				newComputeProgram = glCreateProgram();
				glAttachShader(newComputeProgram, newCompute);
				glLinkProgram(newComputeProgram);
				if(glGetProgrami(newComputeProgram, GL_LINK_STATUS) == GL_FALSE)
					throw new GenerationException("Linking error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
				glValidateProgram(newComputeProgram);
				if(glGetProgrami(newComputeProgram, GL_VALIDATE_STATUS) == GL_FALSE)
					throw new GenerationException("Unexpected error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
			}
		} catch (GenerationException e) {
			Main.logger.warn(e.getMessage());
			deleteShaders(newVertex, newGeometry, newFragment, newCompute);
			deletePrograms(newStandardProgram, newComputeProgram);
			return false;
		}
		
		Main.logger.info("Compiled successfully");
		
		deleteShaders(vertexShader, geometryShader, fragmentShader, computeShader);
		deletePrograms(standardShaderProgram, computeShaderProgram);
		glUseProgram(newStandardProgram);
		standardShaderProgram = newStandardProgram;
		computeShaderProgram = newComputeProgram;
		vertexShader = newVertex;
		geometryShader = newGeometry;
		fragmentShader = newFragment;
		computeShader = newCompute;
		
		if(Main.options.noTextureCache)
			Texture.unloadTextures();
		
		String pseudoTotalSource = Arrays.toString(shaders);
		Uniforms.scan(newStandardProgram, pseudoTotalSource);
		Uniforms.apply();
		
		return true;
	}
	
	private static int buildShader(String source, int glType) {
		int id = glCreateShader(glType);
		glShaderSource(id, source);
		glCompileShader(id);
		if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
			Main.logger.warn("Compilation error: ");
			for(String line : glGetShaderInfoLog(id).strip().split("\n"))
				Main.logger.warn("  " + line);
			glDeleteShader(id);
			return -1;
		}
		return id;
	}
	
	private static void deleteShaders(int... shaders) {
		for(int s : shaders) {
			if(s > 0)
				glDeleteShader(s);
		}
	}
	
	private static void deletePrograms(int... programs) {
		for(int s : programs) {
			if(s > 0)
				glDeleteProgram(s);
		}
	}

	public static void printSystemInformation() {
		System.out.println("GLFW:version               " + glfwGetVersionString());
		System.out.println("OPENGL:version             " + glGetString(GL_VERSION));
		System.out.println("OPENGL:glslversion         " + glGetString(GL_SHADING_LANGUAGE_VERSION));
		System.out.println("GLSL:computeMaxWorkGroup   (" + 
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2) + ")");
		System.out.println("GLSL:computeMaxWorkSize    (" + 
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2) + ")");
		System.out.println("GLSL:computeMaxInvocations " + glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS));
	}

}

package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

class GLWindow {

	private static final int SHADER_STORAGE_DATA_SIZE = 16384;
	
	private static long window;
	private static int shaderStorageBlock;
	private static int[] shaderStorageData = new int[SHADER_STORAGE_DATA_SIZE];
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

		int vbo = glGenBuffers();
		int ibo = glGenBuffers();
		float[] vertices = { -1, -1, 1, -1, 1, 1, -1, 1 };
		int[] indices = { 0, 1, 2, 2, 3, 0 };
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, NULL);
		
		shaderStorageBlock = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, shaderStorageBlock);
		glBufferData(GL_SHADER_STORAGE_BUFFER, shaderStorageData, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageBlock);
		
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
			glMemoryBarrier(GL_ALL_BARRIER_BITS);
//			glClientWaitSync(shaderStorageBlock, GL_BUFFER_ACCESS_FLAGS, 1000000);
//			glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, shaderStorageData);
//			System.out.println(shaderStorageData[0]);
		}
		
		glClear(GL_COLOR_BUFFER_BIT);
		
		if(standardShaderProgram != 0) {
			
			glUseProgram(standardShaderProgram);
			Uniforms.reapply();
			glBindVertexArray(bindableVAO);
			glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, NULL);
		}
		
		glfwSwapBuffers(window);
		glfwPollEvents();
	}

	public static boolean compileShaders(String[] shaders) {
		int newFragment = buildShader(shaders[Resources.TYPE_FRAGMENT], GL_FRAGMENT_SHADER);
		int newVertex   = buildShader(shaders[Resources.TYPE_VERTEX], GL_VERTEX_SHADER);
		int newGeometry = shaders[Resources.TYPE_GEOMETRY] == null ? 0 :
							buildShader(shaders[Resources.TYPE_GEOMETRY], GL_GEOMETRY_SHADER);
		int newCompute = shaders[Resources.TYPE_COMPUTE] == null ? 0 :
							buildShader(shaders[Resources.TYPE_COMPUTE], GL_COMPUTE_SHADER);
		
		if(newFragment == -1 || newVertex == -1 || newGeometry == -1 || newCompute == -1) {
			Main.logger.warn("Could not compile a shader");
			deleteShaders(newVertex, newGeometry, newFragment, newCompute);
			return false;
		}
		
		int newStandardProgram = glCreateProgram();
		glAttachShader(newStandardProgram, newFragment);
		glAttachShader(newStandardProgram, newVertex);
		if(newGeometry != 0)
			glAttachShader(newStandardProgram, newGeometry);
		glLinkProgram(newStandardProgram);
		if(glGetProgrami(newStandardProgram, GL_LINK_STATUS) == GL_FALSE) {
			Main.logger.warn("Linking error: " + glGetProgramInfoLog(newStandardProgram).strip());
			deleteShaders(newVertex, newGeometry, newFragment, newCompute);
			deletePrograms(newStandardProgram);
			return false;
		}
		glValidateProgram(newStandardProgram);
		if(glGetProgrami(newStandardProgram, GL_VALIDATE_STATUS) == GL_FALSE) {
			Main.logger.warn("Unexpected error: " + glGetProgramInfoLog(newStandardProgram).strip());
			deleteShaders(newVertex, newGeometry, newFragment, newCompute);
			deletePrograms(newStandardProgram);
			return false;
		}
		
		int newComputeProgram = 0;
		
		if(newCompute != 0) {
			newComputeProgram = glCreateProgram();
			glAttachShader(newComputeProgram, newCompute);
			glLinkProgram(newComputeProgram);
			if(glGetProgrami(newComputeProgram, GL_LINK_STATUS) == GL_FALSE) {
				Main.logger.warn("Linking error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
				deleteShaders(newVertex, newGeometry, newFragment, newCompute);
				deletePrograms(newStandardProgram, newComputeProgram);
				return false;
			}
			glValidateProgram(newComputeProgram);
			if(glGetProgrami(newComputeProgram, GL_VALIDATE_STATUS) == GL_FALSE) {
				Main.logger.warn("Unexpected error (compute): " + glGetProgramInfoLog(newComputeProgram).strip());
				deleteShaders(newVertex, newGeometry, newFragment, newCompute);
				deletePrograms(newStandardProgram, newComputeProgram);
				return false;
			}
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

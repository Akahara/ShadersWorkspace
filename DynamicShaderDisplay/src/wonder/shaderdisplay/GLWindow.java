package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.Arrays;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;

class GLWindow {

	private static long window;
	private static int shaderProgram;
	private static int vertexShader, geometryShader, fragmentShader;
	private static int bindableVAO;
	static int winWidth, winHeight;

	static void createWindow(int width, int height) {
		winWidth = width;
		winHeight = height;

		GLFWErrorCallback.createPrint(System.err).set();

		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW !");

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
		
		glfwSetErrorCallback((error, desc) -> {
			Main.logger.err("GLFW error [" + Integer.toHexString(error) + "]: " + GLFWErrorCallback.getDescription(desc));
		});

		window = glfwCreateWindow(width, height, "Shader Display", NULL, NULL);

		if (window == NULL)
			throw new IllegalStateException("Unable to create a window !");

		glfwMakeContextCurrent(window);
		glfwShowWindow(window);
		glfwFocusWindow(window);

		GL.createCapabilities();
		
		GLUtil.setupDebugMessageCallback(System.err);
		
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
		GL.destroy();
		glfwTerminate();
		window = 0;
	}
	
	public static void setWindowTitle(String title) {
		glfwSetWindowTitle(window, title);
	}
	
	public static void render() {
		glClear(GL_COLOR_BUFFER_BIT);
		Uniforms.reapply();
		glBindVertexArray(bindableVAO);
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, NULL);
		glfwSwapBuffers(window);
		glfwPollEvents();
	}

	public static boolean compileShaders(String[] shaders) {
		int newFragment = buildShader(shaders[Resources.TYPE_FRAGMENT], GL_FRAGMENT_SHADER);
		int newVertex   = buildShader(shaders[Resources.TYPE_VERTEX], GL_VERTEX_SHADER);
		int newGeometry = shaders[Resources.TYPE_GEOMETRY] == null ? 0 :
							buildShader(shaders[Resources.TYPE_GEOMETRY], GL_GEOMETRY_SHADER);
		
		if(newFragment == -1 || newVertex == -1 || newGeometry == -1) {
			deleteShaders(newVertex, newGeometry, newFragment);
			return false;
		}
		
		int newProgram = glCreateProgram();
		glAttachShader(newProgram, newFragment);
		glAttachShader(newProgram, newVertex);
		if(newGeometry != 0)
			glAttachShader(newProgram, newGeometry);
		glLinkProgram(newProgram);
		glValidateProgram(newProgram);
		if(glGetProgrami(newProgram, GL_VALIDATE_STATUS) == GL_FALSE) {
			Main.logger.warn("Linking error: " + glGetProgramInfoLog(newProgram).strip());
			deleteShaders(newVertex, newGeometry, newFragment);
			return false;
		}
		
		Main.logger.info("Compiled successfully");
		
		deleteShaders(vertexShader, geometryShader, fragmentShader);
		glDeleteProgram(shaderProgram);
		glUseProgram(newProgram);
		shaderProgram = newProgram;
		vertexShader = newVertex;
		geometryShader = newGeometry;
		fragmentShader = newFragment;
		
		if(Main.options.noTextureCache)
			Texture.unloadTextures();
		
		String pseudoTotalSource = Arrays.toString(shaders);
		Uniforms.scan(newProgram, pseudoTotalSource);
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

}

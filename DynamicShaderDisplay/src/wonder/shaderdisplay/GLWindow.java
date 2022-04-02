package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

public class GLWindow {
	
	private static long window;
//	private static int bindableVAO;
	public static int winWidth, winHeight;
	
	private static final List<Callback> closeableCallbacks = new ArrayList<>();

	static void createWindow(int width, int height) {
		winWidth = width;
		winHeight = height;

		GLFWErrorCallback.createPrint(System.err).set();
		
		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW !");

		setGLVersionHint();
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
		
		window = glfwCreateWindow(width, height, "Shader Display", NULL, NULL);

		if (window == NULL)
			throw new IllegalStateException("Unable to create a window !");

		glfwMakeContextCurrent(window);
		glfwShowWindow(window);
		glfwFocusWindow(window);
		glfwSwapInterval(Main.options.vsync ? 1 : 0);

		GL.createCapabilities();
		
		Callback errorCallback = GLUtil.setupDebugMessageCallback(System.err);
		if(errorCallback != null)
			closeableCallbacks.add(errorCallback);
		
		glViewport(0, 0, width, height);
		glClearColor(0, 0, 0, 1);
		glPointSize(3);
		
		glBindVertexArray(glGenVertexArrays());
		
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
	
	private static void setGLVersionHint() {
		if(Main.options.forcedGLVersion == null) {
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
			return;
		} else {
			Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(Main.options.forcedGLVersion);
			if(!m.find())
				throw new IllegalArgumentException("Invalid gl version, got '" + Main.options.forcedGLVersion + "', expected something like 4.3");
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, Integer.parseInt(m.group(1)));
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, Integer.parseInt(m.group(2)));
		}
	}
	
	public static long getWindow() {
		return window;
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

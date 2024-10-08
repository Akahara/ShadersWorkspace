package wonder.shaderdisplay.display;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengl.GL30.glGetIntegeri;
import static org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT;
import static org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS;
import static org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE;
import static org.lwjgl.opengl.GL43C.glDebugMessageCallback;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import wonder.shaderdisplay.Main;

@SuppressWarnings("resource")
public class GLWindow {
	
	private static long window;
	public static int winWidth, winHeight;
	
	private static final List<Callback> closeableCallbacks = new ArrayList<>();
	private static final List<BiConsumer<Integer, Integer>> resizeCallbacks = new ArrayList<>();
	private static final List<BiConsumer<Integer, Integer>> keyCallback = new ArrayList<>();
	private static final List<BiConsumer<Double, Double>> mouseCallback = new ArrayList<>();
	private static final List<BiConsumer<Integer, Integer>> buttonCallback = new ArrayList<>();

	public static void createWindow(int width, int height, boolean visible, String forcedGlVersion, boolean verboseGLFW) {
		winWidth = width;
		winHeight = height;

		GLFWErrorCallback.createPrint(System.err).set();

		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW !");

		setGLVersionHint(forcedGlVersion);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		
		window = glfwCreateWindow(width, height, "Shader Display", NULL, NULL);

		if (window == NULL)
			throw new IllegalStateException("Unable to create a window !");

		glfwMakeContextCurrent(window);
		if(visible) {
			glfwShowWindow(window);
			glfwFocusWindow(window);
		}

		GLCapabilities caps = GL.createCapabilities();

		if(verboseGLFW) {
			Callback errorCallback = GLUtil.setupDebugMessageCallback(System.err);
			if(errorCallback != null)
				closeableCallbacks.add(errorCallback);

			if (caps.OpenGL43) {
				GLDebugMessageCallback oldProc = GLDebugMessageCallback.create(glGetPointer(GL_DEBUG_CALLBACK_FUNCTION));
				GLDebugMessageCallback proc = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
					boolean submit = true;
					submit &= severity != GL_DEBUG_SEVERITY_NOTIFICATION; // PBO streaming using video memory
					submit &= id != 0x20052; // "Pixel transfer is synchronized with 3D rendering"
					if (submit)
						oldProc.invoke(source, type, id, severity, length, message, userParam);
				});
				glDebugMessageCallback(proc, NULL);
			}
		}

		glViewport(0, 0, width, height);
		glClearColor(0, 0, 0, 0);
		glPointSize(3);
		
		glfwSetWindowSizeCallback(window, (win, w, h) -> {
			if (w == 0 || h == 0)
				return; // Skip event if window was minimized
			glViewport(0, 0, w, h);
			winWidth = w;
			winHeight = h;
			for(BiConsumer<Integer, Integer> callback : resizeCallbacks)
				callback.accept(w, h);
		});

		glfwSetKeyCallback(window, (win, key, scanCode, action, mods) -> {
			if(action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
				glfwSetWindowShouldClose(window, true);
			}

			for(BiConsumer<Integer, Integer> callback : keyCallback)
				callback.accept(key, action);
		});

		glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
			for(BiConsumer<Double, Double> callback : mouseCallback)
				callback.accept(xpos, ypos);
		});

		glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
			for(BiConsumer<Integer, Integer> callback : buttonCallback)
				callback.accept(button, action);
		});
	}
	
	private static void setGLVersionHint(String forcedGLVersion) {
		if(forcedGLVersion == null) {
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
			return;
		}

		Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(forcedGLVersion);
		if(!m.find())
			throw new IllegalArgumentException("Invalid gl version, got '" + forcedGLVersion + "', expected something like 4.3");
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, Integer.parseInt(m.group(1)));
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, Integer.parseInt(m.group(2)));
	}
	
	public static void setVSync(boolean enableVSync) {
		glfwSwapInterval(enableVSync ? 1 : 0);
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
	
	public static void resizeWindow(int width, int height) {
		winWidth = width <= 0 ? winWidth : width;
		winHeight = height <= 0 ? winHeight : height;
		glfwSetWindowSize(window, winWidth, winHeight);
	}
	
	public static void setTaskBarIcon(String resourcePath) {
		GLFWImage.Buffer iconImageBuffer = GLFWImage.malloc(1);
		GLFWImage icon = GLFWImage.malloc();
		BufferedImage iconImage;
		try {
			iconImage = ImageIO.read(GLWindow.class.getResourceAsStream(resourcePath));
		} catch (IOException | IllegalArgumentException | NullPointerException e) {
			Main.logger.err(e, "Could not load taskbar icon");
			return;
		}
		int w = iconImage.getWidth(), h = iconImage.getHeight();
		int[] rgbArray = Texture.loadTextureData(iconImage, true);
		ByteBuffer iconBytes = ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.LITTLE_ENDIAN);
		iconBytes.asIntBuffer().put(rgbArray);
		icon.set(w, h, iconBytes);
		iconImageBuffer.put(0, icon);
		glfwSetWindowIcon(window, iconImageBuffer);
	}
	
	public static void printSystemInformation() {
		System.out.println("GLFW:version               " + glfwGetVersionString());
		System.out.println("OPENGL:version             " + glGetString(GL_VERSION));
		System.out.println("OPENGL:glslversion         " + glGetString(GL_SHADING_LANGUAGE_VERSION));
		System.out.println("GLSL:texturesBindingPoints " + glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
		System.out.println("GLSL:computeMaxWorkGroup   (" +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2) + ")");
		System.out.println("GLSL:computeMaxWorkSize    (" +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1) + ", " +
				glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2) + ")");
		System.out.println("GLSL:computeMaxInvocations " + glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS));

		GLCapabilities capabilities = GL.getCapabilities();
		for (Field field : GLCapabilities.class.getFields()) {
			if(field.getType() != boolean.class)
				continue;
			try {
				System.out.println("GLCapability:" + field.getName() + " " + field.get(capabilities));
			} catch (IllegalAccessException x) {}
		}
	}

	public static int getWinWidth() {
		return winWidth;
	}

	public static int getWinHeight() {
		return winHeight;
	}

	public static ListenerHandle addResizeListener(BiConsumer<Integer, Integer> callback) {
		resizeCallbacks.add(callback);
		return new ListenerHandle(() -> resizeCallbacks.remove(callback));
	}

	public static ListenerHandle addKeyCallback(BiConsumer<Integer, Integer> callback) {
		keyCallback.add(callback);
		return new ListenerHandle(() -> keyCallback.remove(callback));
	}

	public static ListenerHandle addMouseCallback(BiConsumer<Double, Double> callback) {
		mouseCallback.add(callback);
		return new ListenerHandle(() -> mouseCallback.remove(callback));
	}

	public static ListenerHandle addButtonCallback(BiConsumer<Integer, Integer> callback) {
		buttonCallback.add(callback);
		return new ListenerHandle(() -> buttonCallback.remove(callback));
	}

	public static class ListenerHandle {

		private final Runnable removeHandle;

		ListenerHandle(Runnable removeHandle) {
			this.removeHandle = Objects.requireNonNull(removeHandle);
		}

		public void remove() {
			removeHandle.run();
		}

	}

}

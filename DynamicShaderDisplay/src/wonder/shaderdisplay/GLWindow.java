package wonder.shaderdisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import fr.wonder.commons.systems.reflection.ReflectUtils;

class GLWindow {

	private static long window;
	private static int shaderProgram;
	private static int vertex;
	private static int fragment;
	static int winWidth, winHeight;

	static void createWindow(int width, int height, String shaderSource, String vertexShader) {
		winWidth = width;
		winHeight = height;

		GLFWErrorCallback.createPrint(System.err).set();

		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW !");

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

		glfwSetErrorCallback((error, desc) -> {
			System.err.println(
					"GLFW error [" + Integer.toHexString(error) + "]: " + GLFWErrorCallback.getDescription(desc));
		});

		window = glfwCreateWindow(width, height, "Shader Display", NULL, NULL);

		if (window == NULL)
			throw new IllegalStateException("Unable to create a window !");

		glfwMakeContextCurrent(window);
		glfwShowWindow(window);
		glfwFocusWindow(window);

		GL.createCapabilities();
		glViewport(0, 0, width, height);
		glClearColor(0, 0, 0, 1);

		pollGLError();

		glBindVertexArray(glGenVertexArrays());

		shaderProgram = glCreateProgram();

		vertex = glCreateShader(GL_VERTEX_SHADER);
		fragment = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(vertex, vertexShader);
		glShaderSource(fragment, shaderSource);
		glCompileShader(vertex);
		glCompileShader(fragment);
		glAttachShader(shaderProgram, vertex);
		glAttachShader(shaderProgram, fragment);
		glLinkProgram(shaderProgram);
		glValidateProgram(shaderProgram);
		glUseProgram(shaderProgram);
		Uniforms.scan(shaderProgram, shaderSource);
		Uniforms.apply();

		pollGLError();

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

		pollGLError();

		glfwSetWindowSizeCallback(window, (win, w, h) -> {
			glViewport(0, 0, w, h);
			winWidth = w;
			winHeight = h;
		});
		
		glfwSetKeyCallback(window, (win, key, scanCode, action, mods) -> {
			if(action == GLFW_PRESS && key == GLFW_KEY_ESCAPE)
				glfwSetWindowShouldClose(window, true);
		});
	}

	public static boolean shouldDispose() {
		return glfwWindowShouldClose(window);
	}
	
	public static void dispose() {
		glfwTerminate();
		window = 0;
	}
	
	public static void setWindowTitle(String title) {
		glfwSetWindowTitle(window, title);
	}
	
	public static void render() {
		pollGLError();
		glClear(GL_COLOR_BUFFER_BIT);
		Uniforms.reapply();
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, NULL);
		glfwSwapBuffers(window);
		glfwPollEvents();
		pollGLError();
	}

	public static void compileNewShader(String source) {
		pollGLError();
		int newFragment = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(newFragment, source);
		glCompileShader(newFragment);
		if (glGetShaderi(newFragment, GL_COMPILE_STATUS) != GL_FALSE) {
			int newShader = glCreateProgram();
			glAttachShader(newShader, newFragment);
			glAttachShader(newShader, vertex);
			glLinkProgram(newShader);
			glValidateProgram(newShader);
			if (glGetProgrami(newShader, GL_VALIDATE_STATUS) != GL_FALSE) {
				System.out.println("Compiled successfully.");
				glUseProgram(newShader);
				Uniforms.scan(newShader, source);
				Uniforms.apply();
				// delete previous shaders
				glDeleteShader(fragment);
				glDeleteProgram(shaderProgram);
				fragment = newFragment;
				shaderProgram = newShader;
			} else {
				System.err.println("Unable to validate program:" + glGetProgramInfoLog(newShader));
			}
		} else {
			System.err.println("Unable to validate shader:" + glGetShaderInfoLog(newFragment));
		}
		System.out.println("Compiled new shader");
		pollGLError();
	}

	private static void pollGLError() {
		int err = glGetError();
		if (err != 0)
			System.out.println("GL error : " + err + " " + ReflectUtils.getCallerTrace());
	}
	
	public static void makeScreenshot(File outFile) {
		int[] pixels = new int[winWidth * winHeight];
		ByteBuffer fb = ByteBuffer.allocateDirect(winWidth * winHeight * 3);
		glReadBuffer(GL_FRONT);
		glReadPixels(0, 0, winWidth, winHeight, GL_RGB, GL_UNSIGNED_BYTE, fb);
		pollGLError();
		BufferedImage img = new BufferedImage(winWidth, winHeight, BufferedImage.TYPE_INT_RGB);
//        for(int i = 0; i < pixels.length; i++) {
//            pixels[i] =
//                ((fb.get(i*3  ) << 16)) +
//                ((fb.get(i*3+1) << 8 )) +
//                ((fb.get(i*3+2) << 0 ));
//        }
//        img.setRGB(0, 0, winWidth, winHeight, pixels, 0, winWidth);
//        AffineTransform at = AffineTransform.getScaleInstance(1, -1);
//        at.translate(0, -img.getHeight(null));
//        img = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR).filter(img, null);
		for(int r = 0; r < winHeight; r++) {
			for(int c = 0; c < winWidth; c++) {
				int l = (c+winWidth*(winHeight-1-r))*3;
				pixels[c+winWidth*r] =
						((fb.get(l  ) << 16)) +
						((fb.get(l+1) << 8 )) +
						((fb.get(l+2) << 0 ));
			}
		}
		img.setRGB(0, 0, winWidth, winHeight, pixels, 0, winWidth);
		try {
			outFile.createNewFile();
			ImageIO.write(img, "png", outFile);
			System.out.println("Screenshot saved to file " + outFile.getAbsolutePath());
		} catch (Exception e) {
			System.err.println("Unable to save screenshot: " + e);
		}
	}

}

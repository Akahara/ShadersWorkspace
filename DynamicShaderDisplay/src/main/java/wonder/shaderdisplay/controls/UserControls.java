package wonder.shaderdisplay.controls;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Math;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.PatternSyntaxException;

import javax.imageio.ImageIO;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.argparser.ArgParser;
import fr.wonder.argparser.InvalidDeclarationError;
import fr.wonder.argparser.annotations.Argument;
import fr.wonder.argparser.annotations.EntryPoint;
import fr.wonder.argparser.annotations.Option;
import fr.wonder.argparser.annotations.OptionClass;
import imgui.ImGui;
import static org.lwjgl.glfw.GLFW.*;

import imgui.type.ImInt;
import org.joml.*;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Main.DisplayOptions;
import wonder.shaderdisplay.serial.Resources;
import wonder.shaderdisplay.serial.Resources.Snippet;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneRenderTarget;
import wonder.shaderdisplay.serial.UserConfig;

public class UserControls {

	private static final int[] screenSizeBuffer = new int[2];
	private static final boolean[] pressedKeys = new boolean[512];
	private static int pressedMods;

	public enum KeyMod {
		SHIFT(GLFW_MOD_SHIFT);

		final int glfwCode;

		KeyMod(int glfwCode) {
			this.glfwCode = glfwCode;
		}
	}

	private boolean takeScreenshot = false;
	private boolean drawBackground = true;
	private boolean resetRenderTargets = false;
	private boolean viewJustMoved = false;
	private int cursorLockedFrames = -1;
	private double freecamSpeed;
	private final Vector3f viewPosition = new Vector3f();
	private final Vector2f prevMousePos = new Vector2f();
	private final Vector2f mousePos = new Vector2f();
	private final Quaternionf viewRotation = new Quaternionf();
	private ImInt selectedRenderTarget;
	private final float[] depthRenderTargetBlitZRangeStart = { 0 }, depthRenderTargetBlitZRangeStop = { 1 };

	public UserControls() {
		UserConfig.Freecam freecam = UserConfig.config.freecam;
		this.freecamSpeed = freecam.speed;
		this.viewPosition.set(freecam.position[0], freecam.position[1], freecam.position[2]);
		this.viewRotation.set(freecam.rotation[0], freecam.rotation[1], freecam.rotation[2], freecam.rotation[3]);

		screenSizeBuffer[0] = GLWindow.winWidth;
		screenSizeBuffer[1] = GLWindow.winHeight;

		GLWindow.addResizeListener((w,h) -> {
			screenSizeBuffer[0] = w;
			screenSizeBuffer[1] = h;
		});

		GLWindow.addKeyCallback((key, action, mods) -> {
			if (key < pressedKeys.length)
				pressedKeys[key] = action != GLFW_RELEASE;
			pressedMods = mods;
		});

		GLWindow.addMouseCallback((x, y) -> {
			mousePos.x = x.floatValue();
			mousePos.y = y.floatValue();
		});

		GLWindow.addScrollCallback((deltaX, deltaY) -> {
			freecamSpeed *= Math.pow(1.2f, deltaY);
		});

		GLWindow.addButtonCallback((button, action) -> {
			boolean wantCapture = !ImGui.getIO().getWantCaptureMouse() && action == GLFW_PRESS;
			cursorLockedFrames = wantCapture ? 0 : -1;
			glfwSetInputMode(GLWindow.getWindow(), GLFW_CURSOR, wantCapture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
		});
		
		new Thread(() -> {
			try (Scanner sc = new Scanner(System.in)) {
				while(true)
					interpretCommand(sc.nextLine().trim());
			} catch (IllegalStateException | NoSuchElementException x) {}
        }, "stdin-interpreter").start();
	}

	public void step(float delta) {
		if (cursorLockedFrames < 0)
			return;
		cursorLockedFrames++;
		if (cursorLockedFrames == 1) {
			// don't apply movement because prevMousePos may not be accurate (if the window wasn't focused on the previous frame)
			prevMousePos.set(mousePos);
			return;
		}

		Vector3f movement = new Vector3f();
		if (pressedKeys[GLFW_KEY_LEFT] || pressedKeys[GLFW_KEY_A])
			movement.x--;
		if (pressedKeys[GLFW_KEY_RIGHT] || pressedKeys[GLFW_KEY_D])
			movement.x++;
		if (pressedKeys[GLFW_KEY_SPACE] || pressedKeys[GLFW_KEY_Q])
			movement.y++;
		if (pressedKeys[GLFW_KEY_LEFT_SHIFT] || pressedKeys[GLFW_KEY_E])
			movement.y--;
		if (pressedKeys[GLFW_KEY_UP] || pressedKeys[GLFW_KEY_W])
			movement.z++;
		if (pressedKeys[GLFW_KEY_DOWN] || pressedKeys[GLFW_KEY_S])
			movement.z--;

		if (movement.lengthSquared() != 0) {
			final Vector3f up = new Vector3f(0, 1, 0);
			Vector3f forward = viewRotation.transform(new Vector3f(1, 0, 0));
			Vector3f left = up.cross(forward, new Vector3f()).normalize();
			movement = new Matrix3f(forward, up, left).transform(movement);
			viewPosition.add(movement.mul(delta * (float)freecamSpeed));
			viewJustMoved = true;
		}

		if (!mousePos.equals(prevMousePos)) {
			final float speed = .02f;
			viewRotation.rotateX(-(mousePos.y - prevMousePos.y) * speed);
			viewRotation.rotateLocalY(-(mousePos.x - prevMousePos.x) * speed);
			viewJustMoved = true;
			prevMousePos.set(mousePos);
		}

		UserConfig.Freecam freecam = UserConfig.config.freecam;
		freecam.speed = (float)freecamSpeed;
		freecam.position[0] = viewPosition.x;
		freecam.position[1] = viewPosition.y;
		freecam.position[2] = viewPosition.z;
		freecam.rotation[0] = viewRotation.x;
		freecam.rotation[1] = viewRotation.y;
		freecam.rotation[2] = viewRotation.z;
		freecam.rotation[3] = viewRotation.w;
	}

	public void renderControls(Scene scene) {
		if(ImGuiSystem.tooltipButton("Take screenshot", "Beware of transparency!"))
			takeScreenshot = true;

		if(ImGui.dragInt2("Window size", screenSizeBuffer, 10, 1, 15000))
			GLWindow.resizeWindow(screenSizeBuffer[0], screenSizeBuffer[1]);

		if(ImGui.checkbox("Draw background", drawBackground))
			drawBackground = !drawBackground;
		ImGuiSystem.showTooltipOnHover("Draw a template background, use to make sure your alpha channel is correct");

		if (scene.renderTargets.size() > 1) {
			ImGui.sameLine();
			if (ImGui.button("Reset render targets"))
				resetRenderTargets = true;
			if (selectedRenderTarget == null)
				selectedRenderTarget = new ImInt(0);
			ImGui.combo("Render Target", selectedRenderTarget, scene.renderTargetNames);
		}

		if (selectedRenderTarget != null && scene.renderTargets.get(selectedRenderTarget.get()).type == SceneRenderTarget.RenderTargetType.DEPTH) {
			ImGui.dragFloatRange2("Depth range", depthRenderTargetBlitZRangeStart, depthRenderTargetBlitZRangeStop, .001f, 0, 1);
			depthRenderTargetBlitZRangeStart[0] = Math.min(depthRenderTargetBlitZRangeStart[0], depthRenderTargetBlitZRangeStop[0]-.0001f);
		}
		ImGui.newLine();
	}

	public int getPrimaryRenderTargetIndex() {
		return selectedRenderTarget == null ? -1 : selectedRenderTarget.get();
	}

	public Vector3f getViewPosition() {
		return viewPosition;
	}

	public Quaternionf getViewRotation() {
		return viewRotation;
	}

	public static boolean isModPressed(KeyMod mod) {
		return (pressedMods & mod.glfwCode) != 0;
	}

	public int getClickState() {
		return cursorLockedFrames+1;
	}

	public Vector2f getMousePosition() {
		return mousePos;
	}

	public boolean justMoved() {
		return viewJustMoved;
	}

	public boolean pollJustMoved() {
		boolean justMoved = viewJustMoved;
		viewJustMoved = false;
		return justMoved;
	}

	public void takeScreenshot(Scene scene, DisplayOptions options) {
		SimpleDateFormat df = new SimpleDateFormat("MMdd_HHmmss");
		String fileName = "screenshot_" + df.format(new Date()) + ".png";
		File file = new File(fileName);
		String format = FilesUtils.getFileExtension(file);
		
		if(format == null) {
			format = "PNG";
			file = new File(file.getParentFile(), file.getName() + ".png");
		} else {
			format = format.toUpperCase();
		}

		String renderTargetName = getPrimaryRenderTargetIndex() < 0 ? SceneRenderTarget.DEFAULT_RT.name : scene.renderTargets.get(getPrimaryRenderTargetIndex()).name;
		Texture texture = scene.swapChain.getAttachment(renderTargetName);
		int w = texture.getWidth(), h = texture.getHeight();
		int[] buffer = scene.swapChain.readColorAttachment(renderTargetName, null, options.background);
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, w, h, buffer, w*(h-1), -w);
		
		try {
			ImageIO.write(image, format, file);
			Main.logger.info("Saved screenshot at " + file.getCanonicalPath());
		} catch (IOException e) {
			Main.logger.merr(e, "Could not save screenshot");
		}
	}
	
	private void interpretCommand(String command) {
		try {
			new ArgParser("", UserCommands.class).run(command);
		} catch (InvalidDeclarationError e) {
			Main.logger.merr(e);
		}
	}

	public boolean getDrawBackground() {
		return drawBackground;
	}

	public void setJustMoved() {
		viewJustMoved = true;
	}

	public boolean pollResetRenderTargetsQueried() {
		boolean queried = resetRenderTargets;
		resetRenderTargets = false;
		return queried;
	}

	public float getDepthPreviewZRangeStart() {
		return depthRenderTargetBlitZRangeStart[0];
	}

	public float getDepthPreviewZRangeStop() {
		return depthRenderTargetBlitZRangeStop[0];
	}

	public static class UserCommands {
		
		@OptionClass
		public static class SnippetsOptions {
			
			@Option(name = "--code", shorthand = "-c", desc = "Prints the snippets code instead of their names")
			public boolean printCode = false;
			@Option(name = "--out", shorthand = "-o", valueName = "file", desc = "Writes the snippets codes to a file")
			public File outputFile;
			
		}
		
		@EntryPoint(path = "snippets", help = "Lists available snippets")
		@Argument(name = "filter", defaultValue = ".*", desc = "Filters snippets, use with a name or a pattern (regex)")
		public static void snippetsCommand(SnippetsOptions options, String filter) {
			List<Snippet> snippets;
			try {
				snippets = Resources.filterSnippets(filter);
			} catch (PatternSyntaxException e) {
				Main.logger.err(e, "Invalid filter");
				return;
			}
			
			if(snippets.isEmpty()) {
				System.err.println("No matching snippets");
				return;
			}
			
			for(Snippet s : snippets) {
				if(options.printCode)
					System.out.println(s.code);
				else
					System.out.println("- " + s.name);
			}
			if(options.outputFile != null) {
				try (FileWriter writer = new FileWriter(options.outputFile, true)) {
					for(Snippet s : snippets)
						writer.write(s.code);
				} catch (IOException e) {
					Main.logger.err(e, "Could not write snippets");
				}
			}
			System.out.println("Found " + snippets.size() + " snippets, use with -c to print their source codes");
		}
	}

	public boolean pollShouldTakeScreenshot() {
		boolean val = takeScreenshot;
		takeScreenshot = false;
		return val;
	}
}
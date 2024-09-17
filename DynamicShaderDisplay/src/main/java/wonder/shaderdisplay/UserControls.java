package wonder.shaderdisplay;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

import javax.imageio.ImageIO;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.systems.argparser.ArgParser;
import fr.wonder.commons.systems.argparser.InvalidDeclarationError;
import fr.wonder.commons.systems.argparser.annotations.Argument;
import fr.wonder.commons.systems.argparser.annotations.EntryPoint;
import fr.wonder.commons.systems.argparser.annotations.Option;
import fr.wonder.commons.systems.argparser.annotations.OptionClass;
import imgui.ImGui;
import static org.lwjgl.glfw.GLFW.*;

import org.joml.*;
import wonder.shaderdisplay.Main.DisplayOptions;
import wonder.shaderdisplay.Resources.Snippet;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.display.TexturesSwapChain;

public class UserControls {

	private final int[] screenSizeBuffer = new int[2];
	private final boolean[] pressedKeys = new boolean[512];

	private boolean takeScreenshot = false;
	private boolean drawBackground = true;
	private boolean viewJustMoved = false;
	private boolean cursorLocked = false;
	private final Vector3f viewPosition = new Vector3f();
	private final Vector2f prevMousePos = new Vector2f();
	private final Vector2f mousePos = new Vector2f();
	private final Quaternionf viewRotation = new Quaternionf();

	public UserControls() {
		screenSizeBuffer[0] = GLWindow.winWidth;
		screenSizeBuffer[1] = GLWindow.winHeight;

		GLWindow.addResizeListener((w,h) -> {
			screenSizeBuffer[0] = w;
			screenSizeBuffer[1] = h;
		});

		GLWindow.addKeyCallback((key, action) -> {
			if (key < pressedKeys.length)
				pressedKeys[key] = action != GLFW_RELEASE;
		});

		GLWindow.addMouseCallback((x, y) -> {
			mousePos.x = x.floatValue();
			mousePos.y = y.floatValue();
		});

		GLWindow.addButtonCallback((button, action) -> {
			cursorLocked = !ImGui.getIO().getWantCaptureMouse() && action == GLFW_PRESS;
			glfwSetInputMode(GLWindow.getWindow(), GLFW_CURSOR, cursorLocked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
		});
		
		new Thread(() -> {
			try (Scanner sc = new Scanner(System.in)) {
				while(true)
					interpretCommand(sc.nextLine().trim());
			} catch (IllegalStateException x) {}
		}, "stdin-interpreter").start();
	}

	public void step(float delta) {
		viewJustMoved = false;

		if (cursorLocked) {
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
				final float speed = 1.f;
				final Vector3f up = new Vector3f(0, 1, 0);
				Vector3f forward = viewRotation.transform(new Vector3f(1, 0, 0));
				Vector3f left = up.cross(forward, new Vector3f()).normalize();
				movement = new Matrix3f(forward, up, left).transform(movement);
				viewPosition.add(movement.mul(delta * speed));
				viewJustMoved = true;
			}

			if (!mousePos.equals(prevMousePos)) {
				final float speed = .02f;
				viewRotation.rotateX(-(mousePos.y - prevMousePos.y) * speed);
				viewRotation.rotateLocalY(-(mousePos.x - prevMousePos.x) * speed);
				viewJustMoved = true;
			}
		}

		prevMousePos.set(mousePos);
	}

	public void renderControls() {
		if(tooltipButton("Take screenshot", "Beware of transparency!"))
			takeScreenshot = true;

		if(ImGui.dragInt2("Window size", screenSizeBuffer, 10, 1, 15000))
			GLWindow.resizeWindow(screenSizeBuffer[0], screenSizeBuffer[1]);

		if(ImGui.checkbox("Draw background", drawBackground))
			drawBackground = !drawBackground;
		showTooltipOnHover("Draw a template background, use to make sure your alpha channel is correct");
	}

	public Vector3f getViewPosition() {
		return viewPosition;
	}

	public Quaternionf getViewRotation() {
		return viewRotation;
	}

	public boolean justMoved() {
		return viewJustMoved;
	}
	
	public static void copyToClipboardBtn(String name, Supplier<String> copiedText) {
		if(tooltipButton("C##" + name, "Copy to clipboard"))
			ImGui.setClipboardText(copiedText.get());
	}
	
	public static boolean tooltipButton(String name, String tooltip) {
		boolean pressed = ImGui.button(name);
		showTooltipOnHover(tooltip);
		return pressed;
	}
	
	public static void showTooltipOnHover(String tooltip) {
		if(ImGui.isItemHovered())
			ImGui.setTooltip(tooltip);
	}
	
	public void takeScreenshot(TexturesSwapChain renderTargetsSwapChain, String renderTargetName, DisplayOptions options) {
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

		Texture texture = renderTargetsSwapChain.getColorAttachment(renderTargetName);
		int w = texture.getWidth(), h = texture.getHeight();
		int[] buffer = renderTargetsSwapChain.readColorAttachment(renderTargetName, null, options.background);
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

	public boolean poolShouldTakeScreenshot() {
		boolean val = takeScreenshot;
		takeScreenshot = false;
		return val;
	}
}
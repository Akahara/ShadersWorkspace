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
import imgui.ImVec2;
import imgui.flag.ImGuiStyleVar;
import wonder.shaderdisplay.Main.DisplayOptions;
import wonder.shaderdisplay.Resources.Snippet;

public class UserControls {

	public static final String RENDER_TARGETS_WINDOW = "Render targets";
	
	private static final int[] screenSizeBuffer = new int[2];
	
	public static void init() {
		screenSizeBuffer[0] = GLWindow.winWidth;
		screenSizeBuffer[1] = GLWindow.winHeight;
		
		new Thread(() -> {
			try (Scanner sc = new Scanner(System.in)) {
				while(true)
					interpretCommand(sc.nextLine().trim());
			} catch (IllegalStateException x) {}
		}, "stdin-interpreter").start();
	}
	
	public static void renderControls(TexturesSwapChain renderTargetsSwapChain) {
		if(ImGui.begin("Controls")) {
			// screenshot
			if(tooltipButton("Take screenshot", "Beware of transparency!"))
				Main.activeUserControls.takeScreenshot = true;
			
			// window resize
			if(ImGui.dragInt2("Window size", screenSizeBuffer, 10, 1, 15000))
				GLWindow.resizeWindow(screenSizeBuffer[0], screenSizeBuffer[1]);
			
			if(ImGui.checkbox("Draw background", Main.activeUserControls.drawBackground))
				Main.activeUserControls.drawBackground = !Main.activeUserControls.drawBackground;
			showTooltipOnHover("Draw a template background, use to make sure your alpha channel is correct");
		}
		ImGui.end();

		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.f, 0.f);
		if(ImGui.begin(RENDER_TARGETS_WINDOW)) {
			ImVec2 winSize = ImGui.getWindowSize();
			float imageW = winSize.x*.5f;
			float imageH = (winSize.y-30)/(TexturesSwapChain.RENDER_TARGET_COUNT/2f);
			for(int i = 1; i < TexturesSwapChain.RENDER_TARGET_COUNT; i++) {
				ImGui.image(renderTargetsSwapChain.getOffscreenTexture(i).getId(), imageW, imageH, 0, 1, 1, 0);
				if(i%2==1)
					ImGui.sameLine();
			}
		}
		ImGui.popStyleVar();
		ImGui.end();
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
	
	public static void takeScreenshot(TexturesSwapChain renderTargetsSwapChain, DisplayOptions options) {
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
		
		int w = renderTargetsSwapChain.getDisplayWidth();
		int h = renderTargetsSwapChain.getDisplayHeight();
		int[] buffer = renderTargetsSwapChain.readColorAttachment(0, new int[w*h], options.background);
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, w, h, buffer, w*(h-1), -w);
		
		try {
			ImageIO.write(image, format, file);
			Main.logger.info("Saved screenshot at " + file.getCanonicalPath());
		} catch (IOException e) {
			Main.logger.merr(e, "Could not save screenshot");
		}
	}
	
	public static void interpretCommand(String command) {
		try {
			new ArgParser("", UserCommands.class).run(command);
		} catch (InvalidDeclarationError e) {
			Main.logger.merr(e);
		}
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
	
}

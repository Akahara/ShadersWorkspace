package wonder.shaderdisplay;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.imageio.ImageIO;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.systems.argparser.ArgParser;
import fr.wonder.commons.systems.argparser.InvalidDeclarationError;
import fr.wonder.commons.systems.argparser.annotations.Argument;
import fr.wonder.commons.systems.argparser.annotations.EntryPoint;
import fr.wonder.commons.systems.argparser.annotations.Option;
import imgui.ImGui;
import wonder.shaderdisplay.Resources.Snippet;

public class UserControls {

	private static final int[] screenSizeBuffer = new int[2];
	
	private static StringBuilder stdinBuffer = new StringBuilder();
	
	public static void init() {
		screenSizeBuffer[0] = GLWindow.winWidth;
		screenSizeBuffer[1] = GLWindow.winHeight;
	}
	
	public static void renderControls() {
		if(!ImGui.collapsingHeader("Controls"))
			return;
		
		// screenshot
		if(ImGui.button("Take screenshot"))
			Main.events.takeScreenshot = true;
		
		// window resize
		if(ImGui.dragInt2("New window size", screenSizeBuffer, 10, 1, 15000))
			GLWindow.resizeWindow(screenSizeBuffer[0], screenSizeBuffer[1]);
	}
	
	public static void takeScreenshot(TexturesSwapChain renderTargetsSwapChain) {
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
		int[] buffer = renderTargetsSwapChain.readColorAttachment(0, new int[w*h]);
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

	public static void readStdin() throws IOException {
		while(System.in.available() > 0) {
			char c = (char)System.in.read();
			if(c == '\n') {
				interpretCommand(stdinBuffer.toString().trim());
				stdinBuffer.setLength(0);
			}
			stdinBuffer.append(c);
		}
	}
	
	public static class UserCommands {
		
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
			System.out.println("Found " + snippets.size() + " snippets");
		}
		
	}
	
}

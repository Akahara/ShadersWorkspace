package wonder.shaderdisplay;

import java.io.File;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.systems.process.argparser.ArgParser;
import wonder.shaderdisplay.renderers.Renderer;

public class ShaderDisplay {
	
	private static ShaderDisplay activeDisplay;
	
	private static final ArgParser commandParser = new ArgParser(">", UserCommands.class);
	private final Renderer renderer;
	private final String name;
	
	private String latestCommand = "";
	
	public static ShaderDisplay createDisplay(Renderer renderer, File fragment) {
		if(activeDisplay != null)
			throw new IllegalStateException("A display already exists");
		
		ShaderDisplay display = new ShaderDisplay(renderer, fragment);
		ShaderDisplay.activeDisplay = display;
		UserCommands.activeDisplay = display;
		return display;
	}
	
	private ShaderDisplay(Renderer renderer, File fragment) {
		this.renderer = renderer;
		this.name = FilesUtils.getFileName(fragment);
	}
	
	public void renderFrame() {
		renderer.render();
	}
	
	public String getName() {
		return name;
	}
	
	public void evalCommand(String command) {
		if(command.isBlank())
			command = latestCommand;
		latestCommand = command;
		try {
			System.out.println("> " + command);
			commandParser.run(command);
		} catch (Throwable t) {
			Main.logger.merr(t);
		}
	}
	
}

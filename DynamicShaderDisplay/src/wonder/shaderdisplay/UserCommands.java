package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;

import fr.wonder.commons.systems.process.argparser.Argument;
import fr.wonder.commons.systems.process.argparser.EntryPoint;
import fr.wonder.commons.systems.process.argparser.Option;

public class UserCommands {

	static ShaderDisplay activeDisplay;
	
	public static class TimeOption {
		@Option(name = "--time", shortand = "-t", desc = "sets the time (in seconds)")
		public float time = -1;
		
		public boolean hasTime() {
			return time != -1;
		}
	}
	
	@EntryPoint(path = "time")
	@Argument(name ="time", defaultValue = "0")
	public static void setTime(float time) {
		UniformsContext.setTimeUniform(time);
	}
	
	@EntryPoint(path = "screen")
	@Argument(name = "file", defaultValue = "-")
	public static void takeScreenshot(TimeOption options, String filePath) throws IOException {
		File outputFile;
		outputFile = filePath.isBlank() || filePath.equals("-") ?
				new File(activeDisplay.getName() + "_" + (int)((System.currentTimeMillis()/1000)%1E6) + ".png") :
				new File(filePath);
		if(options.hasTime()) {
			float currentTime = UniformsContext.getTimeUniform();
			UniformsContext.setTimeUniform(options.time);
			activeDisplay.renderFrame();
			UniformsContext.setTimeUniform(currentTime);
		}
		outputFile = GLWindow.saveScreenshot(outputFile);
		Main.logger.info("File saved as " + outputFile.getCanonicalPath());
	}
	
	@EntryPoint(path = "resize")
	@Argument(name = "width", desc = "the new window width, set to -1 to change only the height")
	@Argument(name = "height", desc = "the new window height, set to -1 to change only the width")
	public static void setWindowSize(int width, int height) {
		GLWindow.resizeWindow(width, height);
	}
	
}

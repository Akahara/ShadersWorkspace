package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import imgui.ImGui;

public class UserControls {

	private static final int[] screenSizeBuffer = new int[2];
	
	public static void renderControls() {
		if(!ImGui.collapsingHeader("Controls"))
			return;
		
		// screenshot
		if(ImGui.button("Take screenshot"))
			Main.events.nextFrameIsScreenshot = true;
		
		// window resize
		ImGui.dragInt2("New window size", screenSizeBuffer, 10, 1, 5000);
		if(ImGui.button("Resize window"))
			GLWindow.resizeWindow(screenSizeBuffer[0], screenSizeBuffer[1]);
	}
	
	public static void takeScreenshot() {
		SimpleDateFormat df = new SimpleDateFormat("MMdd_HHmmss");
		String fileName = "screenshot_" + df.format(new Date()) + ".png";
		File file = new File(fileName);
		try {
			GLWindow.saveScreenshot(file);
			Main.logger.info("Saved screenshot at " + file.getCanonicalPath());
		} catch (IOException e) {
			Main.logger.merr(e, "Could not save screenshot");
		}
	}
	
}

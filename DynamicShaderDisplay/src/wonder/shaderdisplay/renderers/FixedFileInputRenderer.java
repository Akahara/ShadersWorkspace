package wonder.shaderdisplay.renderers;

import java.io.IOException;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Main;

public class FixedFileInputRenderer extends FormatedInputRenderer {

	@Override
	public void reloadInputFile() {
		try {
			String input = FilesUtils.read(Main.options.inputFile);
			rebuildBuffersInput(input);
		} catch (IOException e) {
			Main.logger.err("Unable to reload the input file: " + e.getMessage());
		}
	}
	
}

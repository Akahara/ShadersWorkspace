package wonder.shaderdisplay.renderers;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Main;

public class FixedFileInputRenderer extends FormatedInputRenderer {

	private final File inputFile;
	
	public FixedFileInputRenderer(File inputFile) {
		this.inputFile = Objects.requireNonNull(inputFile);
	}
	
	@Override
	public void reloadInputFile() {
		try {
			String input = FilesUtils.read(inputFile);
			rebuildBuffersInput(input);
		} catch (IOException e) {
			Main.logger.err("Unable to reload the input file: " + e.getMessage());
		}
	}
	
}

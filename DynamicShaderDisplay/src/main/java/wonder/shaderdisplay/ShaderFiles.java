package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ShaderFiles {

	protected final Path[] shaderFiles = new Path[Resources.SHADERS_COUNT];
	protected final String[] shaderSources = new String[Resources.SHADERS_COUNT];
	protected final List<Path> additionalFiles = new ArrayList<>();
	
	public void addDummyFile(File file) {
		additionalFiles.add(file.toPath());
	}
	
	public void addShaderFile(File file, int type) throws IOException {
		shaderFiles[type] = file.toPath();
		if(!file.isFile()) {
			String source = Resources.readDefaultSource(type);
			shaderSources[type] = source;
			Files.writeString(shaderFiles[type], source);
		} else {
			shaderSources[type] = Files.readString(shaderFiles[type]);
		}
	}
	
	public void completeWithDefaultSources() throws IOException {
		for(int i = 0; i < shaderSources.length; i++) {
			if(shaderSources[i] == null && Resources.REQUIRED_SHADERS[i])
				shaderSources[i] = Resources.readDefaultSource(i);
		}
	}
	
	public String[] pollShadersSources() {
		return shaderSources;
	}

}

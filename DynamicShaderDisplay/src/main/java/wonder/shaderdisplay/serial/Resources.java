package wonder.shaderdisplay.serial;

import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.FileWatcher;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.ShaderType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Resources {

	public static final String[] DEFAULT_SHADER_SOURCES;

	private static final String SNIPPET_FILE_EXTENSION = "snippets";
	private static final String SNIPPETS_FILE = "/snippets.snippets";

	public static final List<Snippet> snippets = new ArrayList<>();

	static {
		DEFAULT_SHADER_SOURCES = new String[ShaderType.COUNT];
		for (ShaderType type : ShaderType.TYPES) {
			DEFAULT_SHADER_SOURCES[type.ordinal()] = readResource(type.defaultSourcePath);
		}
	}

	public static void setDefaultFragmentTemplate(Main.RunOptions.FragmentTemplate template) {
		String templatePath = switch (template) {
            case FRAMEBUFFERS -> "/default_fragment_framebuffers.fs";
            case RAYCASTING -> "/default_fragment_raycasting.fs";
            case SHADERTOY -> "/default_fragment_shadertoy.fs";
            case STANDARD -> "/default_fragment_standard.fs";
        };
        DEFAULT_SHADER_SOURCES[ShaderType.FRAGMENT.ordinal()] = readResource(templatePath);
	}

	public static String readResource(String path) {
		try (InputStream is = FileWatcher.class.getResourceAsStream(path)) {
			if(is == null)
				throw new IllegalStateException("Resource " + path + " does not exist");
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	private static List<Snippet> readSnippets(String source) throws IOException {
		List<Snippet> snippets = new ArrayList<>();
		
		final String tokenStart = "BEGIN ", tokenEnd = "EOS";
		
		int current = 0;
		
		while(true) {
			int startPos = source.indexOf(tokenStart, current);
			if(startPos == -1)
				break;
			int headerEnd = source.indexOf('\n', startPos);
			int endPos = source.indexOf(tokenEnd, headerEnd);
			String name = source.substring(startPos+tokenStart.length(), headerEnd).trim();
			String code = source.substring(headerEnd+1, endPos);
			
			snippets.add(new Snippet(name, code));
			current = endPos + tokenEnd.length();
		}
		
		return snippets;
	}
	
	public static List<Snippet> filterSnippets(String filter) throws PatternSyntaxException {
		Pattern p = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
		return snippets.stream().filter(s -> p.matcher(s.name).find()).collect(Collectors.toList());
	}
	
	private static void loadSnippets(List<Snippet> snippets) {
		for(Snippet s : snippets) {
			if(Resources.snippets.contains(s)) {
				Main.logger.warn("Duplicate snippet found: '" + s.name + "'");
				continue;
			}
			Resources.snippets.add(s);
		}
	}
	
	public static void scanForAndLoadSnippets() {
		try {
			loadSnippets(readSnippets(readResource(SNIPPETS_FILE)));
			
			File currentDir = new File(".");
			Main.logger.debug("Searching for snippets file from '" + currentDir.getCanonicalPath() + "'");
			try (Stream<Path> paths = Files.walk(currentDir.toPath(), 2)) {
				for(File file : paths.map(Path::toFile).toList()) {
					if(SNIPPET_FILE_EXTENSION.equals(FilesUtils.getFileExtension(file))) {
						Main.logger.debug("Found snippets file '" + file.getCanonicalPath() + "'");
						loadSnippets(readSnippets(FilesUtils.read(file)));
					}
				}
			}

			Main.logger.debug("Loaded " + snippets.size() + " snippets");
		} catch (IOException e) {
			Main.logger.err(e, "Could not load all snippets");
		} finally {
			snippets.sort(Comparator.comparing(s -> s.name));
		}
	}

	public static void initializeSceneFiles(File sceneFile) throws IOException {
		FilesUtils.write(sceneFile, readResource("/default_scene.json"));
		File requiredFragmentShaderFile = new File(sceneFile.getParentFile(), "shader.fs"); // the file name must match that in the default scene file
		if (!requiredFragmentShaderFile.exists())
			FilesUtils.write(requiredFragmentShaderFile, DEFAULT_SHADER_SOURCES[ShaderType.FRAGMENT.ordinal()]);
	}

	public static class Snippet {
		
		public final String name;
		public final String code;
		
		public Snippet(String name, String code) {
			this.name = name;
			this.code = code;
		}
		
		@Override
		public boolean equals(Object obj) {
			return obj instanceof Snippet && ((Snippet)obj).name.equals(name);
		}
		
		@Override
		public int hashCode() {
			return name.hashCode();
		}
		
	}
	
}

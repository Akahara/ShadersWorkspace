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

	private static final String[] COMMON_FRAGMENT_EXTENSIONS = { ".fs", ".fragment", ".glsl" };
	private static final String[] COMMON_SCENE_EXTENSIONS = { ".json", ".scene" };

	public static final List<Snippet> snippets = new ArrayList<>();

	private static TemplateSceneFiles TEMPLATE_STANDARD = new TemplateSceneFiles("/templates/template_standard", true, "scene.json")
			.add("shader.fs");
	private static TemplateSceneFiles TEMPLATE_COMPUTE = new TemplateSceneFiles("/templates/template_compute", false, "scene.json")
			.add("compute.comp")
			.add("shader.vsfs");

	static {
		DEFAULT_SHADER_SOURCES = new String[ShaderType.COUNT];
		for (ShaderType type : ShaderType.TYPES) {
			DEFAULT_SHADER_SOURCES[type.ordinal()] = readResource(type.defaultSourcePath);
		}
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

	private static boolean hasAnyExtension(File file, String[] extensions) {
		return Stream.of(extensions).anyMatch(ext -> file.getName().endsWith(ext));
	}

	private static void setupMissingFragment(File fragment, String templateFilePath) throws IOException {
		if (!hasAnyExtension(fragment, COMMON_FRAGMENT_EXTENSIONS))
			Main.logger.warn("File " + fragment + " does not look like a fragment shader file, trying to run it as one anyway");

		FilesUtils.write(fragment, readResource(templateFilePath));
	}

	private static void setupMissingSceneFiles(File sceneFile, TemplateSceneFiles templateSceneFiles) throws IOException {
		if (!hasAnyExtension(sceneFile, COMMON_SCENE_EXTENSIONS))
			Main.logger.warn("File " + sceneFile + " does not look like a fragment shader file, trying to run it as one anyway");

		String sceneContent = readResource(templateSceneFiles.scenePath);
		for (String sourcePath : templateSceneFiles.extraSourcePaths) {
			String sourceOriginalName = new File(sourcePath).getName();
			String extraSourceName = !templateSceneFiles.renameFiles ? sourceOriginalName : FilesUtils.getFileName(sceneFile) + '.' + FilesUtils.getFileExtension(sourcePath);
			File extraSourceFile = new File(sceneFile.getParent(), extraSourceName);
			if (extraSourceFile.isFile())
				Main.logger.warn("File " + extraSourceFile + "' already exists, not replacing it");
			else
				FilesUtils.write(extraSourceFile, readResource(sourcePath));
			sceneContent = sceneContent.replace(sourceOriginalName, extraSourceName);
		}
		FilesUtils.write(sceneFile, sceneContent);
	}

	public static void setupSceneFilesIfNotExist(File sceneFile, Main.DisplayOptions.FragmentTemplate sceneTemplate) throws IOException {
		if (sceneFile.isFile())  {
			if (sceneTemplate != Main.DisplayOptions.FragmentTemplate.DEFAULT)
				Main.logger.warn("--template specified but the file already exists");
			return;
		}

		switch (sceneTemplate)
		{
			case FRAMEBUFFERS -> setupMissingFragment(sceneFile, "/templates/default_fragment_framebuffers.fs");
			case RAYCASTING -> setupMissingFragment(sceneFile, "/templates/default_fragment_raycasting.fs.fs");
			case SHADERTOY -> setupMissingFragment(sceneFile, "/templates/default_fragment_shadertoy.fs");
			case STANDARD -> {
				if (hasAnyExtension(sceneFile, COMMON_SCENE_EXTENSIONS))
					setupMissingSceneFiles(sceneFile, TEMPLATE_STANDARD);
				else
					setupMissingFragment(sceneFile, "/templates/default_fragment_standard.fs");
			}
            case COMPUTE -> setupMissingSceneFiles(sceneFile, TEMPLATE_COMPUTE);
			default -> throw new IOException("Invalid template " + sceneTemplate);
        }
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

	private static class TemplateSceneFiles {

		private final String rootPath;
		public final boolean renameFiles;
		public final String scenePath;
		public final List<String> extraSourcePaths = new ArrayList<>();

		public TemplateSceneFiles(String rootPath, boolean renameFiles, String sceneFile) {
			this.rootPath = rootPath;
			this.renameFiles = renameFiles;
			this.scenePath = rootPath + '/' + sceneFile;
		}

		TemplateSceneFiles add(String path) {
			this.extraSourcePaths.add(rootPath + '/' + path);
			return this;
		}
	}

}

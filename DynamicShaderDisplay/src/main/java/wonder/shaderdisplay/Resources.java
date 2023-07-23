package wonder.shaderdisplay;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.utils.ArrayOperator;

public class Resources {
	
	public static final int TYPE_VERTEX = 0;
	public static final int TYPE_GEOMETRY = 1;
	public static final int TYPE_FRAGMENT = 2;
	public static final int TYPE_COMPUTE = 3;
	
	public static final int SHADERS_COUNT = 4;
	
	private static final String[] DEFAULT_SOURCES = {
            "/defaultVertex.vs",
            "/defaultGeometry.gs",
            "/defaultFragment.fs",
            "/defaultCompute.cs",
	};
	
	public static final boolean[] REQUIRED_SHADERS = { true, false, true, false };
	
	private static final String SNIPPET_FILE_EXTENSION = "snippets";
	private static final String SNIPPETS_FILE = "/snippets.snippets";
	
	public static final List<Snippet> SNIPPETS = new ArrayList<>();
	
	public static String readResource(String path) throws IOException {
		try (InputStream is = ShaderFileWatcher.class.getResourceAsStream(path)) {
			if(is == null)
				throw new IOException("Resource " + path + " does not exist");
			return new String(is.readAllBytes());
		}
	}

	public static String readDefaultSource(int type) throws IOException {
		return readResource(DEFAULT_SOURCES[type]);
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
		return SNIPPETS.stream().filter(s -> p.matcher(s.name).find()).collect(Collectors.toList());
	}
	
	private static void loadSnippets(List<Snippet> snippets) {
		for(Snippet s : snippets) {
			if(SNIPPETS.contains(s)) {
				Main.logger.warn("Dupplicate snippet found: '" + s.name + "'");
				continue;
			}
			SNIPPETS.add(s);
		}
	}
	
	public static void scanForAndLoadSnippets() {
		try {
			loadSnippets(readSnippets(readResource(SNIPPETS_FILE)));
			
			File currentDir = new File(".");
			Main.logger.debug("Searching for snippets file from '" + currentDir.getCanonicalPath() + "'");
			for(File file : Files.walk(currentDir.toPath(), 2).map(Path::toFile).collect(Collectors.toList())) {
				if(SNIPPET_FILE_EXTENSION.equals(FilesUtils.getFileExtension(file))) {
					Main.logger.debug("Found snippets file '" + file.getCanonicalPath() + "'");
					loadSnippets(readSnippets(FilesUtils.read(file)));
				}
			}
			
			Main.logger.debug("Loaded " + SNIPPETS.size() + " snippets");
		} catch (IOException e) {
			Main.logger.err(e, "Could not load all snippets");
		} finally {
			SNIPPETS.sort((s1,s2) -> s1.name.compareTo(s2.name));
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

	public static String concatStandardShaderSource(String[] shaders) {
		return String.join("\n", ArrayOperator.filter(new String[] {
				shaders[TYPE_VERTEX  ],
				shaders[TYPE_FRAGMENT],
				shaders[TYPE_GEOMETRY], },
				Objects::nonNull));
	}

	public static String concatComputeShaderSource(String[] shaders) {
		return String.join("\n", ArrayOperator.filter(new String[] {
				shaders[TYPE_COMPUTE ], },
				Objects::nonNull));
	}
	
}

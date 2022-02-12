package wonder.shaderdisplay;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import fr.wonder.commons.types.Tuple;

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
	
	public static final boolean[] REQUIRED_SHADERS = { true, false, true };
	
	private static final String SNIPPETS_FILE = "/snippets.fs";
	
	private static String readResource(String path) throws IOException {
		try (InputStream is = ShaderFileWatcher.class.getResourceAsStream(path)) {
			if(is == null)
				throw new IOException("Resource " + path + " does not exist");
			return new String(is.readAllBytes());
		}
	}

	public static String readDefaultSource(int type) throws IOException {
		return readResource(DEFAULT_SOURCES[type]);
	}
	
	private static List<Tuple<String, String>> readSnippetsList() throws IOException {
		String wholeSource = readResource(SNIPPETS_FILE);
		List<Tuple<String, String>> snippets = new ArrayList<>();
		
		Pattern p = Pattern.compile("/\\*(.*)");
		Matcher m = p.matcher(wholeSource);
		String snippetName = null;
		int snippetStartPos = -1;
		while(m.find()) {
			if(snippetName != null) {
				String snippetSource = wholeSource.substring(snippetStartPos, m.start());
				snippets.add(new Tuple<>(snippetName, snippetSource));
			}
			
			snippetName = m.group(1);
			if(snippetName.endsWith(" */"))
				snippetName = snippetName.substring(0, snippetName.length()-3);
			snippetStartPos = m.start();
		}
		
		if(snippetName != null) {
			String snippetSource = wholeSource.substring(snippetStartPos);
			snippets.add(new Tuple<>(snippetName, snippetSource));
		}
		
		return snippets;
	}
	
	private static String collectSnippets(String name, String separator,
			Function<Tuple<String, String>, String> snippetValueGetter) throws IOException {
		String nameKey = name.equals("_") ? "" : name.toLowerCase();
		List<Tuple<String, String>> snippets = readSnippetsList();
		snippets.removeIf(snippet -> !snippet.a.toLowerCase().contains(nameKey));
		if(snippets.isEmpty())
			return "No matching snippet";
		return String.join(separator, snippets.stream().map(snippetValueGetter).collect(Collectors.toList()));
	}

	public static String readSnippets(String name) throws IOException {
		return collectSnippets(name, "", tuple -> tuple.b);
	}
	
	public static String listSnippets(String name) throws IOException {
		return collectSnippets(name, "\n", tuple -> tuple.a);
	}
	
}

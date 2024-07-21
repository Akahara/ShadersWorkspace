package wonder.shaderdisplay;

import fr.wonder.commons.files.FilesUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FileCache {

    private final Map<File, String> readFiles = new HashMap<>();

    public String readFile(File file) throws IOException {
        String loadedContent = readFiles.get(file);
        if (loadedContent == null) {
            loadedContent = FilesUtils.read(file);
            readFiles.put(file, loadedContent);
        }
        return loadedContent;
    }

    public void invalidateContent(File file) {
        readFiles.remove(file);
    }

}

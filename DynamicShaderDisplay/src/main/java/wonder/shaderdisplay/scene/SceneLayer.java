package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;

import java.util.List;
import java.util.stream.Stream;

public abstract class SceneLayer {

    public boolean enabled = true;

    public abstract String getDisplayName();
    public abstract void renderControls();
    public abstract void dispose();

    public abstract Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles();

}

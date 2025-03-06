package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;

import java.util.stream.Stream;

public abstract class SceneLayer {

    public boolean enabled = true;
    public boolean isBuiltinHiddenLayer = false;
    protected final String displayName;
    public final ExecutionCondition[] executions;

    public SceneLayer(String displayName, ExecutionCondition[] executions) {
        this.displayName = displayName;
        this.executions = executions;
    }

    public abstract String getDisplayName();
    public abstract void renderControls(Scene scene);
    public abstract void dispose();

    public abstract Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles();

}

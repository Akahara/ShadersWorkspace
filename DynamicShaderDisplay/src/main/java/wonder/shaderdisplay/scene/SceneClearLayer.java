package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;

import java.util.Arrays;
import java.util.stream.Stream;

public class SceneClearLayer extends SceneLayer {

    public final String[] outRenderTargets;
    public final float[] clearColor;
    public final float clearDepth;

    public SceneClearLayer(String displayName, ExecutionCondition[] executions, String[] outRenderTargets, float[] clearColor, float clearDepth) {
        super(displayName, executions);
        this.outRenderTargets = outRenderTargets;
        this.clearColor = clearColor;
        this.clearDepth = clearDepth;
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? "clear" + Arrays.toString(outRenderTargets) : displayName;
    }

    @Override
    public void renderControls(Scene scene) {}

    @Override
    public void dispose() {}

    @Override
    public Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles() {
        return Stream.of();
    }

}

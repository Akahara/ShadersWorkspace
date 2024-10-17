package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;

import java.util.Arrays;
import java.util.stream.Stream;

public class SceneClearLayer extends SceneLayer {

    public final String[] outRenderTargets;
    public final float[] clearColor;
    public final float clearDepth;

    public SceneClearLayer(String[] outRenderTargets, float[] clearColor, float clearDepth) {
        this.outRenderTargets = outRenderTargets;
        this.clearColor = clearColor;
        this.clearDepth = clearDepth;
    }

    @Override
    public String getDisplayName() {
        return "clear" + Arrays.toString(outRenderTargets);
    }

    @Override
    public void renderControls() {}

    @Override
    public void dispose() {}

    @Override
    public Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles() {
        return Stream.of();
    }

}

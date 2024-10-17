package wonder.shaderdisplay.scene;

import fr.wonder.commons.annotations.Nullable;

public interface RenderableLayer {

    UniformDefaultValue[] getDefaultUniformValues();
    @Nullable String[] getOutputRenderTargets();

}

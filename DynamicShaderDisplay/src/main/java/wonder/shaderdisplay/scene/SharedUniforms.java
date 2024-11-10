package wonder.shaderdisplay.scene;

import imgui.ImGui;
import wonder.shaderdisplay.uniforms.arbitrary.ArbitraryUniform;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SharedUniforms {

    private record SharedUniform(Number[][] value, ArbitraryUniform controlableUniform) {}

    private final Map<String, SharedUniform> sharedUniformValues = new HashMap<>();

    public SharedUniforms() {}

    public SharedUniforms(String[] names) {
        for (String n : names)
            sharedUniformValues.put(n, null);
    }

    public boolean isShared(String name) {
        return sharedUniformValues.containsKey(name);
    }

    public void reset() {
        sharedUniformValues.entrySet().forEach(s -> s.setValue(null));
    }

    public void loadOrStoreValue(ArbitraryUniform uniform) {
        String name = uniform.asUniform().name;
        SharedUniform cached = sharedUniformValues.get(name);
        if (cached == null)
            sharedUniformValues.put(name, new SharedUniform(uniform.getValues(), uniform));
        else
            uniform.copy(cached.value);
    }

    public void render() {
        boolean anyDrawn = false;
        for (SharedUniform u : sharedUniformValues.values()) {
            if (u != null) {
                u.controlableUniform.asUniform().renderControl();
                anyDrawn = true;
            }
        }
        if (anyDrawn)
            ImGui.separator();
    }

}

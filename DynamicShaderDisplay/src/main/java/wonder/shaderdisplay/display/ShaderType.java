package wonder.shaderdisplay.display;

public enum ShaderType {

    VERTEX("/default_vertex.vs"),
    FRAGMENT("/default_fragment_standard.fs"),
    GEOMETRY("/default_geometry.gs"),
    COMPUTE("/default_compute.cs");

    public final String defaultSourcePath;

    ShaderType(String defaultSourcePath) {
        this.defaultSourcePath = defaultSourcePath;
    }

    public static final int COUNT = ShaderType.values().length;
    public static final ShaderType[] TYPES = values();
    public static final ShaderType[] STANDARD_TYPES = { VERTEX, GEOMETRY, FRAGMENT };

}

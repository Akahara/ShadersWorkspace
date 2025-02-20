package wonder.shaderdisplay.display;

public enum ShaderType {

    VERTEX("/templates/default_vertex.vs"),
    FRAGMENT("/templates/default_fragment_standard.fs"),
    GEOMETRY("/templates/default_geometry.gs"),
    COMPUTE("/templates/default_compute.cs");

    public final String defaultSourcePath;

    ShaderType(String defaultSourcePath) {
        this.defaultSourcePath = defaultSourcePath;
    }

    public String getShaderCompilerMacro() {
        return "DSD_" + name();
    }

    public static final int COUNT = ShaderType.values().length;
    public static final ShaderType[] TYPES = values();
    public static final ShaderType[] STANDARD_TYPES = { VERTEX, GEOMETRY, FRAGMENT };

}

package wonder.shaderdisplay.display;

public enum ShaderType {

    VERTEX("/defaultVertex.vs"),
    FRAGMENT("/defaultFragment.fs"),
    GEOMETRY("/defaultGeometry.gs"),
    COMPUTE("/defaultCompute.cs");

    public final String defaultSourcePath;

    ShaderType(String defaultSourcePath) {
        this.defaultSourcePath = defaultSourcePath;
    }

    public static int COUNT = ShaderType.values().length;
    public static ShaderType[] TYPES = values();
    public static ShaderType[] STANDARD_TYPES = { VERTEX, GEOMETRY, FRAGMENT };

}

package wonder.shaderdisplay.display;

public enum ShaderType {

    VERTEX,
    FRAGMENT,
    GEOMETRY,
    COMPUTE;

    public static int COUNT = ShaderType.values().length;
    public static ShaderType[] TYPES = values();
    public static ShaderType[] STANDARD_TYPES = { VERTEX, GEOMETRY, FRAGMENT };

}

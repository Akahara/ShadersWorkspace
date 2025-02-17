package wonder.shaderdisplay.display;

import fr.wonder.commons.exceptions.ErrorWrapper;
import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.FileCache;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.controls.ShaderDebugTool;
import wonder.shaderdisplay.scene.*;
import wonder.shaderdisplay.serial.Resources;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

public class ShaderCompiler {

    private static boolean debugResolvedShaders = false;

    public static void setDebugResolvedShaders(boolean enable) {
        debugResolvedShaders = enable;
    }

    private final Scene scene;
    private final FileCache fileCache = new FileCache();

    public ShaderCompiler(Scene scene) {
        this.scene = scene;
    }

    public static class ShaderCompilationResult {
        public boolean success = true;
        public boolean fileDependenciesUpdated = false;
        public ErrorWrapper errors = new ErrorWrapper("Could not compile a shader");

        public static ShaderCompilationResult error() {
            ShaderCompilationResult r = new ShaderCompilationResult();
            r.success = false;
            return r;
        }

        public static ShaderCompilationResult success() {
            return new ShaderCompilationResult();
        }
    }

    public ShaderCompilationResult compileShaders(ErrorWrapper errors, CompilableLayer layer) {
        ShaderSet newShaders = new ShaderSet();

        ShaderFileSet fileSet = layer.getCompilationFileset();
        boolean hasGeometry = fileSet.hasCustomShader(ShaderType.GEOMETRY);
        ShaderCompilationResult result = new ShaderCompilationResult();
        result.errors = errors;

        try {
            if (!fileSet.isCompute()) {
                buildShader(result.errors.subErrors("vertex shader"), layer, newShaders, ShaderType.VERTEX, GL_VERTEX_SHADER);
                buildShader(result.errors.subErrors("fragment shader"), layer, newShaders, ShaderType.FRAGMENT, GL_FRAGMENT_SHADER);
                if (hasGeometry) {
                    buildShader(result.errors.subErrors("geometry shader"), layer, newShaders, ShaderType.GEOMETRY, GL_GEOMETRY_SHADER);
                    //verifyGeometryShaderInputType(result.errors, fileSet.getSource(ShaderType.GEOMETRY).getRawSource(), GL_TRIANGLES);
                }
            } else {
                buildShader(result.errors, layer, newShaders, ShaderType.COMPUTE, GL_COMPUTE_SHADER);
            }

            result.errors.assertNoErrors();
            newShaders.program = glCreateProgram();

            if (!fileSet.isCompute()) {
                glAttachShader(newShaders.program, newShaders.shaderIds[ShaderType.VERTEX.ordinal()]);
                glAttachShader(newShaders.program, newShaders.shaderIds[ShaderType.FRAGMENT.ordinal()]);
                if (hasGeometry)
                    glAttachShader(newShaders.program, newShaders.shaderIds[ShaderType.GEOMETRY.ordinal()]);
            } else {
                glAttachShader(newShaders.program, newShaders.shaderIds[ShaderType.COMPUTE.ordinal()]);
            }
            glLinkProgram(newShaders.program);

            if (glGetProgrami(newShaders.program, GL_LINK_STATUS) == GL_FALSE)
                result.errors.addAndThrow("Linking error: " + glGetProgramInfoLog(newShaders.program).strip());
            glValidateProgram(newShaders.program);
            if (glGetProgrami(newShaders.program, GL_VALIDATE_STATUS) == GL_FALSE)
                result.errors.addAndThrow("Unexpected error: " + glGetProgramInfoLog(newShaders.program).strip());
        } catch (ErrorWrapper.WrappedException e) {
            newShaders.disposeAll();
            result.success = false;
            return result;
        }

        Main.logger.info("Compiled successfully " + fileSet.getPrimaryFileName() + " : " + getCompilationTimestampString());

        result.fileDependenciesUpdated = areShaderFileSetDifferent(layer.getCompiledShaders().shaderSourceFiles, newShaders.shaderSourceFiles);
        layer.replaceCompiledShaders(newShaders);

        StringBuilder pseudoTotalSource = new StringBuilder();
        for (ShaderType type : ShaderType.STANDARD_TYPES) {
            String source = newShaders.resolvedSources[type.ordinal()];
            if (source != null)
                pseudoTotalSource.append(source);
        }

        layer.getUniformControls().rescan(newShaders.program, pseudoTotalSource.toString());
        return result;
    }

    private static boolean areShaderFileSetDifferent(File[][] setA, File[][] setB) {
        for (int i = 0; i < setA.length; i++) {
            if ((setA[i] == null) != (setB[i] == null))
                return true;
            if (setA[i] != null && !Set.of(setA[i]).equals(Set.of(setB[i])))
                return true;
        }
        return false;
    }

    private static void verifyGeometryShaderInputType(ErrorWrapper errors, String geometrySource, int glDrawMode) throws ErrorWrapper.WrappedException {
        if (geometrySource == null)
            return;

        String expected =
            glDrawMode == GL_LINES ? "lines" :
            glDrawMode == GL_POINTS ? "points" :
            glDrawMode == GL_TRIANGLES ? "triangles" :
            null;
        if (expected == null)
            throw new IllegalArgumentException("Unknown draw mode");

        Matcher m = Pattern.compile("layout\\((\\w+)\\) in;").matcher(geometrySource);
        if (!m.find())
            errors.addAndThrow("The geometry shader is missing its 'layout(...) in;' directive");
        String in = m.group(1);
        if (!in.equals(expected))
            errors.addAndThrow("The geometry shader input type does not match the provided type, expected '" + expected + "', got '" + in + "'");
    }

    private static String getCompilationTimestampString() {
        LocalDateTime time = LocalDateTime.now();
        int hour = time.getHour();
        int minute = time.getMinute();
        int second = time.getSecond();
        int millis = time.get(ChronoField.MILLI_OF_SECOND);
        return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millis);
    }

    private void buildShader(ErrorWrapper errors, CompilableLayer layer, ShaderSet outShaderSet, ShaderType type, int glType) {
        ShaderFileSet.ShaderSource sourceObject = layer.getCompilationFileset().getSource(type);
        String source, sourceName;

        if (sourceObject == null) {
            errors.add("Could not compile shader: missing " + type.name().toLowerCase() + " shader");
            return;
        }
        if (sourceObject.isRawSource()) {
            source = sourceObject.getRawSource();
            sourceName = layer.getCompilationFileset().getPrimaryFileName() + ":" + type;
            outShaderSet.shaderSourceFiles[type.ordinal()] = new File[0];
        } else {
            sourceName = sourceObject.getFileSource().getName();
            try {
                Set<File> sourceCodeFiles = new HashSet<>();
                source = resolveShaderFile(sourceObject.getFileSource(), sourceCodeFiles);
                source = ShaderDebugTool.patchShaderSource(source);
                outShaderSet.shaderSourceFiles[type.ordinal()] = sourceCodeFiles.toArray(File[]::new);
            } catch (IOException e) {
                errors.add("Compilation error while reading source code for '" + sourceName + "': " + e.getMessage());
                return;
            }
        }

        Stream<Macro> allMacros = scene == null ? Stream.empty() : scene.macros.stream();
        allMacros = Stream.concat(allMacros, Stream.of(layer.getCompilationMacros()));
        allMacros = Stream.concat(allMacros, Stream.of(new Macro("DSD"), new Macro(type.getShaderCompilerMacro())));
        source = addMacroDefinitions(source, allMacros);

        if (debugResolvedShaders && !sourceObject.isRawSource()) {
            File primarySourceCodeFile = sourceObject.getFileSource();
            File resolvedFile = new File(primarySourceCodeFile.getParentFile(), primarySourceCodeFile.getName() + ".resolved");
            try {
                FilesUtils.write(resolvedFile, source);
                Main.logger.debug("Wrote resolved shader to " + resolvedFile);
            } catch (IOException e) {
                Main.logger.err(e, "Could not write resolved shader to " + resolvedFile);
            }
        }

        int id = glCreateShader(glType);
        glShaderSource(id, source);
        glCompileShader(id);
        outShaderSet.shaderIds[type.ordinal()] = id;
        outShaderSet.resolvedSources[type.ordinal()] = source;
        if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            errors.add("Compilation error in '" + sourceName + "':\n" + glGetShaderInfoLog(id));
    }

    public static String addMacroDefinitions(String source, Stream<Macro> macros) {
        StringBuilder macroDefinitions = new StringBuilder();
        macroDefinitions.append('\n');
        macros.forEach(macro -> macroDefinitions.append(String.format("#define %s %s\n", macro.name, macro.value)));
        if (!debugResolvedShaders)
            macroDefinitions.append("#line 2\n");
        return source.replaceFirst("\n", macroDefinitions.toString());
    }

    public static int buildRawShader(String source, int glType) {
        int id = glCreateShader(glType);
        glShaderSource(id, source);
        glCompileShader(id);
        if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Compilation error: " + glGetShaderInfoLog(id));
        return id;
    }

    public static void deleteShaders(int... shaders) {
        if (shaders == null) return;
        for(int s : shaders) {
            if(s > 0)
                glDeleteShader(s);
        }
    }

    public static void deletePrograms(int... programs) {
        for(int s : programs) {
            if(s > 0)
                glDeleteProgram(s);
        }
    }

    private static class IncludeReplacement {
        final int from, to;
        final File file;
        final String rawSource;

        public IncludeReplacement(int from, int to, File file) {
            this.from = from;
            this.to = to;
            this.file = file;
            this.rawSource = null;
        }

        public IncludeReplacement(int from, int to, String rawSource) {
            this.from = from;
            this.to = to;
            this.file = null;
            this.rawSource = rawSource;
        }
    }

    private String resolveShaderFile(File file, Set<File> outSourceCodeFiles) throws IOException {
        StringBuilder sb = resolveShaderFileRecur(file, outSourceCodeFiles);

        for (int i = sb.indexOf("#version", 1); i != -1; i = sb.indexOf("#version", i+3)) {
            sb.insert(i, "//");
        }

        return sb.toString();
    }

    private StringBuilder resolveShaderFileRecur(File file, Set<File> outSourceCodeFiles) throws IOException {
        outSourceCodeFiles.add(file);

        StringBuilder sb = new StringBuilder(fileCache.readFile(file));
        List<IncludeReplacement> replacements = new ArrayList<>();

        int i = 0;
        int nextInclude = -1;
        int line = 1;
        do {
            if (nextInclude < i)
                nextInclude = sb.indexOf("#include", i);
            if (nextInclude == i) {
                int lineEnd = sb.indexOf("\n", i);
                if (lineEnd == -1) {
                    lineEnd = sb.length();
                    sb.append('\n');
                }
                String includeLine = sb.substring(i, lineEnd);
                Matcher m = Pattern.compile("#include \"(.*)\".*").matcher(includeLine.strip());
                if (m.matches()) {
                    int replacementStart = i;
                    int replacementEnd = lineEnd;
                    int substitutionEnd = lineEnd;
                    if (!debugResolvedShaders) {
                        String clauseStart = "#line 1 // start of " + m.group(1) + "\n";
                        String clauseEnd = "#line " + (line+1) + " // end of " + m.group(1) + ", back in " + file.getName() + "\n";
                        sb.insert(lineEnd + 1, clauseEnd);
                        sb.insert(i, clauseStart);
                        replacementStart += clauseStart.length();
                        replacementEnd += clauseStart.length();
                        substitutionEnd += clauseEnd.length() + clauseStart.length();
                    }
                    String includeFileName = m.group(1);
                    switch (includeFileName) {
                        case "dsd_debug.glsl" -> {
                            String rawSource = Resources.readResource("/dsd_debug.glsl");
                            replacements.add(new IncludeReplacement(replacementStart, replacementEnd, rawSource));
                        }
                        default -> {
                            File includeFile = new File(file.getParentFile(), includeFileName);
                            replacements.add(new IncludeReplacement(replacementStart, replacementEnd, includeFile));
                        }
                    }
                    i = substitutionEnd;
                }
            }

            i = sb.indexOf("\n", i) + 1;
            line++;
        } while (i != 0);

        for (int j = replacements.size(); j > 0; j--) {
            IncludeReplacement r = replacements.get(j-1);
            if (r.file != null)
                sb.replace(r.from, r.to, resolveShaderFileRecur(r.file, outSourceCodeFiles).toString());
            else
                sb.replace(r.from, r.to, r.rawSource);
        }

        return sb;
    }

}


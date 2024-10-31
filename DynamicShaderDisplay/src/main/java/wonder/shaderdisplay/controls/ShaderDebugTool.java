package wonder.shaderdisplay.controls;

import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import wonder.shaderdisplay.display.StorageBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_ACTIVE_UNIFORM_BLOCKS;
import static org.lwjgl.opengl.GL31.glGetActiveUniformBlockName;

public class ShaderDebugTool {

    private static final int HEADER_SIZE = 2;
    private static final int DEBUG_ELEMENTS_CAPACITY = 1024;

    // Must match dsd_debug.glsl
    private static final int
            _DSDD_TYPE_INT1 = 1,
            _DSDD_TYPE_INT2 = 2,
            _DSDD_TYPE_INT3 = 3,
            _DSDD_TYPE_INT4 = 4,
            _DSDD_TYPE_FLOAT1 = 5,
            _DSDD_TYPE_FLOAT2 = 6,
            _DSDD_TYPE_FLOAT3 = 7,
            _DSDD_TYPE_FLOAT4 = 8,
            _DSDD_TYPE_BITS = 8;

    private final StorageBuffer debugBuffer;
    private final StorageBuffer debugBufferTemplate;
    private final ByteBuffer readbackBuffer;

    private boolean inUse = false;

    private static final List<String> registeredDebugNames = new ArrayList<>();

    public ShaderDebugTool() {
        int totalByteSize = 4 * (HEADER_SIZE + DEBUG_ELEMENTS_CAPACITY);
        ByteBuffer header = ByteBuffer.allocateDirect(4 * HEADER_SIZE).order(ByteOrder.nativeOrder());
        header.putInt(0, DEBUG_ELEMENTS_CAPACITY); // _dsdd_bufLength
        header.putInt(4*1, 0); // _dsdd_nextWrite
        debugBufferTemplate = new StorageBuffer(totalByteSize);
        debugBuffer = new StorageBuffer(totalByteSize);
        debugBufferTemplate.setData(0, header);
        readbackBuffer = ByteBuffer.allocateDirect(totalByteSize).order(ByteOrder.nativeOrder());
    }

    public void reset() {
        debugBuffer.copyFrom(debugBufferTemplate);
        inUse = false;
    }

    public void tryBindToProgram(int program) {
        if (true) {
            debugBuffer.bind(0);
            inUse = true;
            //return;
        }
        int blocks = glGetProgrami(program, GL_ACTIVE_UNIFORM_BLOCKS);
        for (int i = 0; i < blocks; i++) {
            String name = glGetActiveUniformBlockName(program, 0);
            if (name.equals("_dsdd")) {
                debugBuffer.bind(i);
                inUse = true;
                break;
            }
        }
    }

    public void renderControls() {
        if (!inUse) {
            ImGui.text(
                "This can be used to debug one or more values in your shaders, for example:\n"
                + "#include \"dsd_debug.glsl\"\n"
                + "uniform ivec2 u_cursor;\n"
                + "void main() {\n"
                + "  DSD_DEBUG_INIT();\n"
                + "  DSD_DEBUG_CURSOR();\n"
                + "  // or\n"
                + "  DSD_DEBUG_COND(gl_FragCoord.x==0 && gl_FragCoord.y==0);\n"
                + "  \n"
                + "  vec2 myval = ...;\n"
                + "  DSD_DEBUG(myvar, myval);\n"
                + "}");
            return;
        }
        debugBuffer.read(readbackBuffer, 0, readbackBuffer.capacity());
        int filledSize = readbackBuffer.getInt(1*4);
        if (filledSize > DEBUG_ELEMENTS_CAPACITY)
            ImGui.textColored(0xffffaaaa, "Debug capacity exceeded, did you forget a DSD_DEBUG_COND?");

        int flags = ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersV;

        if (ImGui.beginTable("Debug", 2, flags, 0.f, 0.f)) {
            readbackBuffer.position(4*2);
            while (readbackBuffer.remaining() > 4) {
                int elementHeader = readbackBuffer.getInt();
                if (elementHeader == 0)
                    break;
                int nameKey = elementHeader>>_DSDD_TYPE_BITS;
                String name = nameKey < 0 || nameKey >= registeredDebugNames.size() ? String.format("%d?", nameKey) : registeredDebugNames.get(nameKey);
                String valAsString = switch (elementHeader & ((1<<_DSDD_TYPE_BITS)-1)) {
                    case _DSDD_TYPE_INT1 -> String.format("%d", readbackBuffer.getInt());
                    case _DSDD_TYPE_INT2 -> String.format("%d %d", readbackBuffer.getInt(), readbackBuffer.getInt());
                    case _DSDD_TYPE_INT3 -> String.format("%d %d %d", readbackBuffer.getInt(), readbackBuffer.getInt(), readbackBuffer.getInt());
                    case _DSDD_TYPE_INT4 -> String.format("%d %d %d %d", readbackBuffer.getInt(), readbackBuffer.getInt(), readbackBuffer.getInt(), readbackBuffer.getInt());
                    case _DSDD_TYPE_FLOAT1 -> String.format("%10.3f", readbackBuffer.getFloat());
                    case _DSDD_TYPE_FLOAT2 -> String.format("%10.3f %10.3f", readbackBuffer.getFloat(), readbackBuffer.getFloat());
                    case _DSDD_TYPE_FLOAT3 -> String.format("%10.3f %10.3f %10.3f", readbackBuffer.getFloat(), readbackBuffer.getFloat(), readbackBuffer.getFloat());
                    case _DSDD_TYPE_FLOAT4 -> String.format("%10.3f %10.3f %10.3f %10.3f", readbackBuffer.getFloat(), readbackBuffer.getFloat(), readbackBuffer.getFloat(), readbackBuffer.getFloat());
                    default -> "?";
                };
                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text(name);
                ImGui.tableSetColumnIndex(1);
                ImGui.text(valAsString);
            }
            ImGui.endTable();
        }
    }

    private static void registerDebugName(String name) {
        if (!registeredDebugNames.contains(name))
            registeredDebugNames.add(name);
    }

    private static String getDebugNamesDefinitions() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < registeredDebugNames.size(); i++) {
            sb.append(String.format("#define _DSDD_NAME_%s %d\n", registeredDebugNames.get(i), i));
        }
        return sb.toString();
    }

    public static String patchShaderSource(String source) {
        // Collect new names
        Matcher m = Pattern.compile("DSD_DEBUG\\(([^,]+),").matcher(source);
        while (m.find())
            registerDebugName(m.group(1));

        // Insert the macro definitions
        return source.replaceFirst("// _DSD_DEBUG_NAMES", getDebugNamesDefinitions());
    }

}

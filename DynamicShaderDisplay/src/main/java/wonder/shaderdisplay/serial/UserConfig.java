package wonder.shaderdisplay.serial;

import com.fasterxml.jackson.annotation.JsonValue;
import wonder.shaderdisplay.Main;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UserConfig {

    private static final String CONFIG_FILE_NAME = "config.json";

    public static UserConfig config;

    public static File getProjectConfigDir(File projectRootFile) {
        File userHome = new File(System.getProperty("user.home"));
        File configDirectory = new File(new File(userHome, ".wonder"), "dsd");
        File projectConfigDirectory = new File(configDirectory, projectRootFile.getAbsolutePath().replaceAll("[\\\\/:*?\"<>|]", "_"));
        if (!projectConfigDirectory.isDirectory() && !projectConfigDirectory.mkdirs())
            throw new RuntimeException("Could not create the project config directory at " + projectConfigDirectory.getAbsolutePath());
        return projectConfigDirectory;
    }

    public static void loadConfig(File projectRootFile) {
        File mainConfigFile = new File(getProjectConfigDir(projectRootFile), CONFIG_FILE_NAME);
        if (!mainConfigFile.exists()) {
            config = new UserConfig();
            return;
        }

        try {
            config = JsonUtils.JSON_MAPPER.readValue(mainConfigFile, UserConfig.class);
        } catch (IOException e) {
            Main.logger.merr(e, "Could not load the config file");
            config = new UserConfig();
        }
    }

    public static void saveConfig(File projectRootFile) {
        File mainConfigFile = new File(getProjectConfigDir(projectRootFile), CONFIG_FILE_NAME);
        try {
            JsonUtils.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(mainConfigFile, config);
        } catch (IOException e) {
            Main.logger.merr(e, "Could not save the config file");
        }
    }


    public static class Freecam {
        public float[] position = new float[] { 0,0,0 };
        public float[] rotation = new float[] { 1,0,0,0 };
        public float speed = 1.f;
    }

    public static class LayerState {
        public boolean enabled = true;
    }

    public static class TimeLoopConfig {
        public enum LoopType {
            NO_LOOP("none"),
            LOOP_FRAME("between frames"),
            LOOP_TIME("between timestamps");

            public static LoopType[] values = values();

            public final String displayName;

            LoopType(String displayName) {
                this.displayName = displayName;
            }

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }

        public LoopType loopTime = LoopType.NO_LOOP;
        public float loopFrom = 0, loopTo = 0; // always in seconds
    }

    public static class AudioConfig {
        public boolean mute = false;
    }

    public Freecam freecam = new Freecam();
    public LayerState[] layers = new LayerState[0];
    public int[] windowLocation = null;
    public boolean hideAllWindows = false;
    public List<String> visibleImGuiWindows = List.of("Uniforms");
    public TimeLoopConfig timeLoop = new TimeLoopConfig();
    public AudioConfig audio = new AudioConfig();

}

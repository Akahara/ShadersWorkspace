package wonder.shaderdisplay.serial;

import wonder.shaderdisplay.Main;

import java.io.File;
import java.io.IOException;

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
        if (!mainConfigFile.exists())
            config = new UserConfig();

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

    public Freecam freecam = new Freecam();
    public LayerState[] layers = new LayerState[0];
    public int[] windowLocation = null;

}

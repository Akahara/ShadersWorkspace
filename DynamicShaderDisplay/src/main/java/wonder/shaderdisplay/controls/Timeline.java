package wonder.shaderdisplay.controls;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.serial.InputFiles;
import wonder.shaderdisplay.serial.UserConfig;
import wonder.shaderdisplay.serial.UserConfig.TimeLoopConfig.LoopType;

import java.util.stream.Stream;

public class Timeline {

    private final String[] LOOP_TIME_OPTIONS = Stream.of(LoopType.values()).map(e -> e.displayName).toArray(String[]::new);
    private final ImInt loopTimeType;
    private final float[] loopTimeFrom, loopTimeTo;

    private static boolean shouldRenderTime, shouldRenderFrames;

    public Timeline() {
        UserConfig.TimeLoopConfig config = UserConfig.config.timeLoop;
        float displayConversion = 1.f / getTimeConversionToSeconds(config.loopTime);
        loopTimeType = new ImInt(config.loopTime.ordinal());
        loopTimeFrom = new float[] { config.loopFrom * displayConversion };
        loopTimeTo = new float[] { config.loopTo * displayConversion };
    }

    public void renderControls() {
        LoopType currentLoopType = LoopType.values[loopTimeType.get()];
        float displayTimeConversion = getTimeConversionToSeconds(currentLoopType);
        boolean updatedTimeLoop = false;

        Time.setJustChanged(false);

        // When rendering video, show time controls even if there is no time uniform
        boolean hasVideoInput = InputFiles.singleton != null && InputFiles.singleton.hasInputVideo();
        shouldRenderTime |= hasVideoInput;
        shouldRenderFrames |= hasVideoInput;

        if (!shouldRenderFrames && !shouldRenderTime)
            return;

        if (ImGui.button("Reset time"))
            Time.jumpToTime(currentLoopType == LoopType.NO_LOOP ? 0 : displayTimeConversion * loopTimeFrom[0]);
        ImGui.sameLine();
        if (ImGui.checkbox("Pause time", Time.isPaused()))
            Time.setPaused(!Time.isPaused());

        if (shouldRenderTime) {
            float[] ptr = { Time.getTime() };
            shouldRenderTime = false;
            if(ImGui.dragFloat("Time", ptr, .01f))
                Time.jumpToTime(ptr[0]);
        }
        if (shouldRenderFrames) {
            int[] ptr = { Time.getFrame() };
            shouldRenderFrames = false;
            if(ImGui.dragInt("Frame", ptr))
                Time.jumpToFrame(ptr[0]);
        }

        if (ImGui.combo("Loop time", loopTimeType, LOOP_TIME_OPTIONS)) {
            LoopType newType = LoopType.values[loopTimeType.get()];
            float conversion = getTimeConversionToSeconds(currentLoopType) / getTimeConversionToSeconds(newType);
            loopTimeFrom[0] *= conversion;
            loopTimeTo[0] *= conversion;
            currentLoopType = newType;
            displayTimeConversion = getTimeConversionToSeconds(newType);
            updatedTimeLoop = true;
        }

        if (loopTimeType.get() != LoopType.NO_LOOP.ordinal()) {
            updatedTimeLoop |= ImGui.dragFloatRange2("Loop from/to", loopTimeFrom, loopTimeTo);

            ImVec2 c = ImGui.getCursorScreenPos();
            float padding = ImGui.getStyle().getWindowPaddingX();
            float minX = c.x, maxX = minX + ImGui.getWindowWidth() - padding * 2;
            float minY = c.y - 1, maxY = minY + 20;
            float currentTimeInDisplayUnits = Time.getTime() / displayTimeConversion;
            float currentTimeFrac = (currentTimeInDisplayUnits - loopTimeFrom[0]) / (loopTimeTo[0] - loopTimeFrom[0]);
            float displayTimeX = currentTimeFrac * (maxX - minX) + minX;
            final int backgroundColor = 0xff714324;
            ImGui.pushStyleColor(ImGuiCol.Button, backgroundColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, backgroundColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, backgroundColor);
            ImGui.button("##", maxX - minX, maxY - minY);
            ImGui.popStyleColor(3);
            ImGui.getWindowDrawList().addLine(displayTimeX, minY, displayTimeX, maxY, 0xffb48462);
            if (ImGui.isItemActive()) {
                ImVec2 mouse = ImGui.getMousePos();
                float newTimeFrac = (mouse.x - minX) / (maxX - minX);
                float newTimeInDisplayUnits = newTimeFrac * (loopTimeTo[0] - loopTimeFrom[0]) + loopTimeFrom[0];
                Time.jumpToTime(newTimeInDisplayUnits * displayTimeConversion);
            }
        }

        if (updatedTimeLoop) {
            UserConfig.TimeLoopConfig config = UserConfig.config.timeLoop;
            config.loopTime = currentLoopType;
            config.loopFrom = loopTimeFrom[0] * displayTimeConversion;
            config.loopTo = loopTimeTo[0] * displayTimeConversion;
        }
    }

    public float getLoopBegin() {
        LoopType currentLoopType = LoopType.values[loopTimeType.get()];
        if (currentLoopType == LoopType.NO_LOOP)
            return 0;
        float displayTimeConversion = getTimeConversionToSeconds(currentLoopType);
        return loopTimeFrom[0] / displayTimeConversion;
    }

    public static void renderTimeControls() {
        shouldRenderTime = true;
    }

    public static void renderFrameControls() {
        shouldRenderFrames = true;
    }

    private float getTimeConversionToSeconds(LoopType unit) {
        return switch (unit) {
            case NO_LOOP, LOOP_TIME -> 1.f;
            case LOOP_FRAME -> 1.f / Time.getFramerate();
        };
    }

}

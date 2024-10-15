package wonder.shaderdisplay;

import imgui.ImGui;
import wonder.shaderdisplay.serial.InputFiles;

public class Time {
	
	private static float fps;
	private static float time;
	private static boolean paused;
	private static boolean justChanged;

	private static String frameUniformName, timeUniformName;
	
	private static boolean shouldRenderTime, shouldRenderFrames;
	
	public static float getTime() {
		return time;
	}
	
	public static int getFrame() {
		return (int) (time * fps);
	}

	public static float getFramerate() {
		return fps;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static boolean justChanged() {
		return justChanged;
	}

	public static void setTime(float time) {
		Time.time = time;
	}
	
	public static void setFrame(int frame) {
		time = (frame+.5f) / fps;
	}

	public static void jumpToTime(float time) {
		setTime(time);
		justChanged = true;
	}

	public static void jumpToFrame(int frame) {
		setFrame(frame);
		justChanged = true;
	}

	public static void setFps(float fps) {
		Time.fps = fps;
	}
	
	public static void step(float realDelta) {
		if (!paused)
			time += realDelta;
	}

	public static void stepFrame(int frameCount) {
		if (!paused)
			setFrame(getFrame()+frameCount);
	}
	
	public static void renderTimeControls(String uniformName) {
		shouldRenderTime = true;
		timeUniformName = uniformName;
	}
	
	public static void renderFrameControls(String uniformName) {
		shouldRenderFrames = true;
		frameUniformName = uniformName;
	}

	public static void renderControls() {
		justChanged = false;

		// When rendering video, show time controls even if there is no time uniform
		boolean hasVideoInput = InputFiles.singleton != null && InputFiles.singleton.hasInputVideo();
		shouldRenderTime |= hasVideoInput;
		shouldRenderFrames |= hasVideoInput;

		if(!shouldRenderFrames && !shouldRenderTime)
			return;
		
		if(ImGui.button("Reset iTime"))
			jumpToFrame(0);
		ImGui.sameLine();
		if(ImGui.checkbox("Pause iTime", paused))
			paused = !paused;
		
		if(!paused) ImGui.beginDisabled();
		
		if(shouldRenderTime) {
			float[] ptr = { time };
			shouldRenderTime = false;
			if(ImGui.dragFloat(timeUniformName == null ? "Time" : timeUniformName, ptr, .01f))
				jumpToTime(ptr[0]);
		}
		if(shouldRenderFrames) {
			int[] ptr = { getFrame() };
			shouldRenderFrames = false;
			if(ImGui.dragInt(frameUniformName == null ? "Frame" : frameUniformName, ptr)) {
				setFrame(ptr[0]);
				justChanged = true;
			}
		}

		if(!paused) ImGui.endDisabled();
	}

}

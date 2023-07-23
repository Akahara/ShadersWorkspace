package wonder.shaderdisplay;

import imgui.ImGui;

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

	public static void setFps(float fps) {
		Time.fps = fps;
	}
	
	public static void step(float realDelta) {
		if(!paused)
			time += realDelta;
	}

	public static void stepFrame(int frameCount) {
		if(!paused)
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
		if(!shouldRenderFrames && !shouldRenderTime)
			return;
		
		if(ImGui.button("Reset iTime"))
			time = 0;
		ImGui.sameLine();
		if(ImGui.checkbox("Pause iTime", paused))
			paused = !paused;
		
		if(!paused) ImGui.beginDisabled();
		
		if(shouldRenderTime) {
			float[] ptr = { time };
			shouldRenderTime = false;
			if(ImGui.dragFloat(timeUniformName, ptr, .01f)) {
				setTime(ptr[0]);
				justChanged = true;
			}
		}
		if(shouldRenderFrames) {
			int[] ptr = { getFrame() };
			shouldRenderFrames = false;
			if(ImGui.dragInt(frameUniformName, ptr)) {
				setFrame(ptr[0]);
				justChanged = true;
			}
		}

		if(!paused) ImGui.endDisabled();
	}

}

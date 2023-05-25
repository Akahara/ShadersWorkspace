package wonder.shaderdisplay;

import imgui.ImGui;

public class Time {
	
	private static float fps;
	private static float time;
	private static boolean paused;
	
	private static boolean shouldRenderTime, shouldRenderFrames;
	
	public static float getTime() {
		return time;
	}
	
	public static int getFrame() {
		return (int) (time * fps);
	}
	
	public static void setPaused(boolean paused) {
		Time.paused = paused;
	}
	
	public static boolean isPaused() {
		return paused;
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
	
	public static void renderTimeControls() {
		shouldRenderTime = true;
	}
	
	public static void renderFrameControls() {
		shouldRenderFrames = true;
	}

	public static void renderControls() {
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
			ImGui.dragFloat("iTime", ptr, .01f);
			time = ptr[0];
			shouldRenderTime = false;
		}
		if(shouldRenderFrames) {
			int[] ptr = { getFrame() };
			ImGui.dragInt("iFrame", ptr);
			setFrame(getFrame());
			shouldRenderFrames = false;
		}
		
		if(!paused) ImGui.endDisabled();
	}

}

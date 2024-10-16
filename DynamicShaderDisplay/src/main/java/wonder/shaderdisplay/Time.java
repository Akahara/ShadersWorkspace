package wonder.shaderdisplay;

import wonder.shaderdisplay.serial.UserConfig;

public class Time {
	
	private static float fps;
	private static float time;
	private static boolean paused;
	private static boolean justChanged;
	
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

	public static void setPaused(boolean paused) {
		Time.paused = paused;
	}

	public static boolean justChanged() {
		return justChanged;
	}

	public static void setJustChanged(boolean justChanged) {
		Time.justChanged = justChanged;
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

	public static void applyTimeLoop(UserConfig.TimeLoopConfig timeLoop) {
		if (timeLoop.loopTime == UserConfig.TimeLoopConfig.LoopType.NO_LOOP || timeLoop.loopFrom >= timeLoop.loopTo)
			return;
		float newTime = MathUtils.wrapInBounds(time, timeLoop.loopFrom, timeLoop.loopTo);
		if (newTime != time)
			jumpToTime(newTime);
	}
}

package com.lapissea.glfw;

import java.util.List;

/**
 * Window geometry for borderless full screen on Windows.
 * <p>
 * A window that exactly covers a monitor is promoted to exclusive full screen by some GPU drivers, what can cause issues on Windows by
 * effectively making borderless mode the same as exclusive.<br/>
 * Growing the window by one pixel past an edge with no neighbor monitor avoids this. The extra pixel is invisible. <br/>
 * Bottom and right edges are preferred since they keep the window's coordinates.
 */
record BorderlessCover(int x, int y, int width, int height, int visibleX, int visibleY){
	
	enum Side{BOTTOM, RIGHT, TOP, LEFT, NONE}
	
	static Side freeSide(GlfwMonitor.Rect monitor, List<GlfwMonitor.Rect> others){
		int x = monitor.x, y = monitor.y, w = monitor.width, h = monitor.height;
		if(isFree(x, y + h, w, 1, others)) return Side.BOTTOM;
		if(isFree(x + w, y, 1, h, others)) return Side.RIGHT;
		if(isFree(x, y - 1, w, 1, others)) return Side.TOP;
		if(isFree(x - 1, y, 1, h, others)) return Side.LEFT;
		return Side.NONE;
	}
	private static boolean isFree(int x, int y, int w, int h, List<GlfwMonitor.Rect> others){
		for(var o : others){
			if(x<o.x + o.width && o.x<x + w && y<o.y + o.height && o.y<y + h) return false;
		}
		return true;
	}
	
	static BorderlessCover of(GlfwMonitor.Rect monitor, List<GlfwMonitor.Rect> others){
		int x = monitor.x, y = monitor.y, w = monitor.width, h = monitor.height;
		return switch(freeSide(monitor, others)){
			case BOTTOM -> new BorderlessCover(x, y, w, h + 1, 0, 0);
			case RIGHT -> new BorderlessCover(x, y, w + 1, h, 0, 0);
			case TOP -> new BorderlessCover(x, y - 1, w, h + 1, 0, 1);
			case LEFT -> new BorderlessCover(x - 1, y, w + 1, h, 1, 0);
			// Nowhere to put the pixel and the tweak is disabled. Honestly, what kind of messed up display set up do you have if you hit this?
			case NONE -> new BorderlessCover(x, y, w, h, 0, 0);
		};
	}
	
	static BorderlessCover of(GlfwMonitor monitor){
		var others = GlfwMonitor.getMonitors().stream().filter(m -> !m.equals(monitor)).map(m -> m.bounds).toList();
		return of(monitor.bounds, others);
	}
}

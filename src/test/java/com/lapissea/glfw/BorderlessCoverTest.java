package com.lapissea.glfw;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class BorderlessCoverTest{

	private static final GlfwMonitor.Rect MAIN = new GlfwMonitor.Rect(0, 0, 2560, 1440);

	private static BorderlessCover cover(GlfwMonitor.Rect... others){
		return BorderlessCover.of(MAIN, List.of(others));
	}

	@Test
	public void growsDownWhenNothingIsBelow(){
		assertEquals(cover(), new BorderlessCover(0, 0, 2560, 1441, 0, 0), "Single monitor");
		// Side by side, also offset by a few pixels vertically like real setups.
		assertEquals(cover(new GlfwMonitor.Rect(2560, 3, 2560, 1440)), new BorderlessCover(0, 0, 2560, 1441, 0, 0));
	}

	@Test
	public void growsRightWhenAMonitorIsBelow(){
		assertEquals(cover(new GlfwMonitor.Rect(0, 1440, 1920, 1080)), new BorderlessCover(0, 0, 2561, 1440, 0, 0));
	}

	@Test
	public void growsUpOrLeftOnlyWhenBottomAndRightAreTaken(){
		var below = new GlfwMonitor.Rect(0, 1440, 2560, 1440);
		var right = new GlfwMonitor.Rect(2560, 0, 2560, 1440);
		assertEquals(cover(below, right), new BorderlessCover(0, -1, 2560, 1441, 0, 1), "Top, the monitor area starts a row in");
		var above = new GlfwMonitor.Rect(0, -1440, 2560, 1440);
		assertEquals(cover(below, right, above), new BorderlessCover(-1, 0, 2561, 1440, 1, 0), "Left");
	}

	@Test
	public void coversExactlyWhenSurrounded(){
		var around = new GlfwMonitor.Rect[]{
			new GlfwMonitor.Rect(0, 1440, 2560, 1440), new GlfwMonitor.Rect(2560, 0, 2560, 1440),
			new GlfwMonitor.Rect(0, -1440, 2560, 1440), new GlfwMonitor.Rect(-2560, 0, 2560, 1440)};
		assertEquals(cover(around), new BorderlessCover(0, 0, 2560, 1440, 0, 0));
	}

	@Test
	public void cornerNeighborsDoNotBlockASide(){
		// Only touches the bottom right corner, the strip below the monitor is still free.
		assertEquals(cover(new GlfwMonitor.Rect(2560, 1440, 1920, 1080)), new BorderlessCover(0, 0, 2560, 1441, 0, 0));
	}
}

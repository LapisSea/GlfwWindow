package com.lapissea.glfw;

import org.testng.annotations.Test;

import java.nio.file.Files;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class WindowStateTest{
	
	@Test
	public void roundTripsEveryMode(){
		var states = new WindowState[]{
			new WindowState(WindowState.Mode.NORMAL, new Point(-40, 12), new Size(800, 600), null),
			new WindowState(WindowState.Mode.MAXIMIZED, new Point(100, 200), new Size(1024, 768), null),
			new WindowState(WindowState.Mode.NORMAL, null, new Size(640, 480), null),
			new WindowState(WindowState.Mode.FULL_SCREEN, new Point(5, 6), new Size(300, 200),
			                new WindowState.MonitorRef("DP-1", 1920, 0, 2560, 1440)),
			new WindowState(WindowState.Mode.FULL_SCREEN, null, new Size(300, 200),
			                new WindowState.MonitorRef(null, 0, 0, 1920, 1080)),
			new WindowState(WindowState.Mode.BORDERLESS_FULL_SCREEN, new Point(7, 8), new Size(300, 200),
			                new WindowState.MonitorRef("HDMI-1", 0, 0, 1920, 1080)),
			};
		for(var state : states){
			assertEquals(WindowState.fromJson(state.toJson()), state);
		}
	}
	
	@Test
	public void rejectsGarbage(){
		assertThrows(IllegalArgumentException.class, () -> WindowState.fromJson("{not json"));
		assertThrows(IllegalArgumentException.class, () -> WindowState.fromJson("{\"version\":2}"));
		assertThrows(IllegalArgumentException.class, () -> WindowState.fromJson("[]"));
	}
	
	@Test
	public void savesAtomicallyAndLoadsBack() throws Exception{
		var dir  = Files.createTempDirectory("window-state");
		var file = dir.resolve("state.json");
		try{
			assertTrue(WindowState.load(file).isEmpty(), "Missing file is no state");
			var state = new WindowState(WindowState.Mode.NORMAL, new Point(1, 2), new Size(3, 4), null);
			state.save(file);
			assertEquals(WindowState.load(file).orElseThrow(), state);
			try(var files = Files.list(dir)){
				assertEquals(files.count(), 1L, "No temporary file left behind");
			}
			Files.writeString(file, "broken");
			assertTrue(WindowState.load(file).isEmpty(), "Unreadable file is no state");
		}finally{
			try(var files = Files.list(dir)){ for(var f : files.toList()) Files.delete(f); }
			Files.delete(dir);
		}
	}
	
	@Test
	public void fullScreenNeedsAMonitor(){
		assertThrows(NullPointerException.class, () -> new WindowState(WindowState.Mode.FULL_SCREEN, null, new Size(1, 1), null));
		assertNull(new WindowState(WindowState.Mode.NORMAL, null, new Size(1, 1), null).monitor());
	}
}

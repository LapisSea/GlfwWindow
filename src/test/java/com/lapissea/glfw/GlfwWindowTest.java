package com.lapissea.glfw;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Needs a display. Opens short lived windows. Run with -Dglfw.platform=x11 or wayland to pick the GLFW platform.
 */
public class GlfwWindowTest{

	@BeforeClass
	public static void initGlfw(){
		switch(System.getProperty("glfw.platform", "")){
			case "x11" -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
			case "wayland" -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
			case "win32" -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WIN32);
			default -> { }
		}
		try{
			GlfwWindow.initGLFW();
			if(GlfwMonitor.getMonitors().isEmpty()) throw new SkipException("No monitor");
		}catch(SkipException e){
			throw e;
		}catch(Throwable e){
			throw new SkipException("No display: " + e);
		}
	}

	private static GlfwWindow window(int width, int height){
		var w = new GlfwWindow();
		w.setTitle("glfw-window test");
		w.setSize(width, height);
		w.show();
		return w;
	}
	private static void create(GlfwWindow w){
		w.init(i -> i.withVulkan());
	}

	private static void pollUntil(BooleanSupplier condition, String what){
		long deadline = System.nanoTime() + 3_000_000_000L;
		while(!condition.getAsBoolean()){
			if(System.nanoTime()>deadline) fail("Timed out waiting for: " + what);
			glfwPollEvents();
			LockSupport.parkNanos(5_000_000);
		}
	}
	private static void settle(long millis){
		long end = System.nanoTime() + millis*1_000_000;
		while(System.nanoTime()<end){
			glfwPollEvents();
			LockSupport.parkNanos(5_000_000);
		}
	}

	@Test
	public void requestsBeforeCreationConfigureTheWindow(){
		var w = window(321, 241);
		w.setTitle("configured");
		assertFalse(w.isCreated());
		assertEquals(w.size(), new Size(321, 241));
		create(w);
		try{
			assertEquals(w.title(), "configured");
			pollUntil(w::isVisible, "visible");
			assertEquals(w.size(), new Size(321, 241));
		}finally{ w.destroy(); }
	}

	@Test
	public void resizingSettlesWithoutEchoes(){
		var w = window(320, 240);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			settle(200);
			var events = new ArrayList<Size>();
			var reg    = w.callbacks().windowSize.add((h, width, height) -> events.add(new Size(width, height)));

			w.setSize(400, 300);
			pollUntil(() -> w.size().equals(new Size(400, 300)), "resized");
			settle(300);
			assertEquals(w.size(), new Size(400, 300));
			assertTrue(events.size()<=3, "Resize settles instead of bouncing: " + events);
			reg.remove();
		}finally{ w.destroy(); }
	}

	@Test
	public void stateIsCurrentInsideListeners(){
		var w = window(320, 240);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			var mismatches = new ArrayList<String>();
			w.callbacks().windowSize.add((h, width, height) -> {
				if(!w.size().equals(new Size(width, height))) mismatches.add(w.size() + " != " + width + "x" + height);
			});
			w.setSize(410, 310);
			pollUntil(() -> w.size().equals(new Size(410, 310)), "resized");
			assertTrue(mismatches.isEmpty(), mismatches.toString());
		}finally{ w.destroy(); }
	}

	@Test
	public void removedListenersStopReceiving(){
		var w = window(320, 240);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			List<Size> events = new ArrayList<>();
			try(var reg = w.callbacks().windowSize.add((h, width, height) -> events.add(new Size(width, height)))){
				w.setSize(330, 250);
				pollUntil(() -> !events.isEmpty(), "first resize event");
			}
			int seen = events.size();
			w.setSize(340, 260);
			pollUntil(() -> w.size().equals(new Size(340, 260)), "second resize");
			assertEquals(events.size(), seen);
		}finally{ w.destroy(); }
	}

	@Test
	public void normalGeometrySurvivesMaximize(){
		var w = window(330, 250);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			settle(200);
			var normalPos = w.pos();
			w.maximize();
			pollUntil(w::isMaximized, "maximized");
			settle(200);
			assertTrue(w.size().width()>330, "Actually maximized: " + w.size());

			var state = w.captureState();
			assertEquals(state.mode(), WindowState.Mode.MAXIMIZED);
			assertEquals(state.normalSize(), new Size(330, 250));
			assertEquals(state.normalPos(), normalPos, "Position to restore to");

			w.restore();
			pollUntil(() -> !w.isMaximized() && w.size().equals(new Size(330, 250)), "restored to " + state.normalSize() + ", is " + w.size());
		}finally{ w.destroy(); }
	}

	@Test
	public void minimizedMaximizedWindowSavesAsMaximized(){
		if(glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) throw new SkipException("Wayland does not report minimizing");
		var w = window(330, 250);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			settle(200);
			var normalPos = w.pos();
			w.maximize();
			pollUntil(() -> w.isMaximized() && w.size().width()>330, "maximized");
			settle(200);
			w.iconify();
			pollUntil(w::isIconified, "minimized");
			settle(200);

			var state = w.captureState();
			assertEquals(state.mode(), WindowState.Mode.MAXIMIZED);
			assertEquals(state.normalSize(), new Size(330, 250));
			assertEquals(state.normalPos(), normalPos, "Position to restore to");
		}finally{ w.destroy(); }
	}

	private static boolean covers(GlfwWindow w, GlfwMonitor monitor){
		var b = monitor.bounds;
		if(!w.isFullScreen()) return false;
		// Wayland sizes are in logical units and positions are unknown.
		if(!GlfwWindow.hasPositions()) return w.size().width()>400;
		// Window managers report a full screen window's position late, the size is what matters there.
		if(glfwGetPlatform() != GLFW_PLATFORM_WIN32) return new Size(b.width, b.height).equals(w.size());
		// Borderless reaches a pixel past the monitor, its visible area is what lines up with it.
		var a = w.visibleArea();
		var p = w.pos();
		return p != null && p.x() + a.x() == b.x && p.y() + a.y() == b.y && a.width() == b.width && a.height() == b.height;
	}
	
	@Test
	public void borderlessFullScreenCoversTheMonitorAndReturns(){
		var w = window(330, 250);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			settle(200);
			var normalPos = w.pos();
			var monitor   = w.bestMonitor();
			assertFalse(w.borderlessOversize(), "Opt in");

			w.setFullScreen(monitor, GlfwWindow.FullScreenStyle.BORDERLESS);
			pollUntil(() -> covers(w, monitor), "covering " + monitor + ", is " + w.pos() + " " + w.size());
			assertEquals(w.fullScreenStyle(), GlfwWindow.FullScreenStyle.BORDERLESS);
			var fullFramebuffer = new Area(0, 0, w.framebufferSize().width(), w.framebufferSize().height());
			assertEquals(w.visibleArea(), fullFramebuffer, "Covers exactly unless asked for the oversize");
			if(glfwGetPlatform() == GLFW_PLATFORM_WIN32){
				assertEquals(glfwGetWindowAttrib(w.getHandle(), GLFW_DECORATED), GLFW_FALSE, "No frame");
				assertEquals(w.size(), new Size(monitor.bounds.width, monitor.bounds.height));
			}
			
			// Applies to a window that already is borderless.
			w.setBorderlessOversize(true);
			pollUntil(() -> covers(w, monitor), "oversized, covering " + monitor + ", is " + w.pos() + " " + w.size());
			if(glfwGetPlatform() == GLFW_PLATFORM_WIN32){
				var cover = BorderlessCover.of(monitor);
				pollUntil(() -> w.size().equals(new Size(cover.width(), cover.height())), "a pixel past a free edge, is " + w.size());
				assertEquals(w.pos(), new Point(cover.x(), cover.y()));
			}else{
				assertEquals(w.visibleArea(), new Area(0, 0, w.framebufferSize().width(), w.framebufferSize().height()), "Only Windows oversizes");
			}
			w.setBorderlessOversize(false);
			pollUntil(() -> covers(w, monitor) && w.visibleArea().equals(new Area(0, 0, w.framebufferSize().width(), w.framebufferSize().height())),
			          "exact again, is " + w.pos() + " " + w.size());
			
			var state = w.captureState();
			assertEquals(state.mode(), WindowState.Mode.BORDERLESS_FULL_SCREEN);
			assertEquals(state.normalSize(), new Size(330, 250));
			assertEquals(state.monitor(), WindowState.MonitorRef.of(monitor));
			
			w.setFullScreen(null);
			pollUntil(() -> !w.isFullScreen() && w.size().equals(new Size(330, 250)), "back to normal, is " + w.size());
			if(normalPos != null) pollUntil(() -> normalPos.equals(w.pos()), "back at " + normalPos + ", is " + w.pos());
			assertEquals(glfwGetWindowAttrib(w.getHandle(), GLFW_DECORATED), GLFW_TRUE, "Frame is back");
		}finally{ w.destroy(); }
	}
	
	@Test
	public void maximizedWindowReturnsMaximizedFromFullScreen(){
		for(var style : GlfwWindow.FullScreenStyle.values()){
			var w = window(330, 250);
			create(w);
			try{
				pollUntil(w::isVisible, "visible");
				settle(200);
				var normalPos = w.pos();
				w.maximize();
				pollUntil(() -> w.isMaximized() && w.size().width()>330, style + ": maximized");
				settle(200);
				
				var monitor = w.bestMonitor();
				w.setFullScreen(monitor, style);
				pollUntil(() -> covers(w, monitor), style + ": full screen, is " + w.pos() + " " + w.size());
				settle(200);
				w.setFullScreen(null);
				pollUntil(() -> !w.isFullScreen() && w.isMaximized() && w.size().width()>330, style + ": maximized again, is " + w.size());
				settle(200);
				
				var state = w.captureState();
				assertEquals(state.mode(), WindowState.Mode.MAXIMIZED, style.toString());
				assertEquals(state.normalSize(), new Size(330, 250), style + ": normal size kept");
				w.restore();
				pollUntil(() -> !w.isMaximized() && w.size().equals(new Size(330, 250)), style + ": restored to the normal size, is " + w.size());
				if(normalPos != null) pollUntil(() -> normalPos.equals(w.pos()), style + ": restored to " + normalPos + ", is " + w.pos());
			}finally{ w.destroy(); }
		}
	}
	
	@Test
	public void switchingStyleWhileFullScreen(){
		var w = window(330, 250);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			settle(200);
			var monitor = w.bestMonitor();
			w.setFullScreen(monitor, GlfwWindow.FullScreenStyle.EXCLUSIVE);
			pollUntil(() -> covers(w, monitor), "exclusive, is " + w.pos() + " " + w.size());
			settle(200);
			
			w.setFullScreenStyle(GlfwWindow.FullScreenStyle.BORDERLESS);
			pollUntil(() -> covers(w, monitor), "borderless, is " + w.pos() + " " + w.size());
			assertEquals(w.fullScreenStyle(), GlfwWindow.FullScreenStyle.BORDERLESS);
			if(glfwGetPlatform() == GLFW_PLATFORM_WIN32){
				assertEquals(glfwGetWindowMonitor(w.getHandle()), 0L, "Borderless does not own the monitor");
				assertEquals(glfwGetWindowAttrib(w.getHandle(), GLFW_DECORATED), GLFW_FALSE, "No frame");
			}
			settle(200);
			
			w.setFullScreenStyle(GlfwWindow.FullScreenStyle.EXCLUSIVE);
			pollUntil(() -> covers(w, monitor), "exclusive again, is " + w.pos() + " " + w.size());
			assertEquals(w.fullScreenStyle(), GlfwWindow.FullScreenStyle.EXCLUSIVE);
			assertTrue(glfwGetWindowMonitor(w.getHandle()) != 0L, "Exclusive owns the monitor");
			
			w.setFullScreen(null);
			pollUntil(() -> !w.isFullScreen() && w.size().equals(new Size(330, 250)), "back to normal, is " + w.size());
			assertEquals(glfwGetWindowAttrib(w.getHandle(), GLFW_DECORATED), GLFW_TRUE, "Frame is back");
		}finally{ w.destroy(); }
	}
	
	@Test
	public void savedBorderlessStateRestoresOnCreation(){
		var monitor = GlfwMonitor.getPrimaryMonitor();
		var w       = window(100, 100);
		w.applyState(new WindowState(WindowState.Mode.BORDERLESS_FULL_SCREEN, null, new Size(340, 260), WindowState.MonitorRef.of(monitor)));
		create(w);
		try{
			pollUntil(() -> covers(w, monitor), "created covering " + monitor + ", is " + w.pos() + " " + w.size());
			assertEquals(w.fullScreenStyle(), GlfwWindow.FullScreenStyle.BORDERLESS);
			assertEquals(w.defaultFullScreenStyle(), GlfwWindow.FullScreenStyle.BORDERLESS, "F11 keeps the restored style");
			w.setFullScreen(null);
			pollUntil(() -> !w.isFullScreen() && w.size().equals(new Size(340, 260)), "left to the saved normal size, is " + w.size());
		}finally{ w.destroy(); }
	}
	
	@Test
	public void savedMaximizedStateRestoresOnCreation(){
		var w       = window(100, 100);
		var monitor = GlfwMonitor.getPrimaryMonitor().bounds;
		var saved   = GlfwWindow.hasPositions()? new Point(monitor.x + 120, monitor.y + 140) : null;
		w.applyState(new WindowState(WindowState.Mode.MAXIMIZED, saved, new Size(340, 260), null));
		create(w);
		try{
			// X11 marks the window maximized on creation, before the window manager has made it so.
			pollUntil(() -> w.isMaximized() && w.size().width()>340, "created maximized");
			settle(200);
			assertTrue(w.size().width()>340, "Maximized on the saved monitor, not placed at the saved position: " + w.pos() + " " + w.size());
			w.restore();
			pollUntil(() -> !w.isMaximized() && w.size().equals(new Size(340, 260)), "restored to the saved normal size, is " + w.size());
			if(saved != null){
				pollUntil(() -> saved.equals(w.pos()), "restored to the saved position " + saved + ", is " + w.pos());
			}
		}finally{ w.destroy(); }
	}

	@Test
	public void autoSaveWritesTheLastPlacementOnDestroy() throws Exception{
		var dir  = Files.createTempDirectory("glfw-state");
		var file = dir.resolve("window.json");
		try{
			var w = window(320, 240);
			w.autoSaveState(file);
			create(w);
			pollUntil(w::isVisible, "visible");
			w.setSize(350, 270);
			pollUntil(() -> w.size().equals(new Size(350, 270)), "resized");
			w.destroy();

			var saved = WindowState.load(file).orElseThrow();
			assertEquals(saved.mode(), WindowState.Mode.NORMAL);
			assertEquals(saved.normalSize(), new Size(350, 270));

			// A new window picks the saved placement up before it is shown.
			var again = window(100, 100);
			again.autoSaveState(file);
			assertEquals(again.size(), new Size(350, 270));
			create(again);
			try{
				pollUntil(again::isVisible, "visible");
				assertEquals(again.size(), new Size(350, 270));
				if(saved.normalPos() != null){
					// Not shifted by the title bar, which would make the window creep every run.
					pollUntil(() -> saved.normalPos().equals(again.pos()), "reopened at " + saved.normalPos() + ", is " + again.pos());
				}
			}finally{ again.destroy(); }
		}finally{
			try(var files = Files.list(dir)){ for(var f : files.toList()) Files.delete(f); }
			Files.delete(dir);
		}
	}

	@Test
	public void higherLevelEventsFire(){
		var w = window(320, 240);
		create(w);
		try{
			pollUntil(w::isVisible, "visible");
			var placement = new int[1];
			w.onPlacementChange(() -> placement[0]++);
			w.setSize(360, 280);
			pollUntil(() -> placement[0]>0, "placement change");
		}finally{ w.destroy(); }
	}
}

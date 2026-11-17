package com.lapissea.glfw.demo;

import com.lapissea.glfw.Area;
import com.lapissea.glfw.BuffUtil;
import com.lapissea.glfw.GlfwKeyboardEvent;
import com.lapissea.glfw.GlfwWindow;
import com.lapissea.glfw.Size;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Shows what the window knows about its placement and what it saves, live.
 * <p>
 * Run: {@code mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.lapissea.glfw.demo.StateDemo}
 * <br>Optional arguments: the state file (default {@code glfw-demo-state.json}), {@code x11}/{@code wayland} to pick the
 * platform, {@code --capture=out.png} to save the first settled frame and quit.
 */
public final class StateDemo{
	
	private static final Color BACKGROUND = new Color(0x1E2228);
	private static final Color TEXT       = new Color(0xD8DEE9);
	private static final Color DIM        = new Color(0x7B8394);
	private static final Color ACCENT     = new Color(0x88C0D0);
	
	private record Screen(List<String> left, List<String> right, List<String> footer){ }
	
	public static void main(String[] args) throws IOException{
		var  file    = Path.of("glfw-demo-state.json");
		Path capture = null;
		for(var arg : args){
			if(arg.startsWith("--capture=")){
				capture = Path.of(arg.substring("--capture=".length()));
				continue;
			}
			switch(arg){
				case "x11" -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
				case "wayland" -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
				default -> file = Path.of(arg);
			}
		}
		
		var win = new GlfwWindow();
		win.setTitle("glfw-window state demo");
		
		float scale = defaultSizeScale();
		win.setSize(Math.round(760*scale), Math.round(480*scale));
		
		win.autoSaveState(file, Duration.ofMillis(500));
		win.autoF11Toggle();
		
		// Some GPU drivers still switch the display mode for a borderless window, this keeps them from noticing it.
		win.onKey(GLFW_KEY_O, GlfwKeyboardEvent.Type.DOWN, e -> win.setBorderlessOversize(!win.borderlessOversize()));
		win.onKey(GLFW_KEY_ESCAPE, GlfwKeyboardEvent.Type.DOWN, e -> win.requestClose());
		win.onKey(GLFW_KEY_M, GlfwKeyboardEvent.Type.DOWN, e -> {
			if(win.isMaximized()) win.restore();
			else win.maximize();
		});
		win.onKey(GLFW_KEY_I, GlfwKeyboardEvent.Type.DOWN, e -> win.iconify());
		win.onKey(GLFW_KEY_B, GlfwKeyboardEvent.Type.DOWN, e -> {
			win.setFullScreenStyle(
				win.defaultFullScreenStyle() == GlfwWindow.FullScreenStyle.EXCLUSIVE?
				GlfwWindow.FullScreenStyle.BORDERLESS :
				GlfwWindow.FullScreenStyle.EXCLUSIVE
			);
		});
		win.show();
		win.init(
			i -> i.withOpenGL(
				gl -> gl.withVersion(2, 1)
				        .withProfile(GlfwWindow.Initializer.OpenGLSurfaceAPI.Profile.ANY)
			).doubleBuffer(true)
		);
		glfwSwapInterval(1);
		
		int    texture   = glGenTextures();
		Screen drawn     = null;
		Size   drawnSize = null;
		long   start     = System.nanoTime();
		while(!win.shouldClose()){
			glfwWaitEventsTimeout(0.2);
			
			var fb = win.framebufferSize();
			if(fb.isEmpty()) continue;
			var area    = win.visibleArea();
			var visible = new Size(area.width(), area.height());
			var screen  = describe(win, file);
			if(!screen.equals(drawn) || !visible.equals(drawnSize)){
				upload(texture, render(screen, visible, contentScale(win)), visible);
				drawn = screen;
				drawnSize = visible;
			}
			draw(texture, fb, area);
			if(capture != null && System.nanoTime() - start>1_500_000_000L){
				save(capture, fb);
				win.requestClose();
			}
			win.swapBuffers();
		}
		win.destroy();
		glfwTerminate();
	}
	
	private static Screen describe(GlfwWindow win, Path file){
		var live = new ArrayList<String>();
		live.add("#glfw-window state demo");
		live.add("~platform  " + platformName());
		live.add("");
		
		String mode;
		if(win.isFullScreen()) mode = "Full screen (" + win.fullScreenStyle().name().toLowerCase() + ") on " + win.fullScreenMonitor().name;
		else if(win.isIconified()) mode = "Minimized";
		else if(win.isMaximized()) mode = "Maximized";
		else if(win.isSnapped()) mode = "Snapped";
		else mode = "Normal";
		live.add("!" + mode);
		live.add("position   " + (win.pos() == null? "hidden by platform" : win.pos().x() + ", " + win.pos().y()));
		live.add("size       " + sizeText(win.size()));
		live.add("pixels     " + sizeText(win.framebufferSize()));
		live.add("focused    " + win.isFocused());
		live.add("F11 style  " + win.defaultFullScreenStyle().name().toLowerCase() + (win.borderlessOversize()? " (oversize)" : ""));
		var area = win.visibleArea();
		live.add("visible    " + area.width() + " x " + area.height() + (area.x() != 0 || area.y() != 0? " at " + area.x() + ", " + area.y() : ""));
		live.add("");
		
		var state = win.captureState();
		live.add("#Restores to");
		live.add("mode       " + state.mode());
		live.add("position   " + (state.normalPos() == null? "-" : state.normalPos().x() + ", " + state.normalPos().y()));
		live.add("size       " + sizeText(state.normalSize()));
		
		var saved = new ArrayList<String>();
		saved.add("#Saved in " + file.getFileName());
		try{
			if(Files.exists(file)) Files.readAllLines(file).forEach(l -> saved.add("~" + l));
			else{
				saved.add("~(not written yet)");
				saved.add("~Move or resize the window.");
			}
		}catch(IOException e){
			saved.add("~" + e);
		}
		
		var footer = List.of("~F11 full screen   B exclusive/borderless   O borderless oversize   M maximize/restore   I minimize   Esc quit",
		                     "~Close and reopen: the window comes back where it was.");
		return new Screen(live, saved, footer);
	}
	
	private static float defaultSizeScale(){
		var platform = glfwGetPlatform();
		if(platform != GLFW_PLATFORM_WIN32 && platform != GLFW_PLATFORM_X11) return 1;
		long monitor = glfwGetPrimaryMonitor();
		if(monitor == 0) return 1;
		float[] x = {1}, y = {1};
		glfwGetMonitorContentScale(monitor, x, y);
		return Math.max(1, x[0]);
	}
	
	private static String sizeText(Size s){ return s.width() + " x " + s.height(); }
	
	private static String platformName(){
		return switch(glfwGetPlatform()){
			case GLFW_PLATFORM_WIN32 -> "Windows";
			case GLFW_PLATFORM_COCOA -> "macOS";
			case GLFW_PLATFORM_X11 -> "X11";
			case GLFW_PLATFORM_WAYLAND -> "Wayland";
			default -> "other";
		};
	}
	
	private static float contentScale(GlfwWindow win){
		var     size = win.size();
		var     fb   = win.framebufferSize();
		float[] x    = {1}, y = {1};
		glfwGetWindowContentScale(win.getHandle(), x, y);
		float byFramebuffer = size.width()>0? fb.width()/(float)size.width() : 1;
		return Math.max(x[0], byFramebuffer);
	}
	
	private static BufferedImage render(Screen screen, Size fb, float scale){
		var image = new BufferedImage(fb.width(), fb.height(), BufferedImage.TYPE_INT_ARGB);
		var g     = image.createGraphics();
		try{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, fb.width(), fb.height());
			
			var plain  = new Font(Font.MONOSPACED, Font.PLAIN, Math.round(15*scale));
			var fonts  = new Font[]{plain, plain.deriveFont(Font.BOLD), plain.deriveFont(Font.BOLD, 26*scale)};
			int margin = Math.round(18*scale);
			
			// Side by side when there is room, the saved file below the live state otherwise.
			int liveEnd = drawLines(g, screen.left(), margin, margin, fonts, scale), contentEnd;
			if(fb.width()>=Math.round(640*scale)){
				contentEnd = Math.max(liveEnd, drawLines(g, screen.right(), fb.width()/2, margin, fonts, scale));
			}else{
				contentEnd = drawLines(g, screen.right(), margin, liveEnd + margin, fonts, scale);
			}
			
			// At the bottom, or below the content when the window is too small for both.
			g.setFont(plain);
			int footerHeight = (g.getFontMetrics().getHeight() + Math.round(2*scale))*screen.footer().size();
			drawLines(g, screen.footer(), margin, Math.max(contentEnd + margin, fb.height() - margin - footerHeight), fonts, scale);
		}finally{
			g.dispose();
		}
		return image;
	}
	
	private static int drawLines(Graphics2D g, List<String> lines, int x, int y, Font[] fonts, float scale){
		for(var line : lines){
			Font  font  = fonts[0];
			Color color = TEXT;
			if(line.startsWith("#")){
				font = fonts[1];
				color = ACCENT;
				line = line.substring(1);
			}else if(line.startsWith("!")){
				font = fonts[2];
				color = Color.WHITE;
				line = line.substring(1);
			}else if(line.startsWith("~")){
				color = DIM;
				line = line.substring(1);
			}
			g.setFont(font);
			g.setColor(color);
			var metrics = g.getFontMetrics();
			y += line.isEmpty()? metrics.getHeight()/2 : metrics.getAscent();
			g.drawString(line, x, y);
			y += metrics.getDescent() + Math.round(2*scale);
		}
		return y;
	}
	
	private static void upload(int texture, BufferedImage image, Size fb){
		var pixels = BufferUtils.createByteBuffer(fb.width()*fb.height()*4);
		BuffUtil.imageToBuffer(image, pixels);
		glBindTexture(GL_TEXTURE_2D, texture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fb.width(), fb.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
	}
	
	private static void draw(int texture, Size fb, Area area){
		// OpenGL counts from the bottom left.
		glViewport(area.x(), fb.height() - area.y() - area.height(), area.width(), area.height());
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);
		glMatrixMode(GL_PROJECTION);
		glLoadIdentity();
		glOrtho(0, 1, 1, 0, -1, 1);
		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();
		glEnable(GL_TEXTURE_2D);
		glBindTexture(GL_TEXTURE_2D, texture);
		glColor4f(1, 1, 1, 1);
		glBegin(GL_QUADS);
		glTexCoord2f(0, 0);
		glVertex2f(0, 0);
		glTexCoord2f(1, 0);
		glVertex2f(1, 0);
		glTexCoord2f(1, 1);
		glVertex2f(1, 1);
		glTexCoord2f(0, 1);
		glVertex2f(0, 1);
		glEnd();
	}
	
	private static void save(Path file, Size fb) throws IOException{
		var pixels = BufferUtils.createByteBuffer(fb.width()*fb.height()*4);
		glReadPixels(0, 0, fb.width(), fb.height(), GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		var image = new BufferedImage(fb.width(), fb.height(), BufferedImage.TYPE_INT_RGB);
		for(int y = 0; y<fb.height(); y++){
			for(int x = 0; x<fb.width(); x++){
				int i = (x + (fb.height() - 1 - y)*fb.width())*4;
				image.setRGB(x, y, (pixels.get(i)&0xFF)<<16|(pixels.get(i + 1)&0xFF)<<8|(pixels.get(i + 2)&0xFF));
			}
		}
		ImageIO.write(image, "png", file.toFile());
	}
}

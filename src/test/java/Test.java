import com.lapissea.glfw.GlfwMonitor;
import com.lapissea.glfw.GlfwWindow;

import java.io.File;

public class Test{
	
	public static void main(String[] args){
		GlfwMonitor.init();
		GlfwWindow win=new GlfwWindow();
		win.autoHandleStateSaving(new File("ay.json"), 100);
		win.visible.set(true);
		win.autoF11Toggle();
		win.init();
		win.pollEventsWhileOpen();
	}
	
}

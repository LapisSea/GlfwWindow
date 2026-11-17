package com.lapissea.glfw;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.MONITORINFOEX;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;
import org.lwjgl.system.windows.WINDOWPLACEMENT;

import static org.lwjgl.system.windows.User32.*;

final class Win32Placement{
	private Win32Placement(){ }
	
	record Restored(Point pos, Size size, boolean restoreToMaximized){ }
	
	private static long adjustForDpi = -1, isWindowArranged = -1;
	
	/**
	 * @return true if the window is snapped to a screen edge, quadrant or snap layout
	 */
	static boolean isArranged(long glfwWindow){
		long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
		if(hwnd == 0) return false;
		
		// Windows 10 2004 and later
		if(isWindowArranged == -1) isWindowArranged = User32.getLibrary().getFunctionAddress("IsWindowArranged");
		if(isWindowArranged != 0) return JNI.invokePI(hwnd, isWindowArranged) != 0;
		
		// Older: a normal window whose geometry is not the one it restores to was arranged by the shell
		var restored = read(glfwWindow);
		if(restored == null) return false;
		
		int[] x = {0}, y = {0}, w = {0}, h = {0};
		GLFW.glfwGetWindowPos(glfwWindow, x, y);
		GLFW.glfwGetWindowSize(glfwWindow, w, h);
		
		return !restored.pos().equals(new Point(x[0], y[0])) ||
		       !restored.size().equals(new Size(w[0], h[0]));
	}
	
	static void showMaximized(long glfwWindow){
		long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
		
		try(var stack = MemoryStack.stackPush()){
			var placement = WINDOWPLACEMENT.calloc(stack).length(WINDOWPLACEMENT.SIZEOF);
			if(hwnd == 0 || !GetWindowPlacement(null, hwnd, placement)){
				GLFW.glfwShowWindow(glfwWindow);
				return;
			}
			placement.flags(0).showCmd(SW_SHOWMAXIMIZED);
			SetWindowPlacement(null, hwnd, placement);
		}
	}
	
	/**
	 * @return content area geometry in screen coordinates, the same space GLFW positions and sizes use, null if unavailable
	 */
	static Restored read(long glfwWindow){
		long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
		if(hwnd == 0) return null;
		
		try(var stack = MemoryStack.stackPush()){
			var placement = WINDOWPLACEMENT.calloc(stack).length(WINDOWPLACEMENT.SIZEOF);
			if(!GetWindowPlacement(null, hwnd, placement)){
				return null;
			}
			
			RECT outer = placement.rcNormalPosition();
			
			int offX = 0, offY = 0;
			var info = MONITORINFOEX.calloc(stack).cbSize(MONITORINFOEX.SIZEOF); // why is windows like this...
			
			if(GetMonitorInfo(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), info)){
				offX = info.rcWork().left() - info.rcMonitor().left();
				offY = info.rcWork().top() - info.rcMonitor().top();
			}
			
			var frame = frameInsets(stack, hwnd);
			
			int x = outer.left() + offX - frame.left();
			int y = outer.top() + offY - frame.top();
			int w = (outer.right() - outer.left()) - (frame.right() - frame.left());
			int h = (outer.bottom() - outer.top()) - (frame.bottom() - frame.top());
			
			return new Restored(new Point(x, y), new Size(w, h), (placement.flags()&WPF_RESTORETOMAXIMIZED) != 0);
		}
	}
	
	private static RECT frameInsets(MemoryStack stack, long hwnd){
		int style   = (int)GetWindowLongPtr(null, hwnd, GWL_STYLE);
		int exStyle = (int)GetWindowLongPtr(null, hwnd, GWL_EXSTYLE);
		var frame   = RECT.calloc(stack);
		
		// GLFW sizes frames for the window's DPI (Windows 10 1607+), the plain variant uses the system DPI.
		if(adjustForDpi == -1){
			adjustForDpi = User32.getLibrary().getFunctionAddress("AdjustWindowRectExForDpi");
		}
		if(adjustForDpi != 0){
			int dpi = GetDpiForWindow(hwnd);
			// BOOL AdjustWindowRectExForDpi(LPRECT, DWORD style, BOOL menu, DWORD exStyle, UINT dpi)
			if(dpi>0 && JNI.invokePI(frame.address(), style, 0, exStyle, dpi, adjustForDpi) != 0) return frame;
		}
		
		AdjustWindowRectEx(null, frame, style, false, exStyle);
		return frame;
	}
}

package com.lapissea.glfw;

import org.lwjgl.glfw.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;

public final class GlfwCallbacks{
	public final Listeners<GLFWWindowPosCallbackI>          windowPos          = new Listeners<>();
	public final Listeners<GLFWWindowSizeCallbackI>         windowSize         = new Listeners<>();
	public final Listeners<GLFWFramebufferSizeCallbackI>    framebufferSize    = new Listeners<>();
	public final Listeners<GLFWWindowCloseCallbackI>        windowClose        = new Listeners<>();
	public final Listeners<GLFWWindowRefreshCallbackI>      windowRefresh      = new Listeners<>();
	public final Listeners<GLFWWindowFocusCallbackI>        windowFocus        = new Listeners<>();
	public final Listeners<GLFWWindowIconifyCallbackI>      windowIconify      = new Listeners<>();
	public final Listeners<GLFWWindowMaximizeCallbackI>     windowMaximize     = new Listeners<>();
	public final Listeners<GLFWWindowContentScaleCallbackI> windowContentScale = new Listeners<>();
	public final Listeners<GLFWKeyCallbackI>                key                = new Listeners<>();
	public final Listeners<GLFWCharCallbackI>               character          = new Listeners<>();
	public final Listeners<GLFWMouseButtonCallbackI>        mouseButton        = new Listeners<>();
	public final Listeners<GLFWCursorPosCallbackI>          cursorPos          = new Listeners<>();
	public final Listeners<GLFWCursorEnterCallbackI>        cursorEnter        = new Listeners<>();
	public final Listeners<GLFWScrollCallbackI>             scroll             = new Listeners<>();
	public final Listeners<GLFWDropCallbackI>               drop               = new Listeners<>();
	
	GlfwCallbacks(){ }
	
	void install(long handle){
		glfwSetWindowPosCallback(handle, (w, x, y) -> windowPos.dispatch(l -> l.invoke(w, x, y)));
		glfwSetWindowSizeCallback(handle, (w, x, y) -> windowSize.dispatch(l -> l.invoke(w, x, y)));
		glfwSetFramebufferSizeCallback(handle, (w, x, y) -> framebufferSize.dispatch(l -> l.invoke(w, x, y)));
		glfwSetWindowCloseCallback(handle, w -> windowClose.dispatch(l -> l.invoke(w)));
		glfwSetWindowRefreshCallback(handle, w -> windowRefresh.dispatch(l -> l.invoke(w)));
		glfwSetWindowFocusCallback(handle, (w, f) -> windowFocus.dispatch(l -> l.invoke(w, f)));
		glfwSetWindowIconifyCallback(handle, (w, i) -> windowIconify.dispatch(l -> l.invoke(w, i)));
		glfwSetWindowMaximizeCallback(handle, (w, m) -> windowMaximize.dispatch(l -> l.invoke(w, m)));
		glfwSetWindowContentScaleCallback(handle, (w, x, y) -> windowContentScale.dispatch(l -> l.invoke(w, x, y)));
		glfwSetKeyCallback(handle, (w, k, s, a, m) -> key.dispatch(l -> l.invoke(w, k, s, a, m)));
		glfwSetCharCallback(handle, (w, c) -> character.dispatch(l -> l.invoke(w, c)));
		glfwSetMouseButtonCallback(handle, (w, b, a, m) -> mouseButton.dispatch(l -> l.invoke(w, b, a, m)));
		glfwSetCursorPosCallback(handle, (w, x, y) -> cursorPos.dispatch(l -> l.invoke(w, x, y)));
		glfwSetCursorEnterCallback(handle, (w, e) -> cursorEnter.dispatch(l -> l.invoke(w, e)));
		glfwSetScrollCallback(handle, (w, x, y) -> scroll.dispatch(l -> l.invoke(w, x, y)));
		glfwSetDropCallback(handle, (w, c, n) -> drop.dispatch(l -> l.invoke(w, c, n)));
	}
	
	static void free(long handle){
		glfwFreeCallbacks(handle);
	}
}

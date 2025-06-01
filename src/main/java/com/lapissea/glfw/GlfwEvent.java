package com.lapissea.glfw;

public class GlfwEvent{
	
	protected final GlfwWindow source;
	public GlfwEvent(GlfwWindow source){ this.source = source; }
	
	public GlfwWindow getSource(){
		return source;
	}
}

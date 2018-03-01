package com.lapissea.glfw;

import com.lapissea.glfw.GlfwWindow;
import com.lapissea.util.event.Event;

public class GlfwMouseEvent extends Event<GlfwWindow>{
	
	public enum Type{
		DOWN, UP, HOLD
	}
	
	public final int  key;
	public final Type type;
	
	GlfwMouseEvent(GlfwWindow source, int key, Type type){
		super(source);
		this.key=key;
		this.type=type;
	}
}
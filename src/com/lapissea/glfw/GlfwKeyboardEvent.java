package com.lapissea.glfw;

import com.lapissea.glfw.GlfwWindow;
import com.lapissea.util.event.Event;

public class GlfwKeyboardEvent extends Event<GlfwWindow>{
	
	public enum Type{
		DOWN, HOLD, UP
	}
	
	public final int  key;
	public final Type type;
	
	GlfwKeyboardEvent(GlfwWindow source, int key, Type type){
		super(source);
		this.key=key;
		this.type=type;
	}
}
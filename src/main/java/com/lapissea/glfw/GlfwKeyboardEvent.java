package com.lapissea.glfw;

import java.util.ArrayDeque;
import java.util.Deque;

public class GlfwKeyboardEvent extends GlfwEvent{
	
	public enum Type{
		DOWN, HOLD, UP
	}
	
	private int  key;
	private Type type;
	
	GlfwKeyboardEvent(GlfwWindow source, int key, Type type){
		this.source = source;
		this.key = key;
		this.type = type;
	}
	
	public int getKey(){
		return key;
	}
	
	public Type getType(){
		return type;
	}
}

package com.lapissea.glfw;

public class GlfwKeyboardEvent extends GlfwEvent{
	
	public enum Type{
		DOWN, HOLD, UP
	}
	
	private final int  key;
	private final Type type;
	
	GlfwKeyboardEvent(GlfwWindow source, int key, Type type){
		super(source);
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

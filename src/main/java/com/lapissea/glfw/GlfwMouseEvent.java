package com.lapissea.glfw;

public class GlfwMouseEvent extends GlfwEvent{
	
	public enum Type{
		DOWN, UP, HOLD
	}
	
	private final int  key;
	private final Type type;
	
	GlfwMouseEvent(GlfwWindow source, int key, Type type){
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
	
	@Override
	public String toString(){
		return "GlfwMouseEvent{" +
		       "key=" + key +
		       ", type=" + type +
		       ", source=" + source +
		       '}';
	}
}

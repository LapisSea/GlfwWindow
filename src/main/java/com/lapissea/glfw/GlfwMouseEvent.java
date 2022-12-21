package com.lapissea.glfw;

public class GlfwMouseEvent extends GlfwEvent{
	
	public enum Type{
		DOWN, UP, HOLD
	}
	
	private int  key;
	private Type type;
	
	GlfwMouseEvent(GlfwWindow source, int key, Type type){
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
	
	@Override
	public String toString(){
		return "GlfwMouseEvent{" +
		       "key=" + key +
		       ", type=" + type +
		       ", source=" + source +
		       '}';
	}
}

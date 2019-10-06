package com.lapissea.glfw;

import java.util.Stack;

public class GlfwMouseEvent extends GlfwEvent{
	
	private static final Stack<GlfwMouseEvent> STACK=new Stack<>();
	
	static synchronized GlfwMouseEvent get(GlfwWindow source, int key, Type type){
		GlfwMouseEvent e=STACK.isEmpty()?new GlfwMouseEvent():STACK.pop();
		e.set(source, key, type);
		return e;
	}
	
	static synchronized void give(GlfwMouseEvent e){
		STACK.push(e);
	}
	
	public enum Type{
		DOWN, UP, HOLD
	}
	
	int  key;
	Type type;
	
	void set(GlfwWindow source, int key, Type type){
		this.source=source;
		this.key=key;
		this.type=type;
	}
	
	public int getKey(){
		return key;
	}
	
	public Type getType(){
		return type;
	}
	
	@Override
	public String toString(){
		return "GlfwMouseEvent{"+
		       "key="+key+
		       ", type="+type+
		       ", source="+source+
		       '}';
	}
}

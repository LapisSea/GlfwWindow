package com.lapissea.glfw;

import java.util.ArrayDeque;
import java.util.Deque;

public class GlfwKeyboardEvent extends GlfwEvent{
	
	private static final Deque<GlfwKeyboardEvent> STACK = new ArrayDeque<>();
	
	static synchronized GlfwKeyboardEvent get(GlfwWindow source, int key, Type type){
		GlfwKeyboardEvent e = STACK.isEmpty()? new GlfwKeyboardEvent() : STACK.pop();
		e.set(source, key, type);
		return e;
	}
	
	static synchronized void give(GlfwKeyboardEvent e){
		STACK.push(e);
	}
	
	public enum Type{
		DOWN, HOLD, UP
	}
	
	int  key;
	Type type;
	
	void set(GlfwWindow source, int key, Type type){
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

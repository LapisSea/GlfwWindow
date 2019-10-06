package com.lapissea.glfw;

import com.lapissea.vec.interf.IVec2iR;

import java.util.Stack;

public class GlfwMouseMoveEvent extends GlfwEvent{
	
	private static final Stack<GlfwMouseMoveEvent> STACK=new Stack<>();
	
	static synchronized GlfwMouseMoveEvent get(GlfwWindow source, IVec2iR delta, IVec2iR position){
		GlfwMouseMoveEvent e=STACK.isEmpty()?new GlfwMouseMoveEvent():STACK.pop();
		e.set(source, delta, position);
		return e;
	}
	
	static synchronized void give(GlfwMouseMoveEvent e){
		STACK.push(e);
	}
	
	
	IVec2iR delta;
	IVec2iR position;
	IVec2iR prevPos=new IVec2iR(){
		@Override
		public int x(){
			return position.x()-delta.x();
		}
		
		@Override
		public int y(){
			return position.y()-delta.y();
		}
	};
	
	void set(GlfwWindow source, IVec2iR delta, IVec2iR position){
		this.source=source;
		this.delta=delta;
		this.position=position;
	}
	
	public IVec2iR getDelta(){
		return delta;
	}
	
	public IVec2iR getPosition(){
		return position;
	}
	
	public IVec2iR getPrevPos(){
		return prevPos;
	}
}
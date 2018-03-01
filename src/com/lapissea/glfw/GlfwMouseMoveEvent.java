package com.lapissea.glfw;

import com.lapissea.glfw.GlfwWindow;
import com.lapissea.util.event.Event;
import com.lapissea.vec.interf.IVec2iR;

public class GlfwMouseMoveEvent extends Event<GlfwWindow>{
	
	public final IVec2iR delta;
	public final IVec2iR position;
	public final IVec2iR prevPos=new IVec2iR(){
		@Override
		public int x(){
			return position.x()-delta.x();
		}
		
		@Override
		public int y(){
			return position.y()-delta.y();
		}
	};
	
	GlfwMouseMoveEvent(GlfwWindow source, IVec2iR delta, IVec2iR position){
		super(source);
		this.delta=delta;
		this.position=position;
	}
}
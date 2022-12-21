package com.lapissea.glfw;

import com.lapissea.vec.interf.IVec2iR;

public class GlfwMouseMoveEvent extends GlfwEvent{
	
	private       IVec2iR delta;
	private       IVec2iR position;
	private final IVec2iR prevPos = new IVec2iR(){
		@Override
		public int x(){
			return position.x() - delta.x();
		}
		
		@Override
		public int y(){
			return position.y() - delta.y();
		}
	};
	
	GlfwMouseMoveEvent(GlfwWindow source, IVec2iR delta, IVec2iR position){
		this.source = source;
		this.delta = delta;
		this.position = position;
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

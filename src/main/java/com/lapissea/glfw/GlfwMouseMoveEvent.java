package com.lapissea.glfw;

public class GlfwMouseMoveEvent extends GlfwEvent{
	
	private final Point position, prevPos;
	
	GlfwMouseMoveEvent(GlfwWindow source, Point prevPos, Point position){
		super(source);
		this.prevPos = prevPos;
		this.position = position;
	}
	
	public Point getPosition(){ return position; }
	public Point getPrevPos() { return prevPos; }
	public Point getDelta()   { return new Point(position.x() - prevPos.x(), position.y() - prevPos.y()); }
}

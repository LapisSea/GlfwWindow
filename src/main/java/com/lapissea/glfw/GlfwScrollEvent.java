package com.lapissea.glfw;

public class GlfwScrollEvent extends GlfwEvent{
	private final double x, y;
	
	GlfwScrollEvent(GlfwWindow source, double x, double y){
		super(source);
		this.x = x;
		this.y = y;
	}
	public double getX(){ return x; }
	public double getY(){ return y; }
}

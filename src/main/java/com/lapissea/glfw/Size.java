package com.lapissea.glfw;

public record Size(int width, int height){
	public boolean isEmpty(){
		return width<=0 || height<=0;
	}
}

module GLFWWindow {
	requires java.desktop;
	
	requires com.google.gson;
	
	requires jlapisutil;
	requires jlapisvector;
	
	requires org.lwjgl.glfw;
	requires org.lwjgl;
	requires org.lwjgl.opengl;
	
	exports com.lapissea.glfw;
	
}

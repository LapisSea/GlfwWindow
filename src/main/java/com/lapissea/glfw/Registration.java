package com.lapissea.glfw;

public interface Registration extends AutoCloseable{
	void remove();
	@Override
	default void close(){ remove(); }
}

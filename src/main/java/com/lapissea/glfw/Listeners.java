package com.lapissea.glfw;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Listeners<L>{
	private final List<L> list = new CopyOnWriteArrayList<>();
	
	public Registration add(L listener){
		Objects.requireNonNull(listener);
		list.add(listener);
		return () -> list.remove(listener);
	}
	
	public boolean remove(L listener){ return list.remove(listener); }
	public boolean isEmpty()         { return list.isEmpty(); }
	
	void dispatch(Consumer<L> call){
		for(var l : list){
			call.accept(l);
		}
	}
}

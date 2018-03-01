package com.lapissea.glfw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.lapissea.util.TextUtil;
import com.lapissea.util.event.EventRegistry;
import com.lapissea.util.event.change.ChangeRegistry;
import com.lapissea.util.event.change.ChangeRegistryBool;
import com.lapissea.vec.ChangeRegistryVec2i;
import com.lapissea.vec.Vec2i;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.system.MemoryUtil;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GlfwWindow{
	
	protected long handle=NULL;
	
	public final ChangeRegistry<String> title=new ChangeRegistry<>("", e->{
		if(isCreated()) glfwSetWindowTitle(handle, e.object);
	});
	
	public final ChangeRegistryBool maximized=new ChangeRegistryBool(false, e->{
		if(!isCreated()&&!isFullScreen()) return;
		if(e.bool) glfwMaximizeWindow(handle);
		else glfwRestoreWindow(handle);
	});
	
	public final ChangeRegistryBool visible=new ChangeRegistryBool(false, e->{
		if(!isCreated()&&!isFullScreen()) return;
		if(e.bool) glfwShowWindow(handle);
		else glfwHideWindow(handle);
		if(maximized.get()) glfwMaximizeWindow(handle);
	});
	
	public final ChangeRegistryVec2i size=new ChangeRegistryVec2i(600, 400, e->{
		if(isCreated()&&!isFullScreen()) glfwSetWindowSize(handle, e.getSource().x(), e.getSource().y());
	});
	
	public final ChangeRegistryVec2i pos=new ChangeRegistryVec2i(-1, -1, e->{
		if(isCreated()&&!isFullScreen()) glfwSetWindowPos(handle, e.getSource().x(), e.getSource().y());
	}){
		
		@Override
		public ChangeRegistryVec2i set(int x, int y){
			
			if(x==-1&&y==-1){
				GlfwMonitor monitor=GlfwMonitor.getPrimaryMonitor();
				
				super.set((int)monitor.bounds.getCenterX()-size.x()/2, (int)monitor.bounds.getCenterY()-size.y()/2);
				return this;
			}
			
			super.set(x, y);
			return this;
		}
	};
	
	private final Vec2i restorePos=new Vec2i(), restoreSize=new Vec2i();
	public final ChangeRegistry<GlfwMonitor> monitor=new ChangeRegistry<GlfwMonitor>(e->{
		if(!isCreated()) return;
		if(e.object==null) glfwSetWindowMonitor(handle, 0, restorePos.x(), restorePos.y(), restoreSize.x(), restoreSize.y(), 0);
		else{
			restorePos.set(pos);
			restoreSize.set(size);
			glfwSetWindowMonitor(handle, e.object.handle, pos.x(), pos.y(), e.object.bounds.width, e.object.bounds.height, e.object.refreshRate);
		}
	});
	
	private final Vec2i               mousePosControl=new Vec2i();
	public final  ChangeRegistryVec2i mousePos       =new ChangeRegistryVec2i(e->{
		if(mousePosControl.equals(e.getSource())) return;
		mousePosControl.set(e.getSource());
		glfwSetCursorPos(handle, e.getSource().x(), e.getSource().y());
	});
	
	public final EventRegistry<GlfwWindow, GlfwKeyboardEvent>  registryKeyboardKey=new EventRegistry<>();
	public final EventRegistry<GlfwWindow, GlfwMouseEvent>     registryMouseButton=new EventRegistry<>();
	public final EventRegistry<GlfwWindow, GlfwMouseMoveEvent> registryMouseMove  =new EventRegistry<>();
	
	public GlfwWindow(){ }
	
	public GlfwWindow init(){
		return init(true, true);
	}
	
	public GlfwWindow init(boolean resizeable, boolean decorated){
		
		pos.set(pos);
		Rectangle2D windowRect=new Rectangle2D.Float();
		windowRect.setRect(pos.x(), pos.y(), size.x(), size.y());
		
		if(GlfwMonitor.moveToVisible(windowRect)){
			pos.set((int)windowRect.getX(), (int)windowRect.getY());
			size.set((int)windowRect.getWidth(), (int)windowRect.getHeight());
		}
		preInit(resizeable, decorated);
		
		
		if(isFullScreen()){
			GlfwMonitor monitor=this.monitor.get();
			handle=glfwCreateWindow(monitor.bounds.width, monitor.bounds.height, "", monitor.handle, handle);
		}else handle=glfwCreateWindow(100, 100, "", NULL, handle);
		
		initProps();
		
		return this;
	}
	
	public boolean isFullScreen(){
		return monitor.get()!=null;
	}
	
	private void preInit(boolean resizeable, boolean decorated){
		glfwWindowHint(GLFW_RESIZABLE, resizeable?GLFW_TRUE:GLFW_FALSE);
		glfwWindowHint(GLFW_DECORATED, decorated?GLFW_TRUE:GLFW_FALSE);
		if(isFullScreen()) glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, visible.get()?GLFW_TRUE:GLFW_FALSE);
	}
	
	private void initProps(){
		glfwSetWindowTitle(handle, title.get());
		
		if(!isFullScreen()){
			glfwSetWindowPos(handle, pos.x(), pos.y());
			glfwSetWindowSize(handle, size.x(), size.y());
			if(visible.get()){
				if(maximized.get()) glfwMaximizeWindow(handle);
				glfwShowWindow(handle);
			}
		}
		
		
		glfwSetWindowMaximizeCallback(handle, (window, maximized)->{
			if(window!=handle) return;
			this.maximized.set(maximized);
		});
		glfwSetWindowSizeCallback(handle, (window, w, h)->{
			if(window!=handle) return;
			size.set(w, h);
		});
		glfwSetWindowPosCallback(handle, (window, xpos, ypos)->{
			if(window!=handle) return;
			pos.set(xpos, ypos);
		});
		
		glfwSetKeyCallback(handle, GLFWKeyCallback.create((window, key, scancode, action, mods)->{
			if(window!=handle) return;
			GlfwKeyboardEvent.Type type;
			
			if(action==GLFW_PRESS) type=GlfwKeyboardEvent.Type.DOWN;
			else if(action==GLFW_REPEAT) type=GlfwKeyboardEvent.Type.HOLD;
			else type=GlfwKeyboardEvent.Type.UP;
			
			registryKeyboardKey.dispatch(new GlfwKeyboardEvent(this, key, type));
		}));
		glfwSetMouseButtonCallback(handle, GLFWMouseButtonCallback.create((window, button, action, mods)->{
			if(window!=handle) return;
			
			GlfwMouseEvent.Type type;
			if(action==GLFW_PRESS) type=GlfwMouseEvent.Type.DOWN;
			else if(action==GLFW_REPEAT) type=GlfwMouseEvent.Type.HOLD;
			else type=GlfwMouseEvent.Type.UP;
			
			registryMouseButton.dispatch(new GlfwMouseEvent(this, button, type));
		}));
		glfwSetCursorPosCallback(handle, GLFWCursorPosCallback.create((window, xpos, ypos)->{
			if(window!=handle) return;
			mousePosControl.set((int)xpos, (int)ypos);
			Vec2i delta=mousePosControl.clone().sub(mousePos);
			mousePos.set(mousePosControl);
			registryMouseMove.dispatch(new GlfwMouseMoveEvent(this, delta, mousePos));
		}));
	}
	
	public boolean isKeyDown(int key){
		return glfwGetKey(handle, GLFW_KEY_V)==GLFW_TRUE;
	}
	
	public GlfwWindow setUserPointer(long pointer){
		glfwSetWindowUserPointer(handle, pointer);
		return this;
	}
	
	public long getUserPointer(){
		return glfwGetWindowUserPointer(handle);
	}
	
	public void setIcon(GLFWImage... images){
		if(images.length==0) return;
		
		try(final GLFWImage.Buffer iconSet=GLFWImage.malloc(images.length)){
			for(int i=images.length-1;i>=0;i--){
				iconSet.put(i, images[i]);
			}
			glfwSetWindowIcon(handle, iconSet);
		}
	}
	
	public static GLFWImage imgToGlfw(BufferedImage image){
		return GLFWImage.create().set(image.getWidth(), image.getHeight(), BuffUtil.imageToBuffer(image, memAlloc(image.getWidth()*image.getHeight()*4)));
	}
	
	public boolean isCreated(){
		return handle!=NULL;
	}
	
	public void pollEvents(){
		glfwPollEvents();
	}
	
	public boolean shouldClose(){
		return glfwWindowShouldClose(handle);
	}
	
	
	public void destroy(){
		if(!isCreated()) throw new IllegalStateException();
		glfwDestroyWindow(handle);
		handle=NULL;
	}
	
	
	public void hide(){
		visible.set(false);
	}
	
	public void show(){
		visible.set(true);
	}
	
	
	public boolean isVisible(){
		return visible.get()&&size.x()!=0&&size.y()!=0;
	}
	
	
	//=======SAVE=======//
	
	
	public GlfwWindow loadState(String data){
		return loadState(new Gson().fromJson(data, HashMap.class));
	}
	
	public GlfwWindow loadState(File file){
		try(Reader data=new BufferedReader(new FileReader(file))){
			return loadState(data);
		}catch(IOException e){}
		return this;
	}
	
	public GlfwWindow loadState(Reader data){
		return loadState(new Gson().fromJson(data, HashMap.class));
	}
	
	public GlfwWindow loadState(Map<String, LinkedTreeMap> data){
		
		try{
			IntBuffer target=ByteBuffer.allocate(Integer.SIZE/Byte.SIZE*4)
			                           .order(ByteOrder.nativeOrder())
			                           .put(TextUtil.hexStringToByteArray(((Map<String, ?>)data).get("target").toString()))
			                           .flip()
			                           .asIntBuffer();
			
			Rectangle2D rect=new Rectangle2D.Float(target.get(), target.get(), target.get(), target.get());
			
			monitor.set(GlfwMonitor.getMonitors()
			                       .stream()
			                       .filter(m->m.bounds.equals(rect))
			                       .findAny()
			                       .orElseGet(GlfwMonitor::getPrimaryMonitor));
			size.set(monitor.get().bounds.width, monitor.get().bounds.height);
			
			try{
				LinkedTreeMap<String, Number> size=(LinkedTreeMap<String, Number>)((LinkedTreeMap)data.get("restore")).get("size");
				LinkedTreeMap<String, Number> pos =(LinkedTreeMap<String, Number>)((LinkedTreeMap)data.get("restore")).get("location");
				restoreSize.set(size.getOrDefault("width", this.size.x()).intValue(),
				                size.getOrDefault("height", this.size.y()).intValue());
				restorePos.set(pos.getOrDefault("top", this.pos.x()).intValue(),
				               pos.getOrDefault("left", this.pos.y()).intValue());
			}catch(Throwable e){}
			
			return this;
		}catch(Throwable e){}
		
		try{
			LinkedTreeMap<String, Number> size=data.get("size");
			this.size.set(size.getOrDefault("width", this.size.x()).intValue(),
			              size.getOrDefault("height", this.size.y()).intValue());
		}catch(Throwable e){}
		
		try{
			LinkedTreeMap<String, Number> pos=data.get("location");
			this.pos.set(pos.getOrDefault("top", this.pos.x()).intValue(),
			             pos.getOrDefault("left", this.pos.y()).intValue());
		}catch(Throwable e){}
		try{
			maximized.set(Boolean.parseBoolean(data.get("max")+""));
		}catch(Throwable e){
			e.printStackTrace();
		}
		
		
		return this;
	}
	
	private @NotNull Gson getG(){
		return new GsonBuilder().setPrettyPrinting()
		                        .disableHtmlEscaping()
		                        .create();
	}
	
	public GlfwWindow saveState(File file){
		try(Writer data=new BufferedWriter(new FileWriter(file))){
			return saveState(data);
		}catch(IOException e){}
		return this;
	}
	
	public GlfwWindow saveState(Writer out){
		getG().toJson(saveState(new JsonObject()), out);
		return this;
	}
	
	public String saveState(){
		return getG().toJson(saveState(new JsonObject()));
	}
	
	public JsonObject saveState(JsonObject json){
		if(!isCreated()) throw new IllegalStateException();
		
		if(isFullScreen()){
			ByteBuffer bytes=ByteBuffer.allocate(Integer.SIZE/Byte.SIZE*4).order(ByteOrder.nativeOrder());
			
			GlfwMonitor m=monitor.get();
			bytes.putInt(m.bounds.x);
			bytes.putInt(m.bounds.y);
			bytes.putInt(m.bounds.width);
			bytes.putInt(m.bounds.height);
			
			json.addProperty("target", TextUtil.bytesToHex(bytes.array()));
			JsonObject restore=new JsonObject();
			
			JsonObject size=new JsonObject();
			size.addProperty("width", restoreSize.x());
			size.addProperty("height", restoreSize.y());
			JsonObject location=new JsonObject();
			location.addProperty("top", restorePos.x());
			location.addProperty("left", restorePos.y());
			
			restore.add("size", size);
			restore.add("location", location);
			
			json.add("restore", restore);
		}else{
			boolean max=maximized.get();
			if(max) maximized.set(false);
			
			JsonObject size=new JsonObject();
			size.addProperty("width", this.size.x());
			size.addProperty("height", this.size.y());
			JsonObject location=new JsonObject();
			location.addProperty("top", pos.x());
			location.addProperty("left", pos.y());
			
			if(max) maximized.set(true);
			
			json.add("size", size);
			json.add("location", location);
			json.addProperty("max", maximized.get());
		}
		return json;
	}
	
	public void setAutoFullScreen(){
		
		pos.set(pos);
		Rectangle2D windowRect=new Rectangle2D.Float();
		windowRect.setRect(pos.x(), pos.y(), size.x(), size.y());
		
		GlfwMonitor.moveToVisible(windowRect);
		
		GlfwMonitor best       =null;
		double      bestOverlap=0;
		
		for(GlfwMonitor monitor : GlfwMonitor.getMonitors()){
			Rectangle2D overlapRect=windowRect.createIntersection(monitor.bounds);
			double      overlap    =overlapRect.getWidth()*overlapRect.getHeight();
			if(bestOverlap<overlap){
				best=monitor;
				bestOverlap=overlap;
			}
		}
		monitor.set(best);
	}
	
	public void requestClose(){
		glfwSetWindowShouldClose(handle, true);
	}
}
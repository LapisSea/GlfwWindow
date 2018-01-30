package com.lapissea.glfw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.lapissea.util.TextUtil;
import com.lapissea.util.event.change.ChangeRegistry;
import com.lapissea.util.event.change.ChangeRegistryBool;
import com.lapissea.vec.ChangeRegistryVec2i;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWImage;

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
	
	public final ChangeRegistry<GlfwMonitor> monitor=new ChangeRegistry<GlfwMonitor>(e->{
		if(!isCreated()) return;
		
		glfwSetWindowMonitor(handle, e.object.handle, pos.x(), pos.y(), e.object.bounds.width, e.object.bounds.height, e.object.refreshRate);
	});
	
	
	public GlfwWindow(){
	}
	
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
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
	}
	
	private void initProps(){
		glfwSetWindowTitle(handle, title.get());
		
		if(!isFullScreen()){
			glfwSetWindowPos(handle, pos.x(), pos.y());
			glfwSetWindowSize(handle, size.x(), size.y());
			if(maximized.get()) glfwMaximizeWindow(handle);
			if(visible.get()) glfwShowWindow(handle);
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
		
		ByteBuffer buffer=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
		
		for(int i=0;i<image.getHeight();i++){
			for(int j=0;j<image.getWidth();j++){
				int colorSpace=image.getRGB(j, i);
				buffer.put((byte)((colorSpace>>16)&0xFF));
				buffer.put((byte)((colorSpace>>8)&0xFF));
				buffer.put((byte)((colorSpace>>0)&0xFF));
				buffer.put((byte)((colorSpace>>24)&0xFF));
			}
		}
		
		buffer.flip();
		
		return GLFWImage.create().set(image.getWidth(), image.getHeight(), buffer);
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
	
	
	public boolean isHidden(){
		return !isVisible();
	}
	
	public boolean isVisible(){
		return visible.get();
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
	
}
package com.lapissea.glfw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.lapissea.util.LogUtil;
import com.lapissea.util.NotNull;
import com.lapissea.util.TextUtil;
import com.lapissea.util.UtilL;
import com.lapissea.util.event.EventRegistry;
import com.lapissea.util.event.change.ChangeRegistry;
import com.lapissea.util.event.change.ChangeRegistryBool;
import com.lapissea.vec.ChangeRegistryVec2i;
import com.lapissea.vec.Vec2f;
import com.lapissea.vec.Vec2i;
import com.lapissea.vec.interf.IVec2iR;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.lapissea.glfw.GlfwKeyboardEvent.Type.DOWN;
import static com.lapissea.glfw.GlfwWindow.Cursor.NORMAL;
import static com.lapissea.glfw.GlfwWindow.SurfaceAPI.OPENGL;
import static com.lapissea.glfw.GlfwWindow.SurfaceAPI.VULKAN;
import static com.lapissea.util.UtilL.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAlloc;

@SuppressWarnings("unused")
public class GlfwWindow{
	
	public enum SurfaceAPI{
		OPENGL(GLFW_OPENGL_API),
		OPENGL_ES(GLFW_OPENGL_ES_API),
		VULKAN(GLFW_NO_API);
		
		private final int handle;
		
		SurfaceAPI(int handle){
			this.handle=handle;
		}
	}
	
	public enum Cursor{
		NORMAL(GLFW_CURSOR_NORMAL),
		HIDDEN(GLFW_CURSOR_HIDDEN),
		DISABLED(GLFW_CURSOR_DISABLED);
		
		private final int handle;
		
		Cursor(int handle){
			this.handle=handle;
		}
	}
	
	protected long handle=NULL;
	
	public final ChangeRegistry<String> title=new ChangeRegistry<>("", tit/*ty*/->{
		if(isCreated()) glfwSetWindowTitle(handle, tit);
		
	});
	
	public final ChangeRegistryBool maximized=new ChangeRegistryBool(false, max->{
		if(!isCreated()&&!isFullScreen()) return;
		if(max) glfwMaximizeWindow(handle);
		else glfwRestoreWindow(handle);
	});
	
	public final ChangeRegistryBool visible=new ChangeRegistryBool(false, vis->{
		if(!isCreated()&&!isFullScreen()) return;
		if(vis){
			moveToVisible();
			glfwShowWindow(handle);
		}else glfwHideWindow(handle);
		if(maximized.get()) glfwMaximizeWindow(handle);
	});
	
	
	public final ChangeRegistryVec2i size=new ChangeRegistryVec2i(600, 400, siz->{
		if(isCreated()&&!isFullScreen()) glfwSetWindowSize(handle, siz.x(), siz.y());
	});
	
	private final Vec2i restorePos=new Vec2i(), restoreSize=new Vec2i();
	public final ChangeRegistryVec2i pos=new ChangeRegistryVec2i(-1, -1, p->{
		if(isCreated()&&!isFullScreen()) glfwSetWindowPos(handle, p.x(), p.y());
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
	
	private      boolean            iconifiedEventFlag;
	public final ChangeRegistryBool iconified=new ChangeRegistryBool(false, vis->{
		if(iconifiedEventFlag){
			iconifiedEventFlag=false;
			return;
		}
		
		if(!isCreated()&&!isFullScreen()) return;
		if(vis) glfwIconifyWindow(handle);
		else glfwRestoreWindow(handle);
		if(maximized.get()) glfwMaximizeWindow(handle);
	});
	
	public final ChangeRegistry<GlfwMonitor> monitor=new ChangeRegistry<GlfwMonitor>(monitor->{
		if(!isCreated()) return;
		if(monitor==null) glfwSetWindowMonitor(handle, 0, restorePos.x(), restorePos.y(), restoreSize.x(), restoreSize.y(), 0);
		else{
			restorePos.set(pos);
			restoreSize.set(size);
			glfwSetWindowMonitor(handle, monitor.handle, pos.x(), pos.y(), monitor.bounds.width, monitor.bounds.height, monitor.refreshRate);
		}
	});
	
	private final Vec2i               mousePosControl=new Vec2i();
	public final  ChangeRegistryVec2i mousePos       =new ChangeRegistryVec2i(pos->{
		if(mousePosControl.equals(pos)) return;
		mousePosControl.set(pos);
		glfwSetCursorPos(handle, pos.x(), pos.y());
	});
	
	public final ChangeRegistryBool focused=new ChangeRegistryBool(false);
	
	public static class KeyboardEventRegistry extends EventRegistry<GlfwKeyboardEvent>{
		
		public boolean register(int key, GlfwKeyboardEvent.Type type, @NotNull Consumer<GlfwKeyboardEvent> listener){
			return register(e->{if(e.key==key&&e.type==type) listener.accept(e);});
		}
	}
	
	public final KeyboardEventRegistry             registryKeyboardKey=new KeyboardEventRegistry();
	public final EventRegistry<GlfwMouseEvent>     registryMouseButton=new EventRegistry<>();
	public final EventRegistry<GlfwMouseMoveEvent> registryMouseMove  =new EventRegistry<>();
	public final EventRegistry<Vec2f>              registryMouseScroll=new EventRegistry<>();
	
	public final ChangeRegistry<Cursor> cursorMode=new ChangeRegistry<>(NORMAL, state->{
		if(!isCreated()) return;
		glfwSetInputMode(handle, GLFW_CURSOR, state.handle);
	});
	
	private final ArrayList<Runnable> onDestroy=new ArrayList<>(1);
	
	public GlfwWindow init(){
		return init(OPENGL);
	}
	
	public GlfwWindow init(boolean resizeable, boolean decorated){
		return init(OPENGL, resizeable, decorated, false);
	}
	
	public GlfwWindow init(boolean resizeable){
		return init(OPENGL, resizeable);
	}
	
	public GlfwWindow init(@NotNull SurfaceAPI api){
		return init(api, true);
	}
	
	public GlfwWindow init(@NotNull SurfaceAPI api, boolean resizeable){
		return init(api, resizeable, true, false);
	}
	
	public GlfwWindow init(@NotNull SurfaceAPI api, boolean resizeable, boolean decorated, boolean transparent){
		pos.set(pos);
		moveToVisible();
		preInit(api, resizeable, decorated, transparent);
		
		
		if(isFullScreen()){
			GlfwMonitor monitor=this.monitor.get();
			handle=glfwCreateWindow(monitor.bounds.width, monitor.bounds.height, "", monitor.handle, handle);
		}else handle=glfwCreateWindow(100, 100, "", NULL, handle);
		if(api==VULKAN){
			glfwSetWindowSizeLimits(handle, 1, 1, GLFW_DONT_CARE, GLFW_DONT_CARE);
		}
		initProps();
		
		return this;
	}
	
	private void moveToVisible(){
		
		Rectangle2D windowRect=new Rectangle2D.Float();
		windowRect.setRect(pos.x(), pos.y(), size.x(), size.y());
		
		if(GlfwMonitor.moveToVisible(windowRect)){
			pos.set((int)windowRect.getX(), (int)windowRect.getY());
			size.set((int)windowRect.getWidth(), (int)windowRect.getHeight());
		}
	}
	
	public boolean isFullScreen(){
		return monitor.get()!=null;
	}
	
	private void preInit(SurfaceAPI api, boolean resizeable, boolean decorated, boolean transparent){
		glfwWindowHint(GLFW_CLIENT_API, api.handle);
		glfwWindowHint(GLFW_RESIZABLE, resizeable?GLFW_TRUE:GLFW_FALSE);
		glfwWindowHint(GLFW_DECORATED, decorated?GLFW_TRUE:GLFW_FALSE);
		if(isFullScreen()) glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, visible.get()?GLFW_TRUE:GLFW_FALSE);
		glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, transparent?GLFW_TRUE:GLFW_FALSE);
	}
	
	protected void initProps(){
		
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
		glfwSetWindowFocusCallback(handle, (window, focused)->{
			if(window!=handle) return;
			this.focused.set(focused);
		});
		glfwSetWindowIconifyCallback(handle, (window, iconified)->{
			if(window!=handle) return;
			iconifiedEventFlag=true;
			this.iconified.set(iconified);
		});
		
		glfwSetKeyCallback(handle, GLFWKeyCallback.create((window, key, scancode, action, mods)->{
			if(window!=handle) return;
			GlfwKeyboardEvent.Type type;
			
			if(action==GLFW_PRESS) type=DOWN;
			else if(action==GLFW_REPEAT) type=GlfwKeyboardEvent.Type.HOLD;
			else type=GlfwKeyboardEvent.Type.UP;
			
			GlfwKeyboardEvent e=GlfwKeyboardEvent.get(this, key, type);
			try{
				registryKeyboardKey.dispatch(e);
			}finally{
				GlfwKeyboardEvent.give(e);
			}
		}));
		glfwSetMouseButtonCallback(handle, GLFWMouseButtonCallback.create((window, button, action, mods)->{
			if(window!=handle) return;
			
			GlfwMouseEvent.Type type;
			if(action==GLFW_PRESS) type=GlfwMouseEvent.Type.DOWN;
			else if(action==GLFW_REPEAT) type=GlfwMouseEvent.Type.HOLD;
			else type=GlfwMouseEvent.Type.UP;
			
			GlfwMouseEvent e=GlfwMouseEvent.get(this, button, type);
			try{
				registryMouseButton.dispatch(e);
			}finally{
				GlfwMouseEvent.give(e);
			}
		}));
		glfwSetCursorPosCallback(handle, GLFWCursorPosCallback.create((window, xpos, ypos)->{
			if(window!=handle) return;
			mousePosControl.set((int)xpos, (int)ypos);
			Vec2i delta=mousePosControl.clone().sub(mousePos);
			mousePos.set(mousePosControl);
			
			GlfwMouseMoveEvent e=GlfwMouseMoveEvent.get(this, delta, mousePos);
			try{
				registryMouseMove.dispatch(e);
			}finally{
				GlfwMouseMoveEvent.give(e);
			}
		}));
		glfwSetScrollCallback(handle, (window, xoffset, yoffset)->{
			if(window!=handle) return;
			registryMouseScroll.dispatch(new Vec2f((float)xoffset, (float)yoffset));
		});
		
		title.dispatch(title.get());
		
		if(!isFullScreen()){
			pos.dispatch(pos);
			size.dispatch(size);
			if(visible.get()) maximized.dispatch(maximized.get());
		}
		cursorMode.dispatch(cursorMode.get());
		focus();
		focused.set(true);
	}
	
	public boolean isKeyDown(int key){
		requireCreated();
		return glfwGetKey(handle, key)==GLFW_PRESS;
	}
	
	public boolean isMouseKeyDown(int key){
		requireCreated();
		return glfwGetMouseButton(handle, key)==GLFW_PRESS;
	}
	
	public GlfwWindow setUserPointer(@NotNull PointerBuffer pointer){
		return setUserPointer(pointer.address());
	}
	
	public GlfwWindow setUserPointer(long pointer){
		requireCreated();
		glfwSetWindowUserPointer(handle, pointer);
		return this;
	}
	
	public long getUserPointer(){
		return glfwGetWindowUserPointer(handle);
	}
	
	public void setIcon(BufferedImage... images){
		if(images.length==0) return;
		setIcon(UtilL.convert(
			images,
			GLFWImage[]::new,
			image->GLFWImage.create().set(image.getWidth(), image.getHeight(), BuffUtil.imageToBuffer(image, memAlloc(image.getWidth()*image.getHeight()*4)))
		));
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
	
	public boolean isCreated(){
		return handle!=NULL;
	}
	
	public void pollEvents(){
		glfwPollEvents();
	}
	
	public void waitEvents(){
		glfwWaitEvents();
	}
	
	public boolean shouldClose(){
		if(!isCreated()) return true;
		return glfwWindowShouldClose(handle);
	}
	
	
	public void destroy(){
		requireCreated();
		onDestroy.forEach(Runnable::run);
		onDestroy.clear();
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
	
	public void swapBuffers(){
		requireCreated();
		glfwSwapBuffers(handle);
	}
	
	//=======SAVE=======//
	
	
	@SuppressWarnings("unchecked")
	public GlfwWindow loadState(String data){
		return loadState(new Gson().fromJson(data, HashMap.class));
	}
	
	public GlfwWindow loadState(File file){
		if(file.length()==0) return this;
		
		try(Reader data=new BufferedReader(new FileReader(file))){
			return loadState(data);
		}catch(Exception ignored){}
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public GlfwWindow loadState(Reader data){
		return loadState(new Gson().fromJson(data, HashMap.class));
	}
	
	@SuppressWarnings({"unchecked", "AutoBoxing", "rawtypes"})
	public GlfwWindow loadState(Map<String, LinkedTreeMap> data){
		
		try{
			IntBuffer target=ByteBuffer.wrap(TextUtil.hexStringToByteArray(((Map<String, ?>)data).get("target").toString()))
			                           .order(ByteOrder.nativeOrder())
			                           .asIntBuffer();
			
			Rectangle2D rect=new Rectangle2D.Float(target.get(), target.get(), target.get(), target.get());
			
			monitor.set(GlfwMonitor.getMonitors()
			                       .stream()
			                       .filter(m->m.bounds.equals(rect))
			                       .findAny()
			                       .orElseGet(GlfwMonitor::getPrimaryMonitor));
			size.set(monitor.get().bounds.width, monitor.get().bounds.height);
			
			try{
				LinkedTreeMap<String, LinkedTreeMap<String, Number>> restore=data.get("restore");
				
				LinkedTreeMap<String, Number> sizeD=restore.get("size"), posD=restore.get("location");
				
				restoreSize.set(sizeD.getOrDefault("width", size.x()).intValue(),
				                sizeD.getOrDefault("height", size.y()).intValue());
				restorePos.set(posD.getOrDefault("top", pos.x()).intValue(),
				               posD.getOrDefault("left", pos.y()).intValue());
			}catch(Throwable ignored){}
			
			return this;
		}catch(Throwable ignored){}
		
		try{
			LinkedTreeMap<String, Number> size=data.get("size");
			this.size.set(size.getOrDefault("width", this.size.x()).intValue(),
			              size.getOrDefault("height", this.size.y()).intValue());
		}catch(Throwable ignored){}
		
		try{
			LinkedTreeMap<String, Number> pos=data.get("location");
			this.pos.set(pos.getOrDefault("top", this.pos.x()).intValue(),
			             pos.getOrDefault("left", this.pos.y()).intValue());
		}catch(Throwable ignored){}
		try{
			maximized.set(Boolean.parseBoolean(data.get("max")+""));
		}catch(Throwable e){
			e.printStackTrace();
		}
		
		
		return this;
	}
	
	@NotNull
	private Gson getG(){
		return new GsonBuilder().setPrettyPrinting()
		                        .disableHtmlEscaping()
		                        .create();
	}
	
	public synchronized GlfwWindow saveState(File file){
		File tmp=new File(file.getPath()+"@");
		try(Writer data=new BufferedWriter(new FileWriter(tmp))){
			saveState(data);
		}catch(Exception ignored){
			return this;
		}
		
		file.delete();
		tmp.renameTo(file);
		
		return this;
	}
	
	public GlfwWindow saveState(Writer out){
		getG().toJson(saveState(new JsonObject()), out);
		return this;
	}
	
	public String saveState(){
		return getG().toJson(saveState(new JsonObject()));
	}
	
	@SuppressWarnings("AutoBoxing")
	public JsonObject saveState(JsonObject json){
		requireCreated();
		
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
			IVec2iR resSiz;
			IVec2iR resPos;
			boolean max;
			boolean ico;
			
			final boolean visible=isVisible();
			
			if(!visible){
				ico=iconified.get();
				max=!ico&&maximized.get();
				
				if(max) maximized.set(false);
				if(ico) iconified.set(false);
				
				resSiz=this.size;
				resPos=pos;
			}else{
				max=false;
				ico=false;
				if(restorePos.equals(0, 0)) restorePos.set(pos);
				if(restoreSize.equals(0, 0)) restoreSize.set(size);
				
				resSiz=restoreSize;
				resPos=restorePos;
			}
			
			JsonObject size=new JsonObject();
			size.addProperty("width", resSiz.x());
			size.addProperty("height", resSiz.y());
			JsonObject location=new JsonObject();
			location.addProperty("top", resPos.x());
			location.addProperty("left", resPos.y());
			
			if(max) maximized.set(true);
			if(ico) iconified.set(true);
			
			json.add("size", size);
			json.add("location", location);
			json.addProperty("max", maximized.get());
		}
		return json;
	}
	
	public void setAutoFullScreen(){
		
		
		monitor.set(getBestScreen());
	}
	
	private GlfwMonitor getBestScreen(){
		GlfwMonitor.init();
		
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
		
		return best;
	}
	
	public void requestClose(){
		requireCreated();
		glfwSetWindowShouldClose(handle, true);
	}
	
	public boolean isFocused(){
		return focused.get();
	}
	
	public void focus(){
		glfwFocusWindow(handle);
	}
	
	public void toggleFullScreen(){
		if(monitor.get()!=null) monitor.set(null);
		else setAutoFullScreen();
	}
	
	public CompletableFuture<Void> whileOpen(@NotNull Runnable run, @NotNull String name){
		return whileOpen(run, name, Thread::new);
	}
	
	public CompletableFuture<Void> whileOpen(@NotNull Runnable run, @NotNull String name, @NotNull BiFunction<Runnable, String, Thread> newThread){
		CompletableFuture<Void> future=new CompletableFuture<>();
		newThread.apply(()->{
			whileOpen(run);
			future.complete(null);
		}, name).start();
		return future;
	}
	
	public void whileOpen(@NotNull Runnable run){
		while(!this.shouldClose()){
			run.run();
		}
	}
	
	public void centerMouse(){
		mousePos.set(size.x()/2, size.y()/2);
	}
	
	@Override
	public String toString(){
		return "GlfwWindowVk{"+title.get()+"}";
	}
	
	public void useThisThread(){
		requireCreated();
		glfwMakeContextCurrent(handle);
	}
	
	private void requireCreated(){
		if(!isCreated()) throw new IllegalStateException();
	}
	
	public void centerWindow(){
		GlfwMonitor monitor=getBestScreen();
		pos.set((int)monitor.bounds.getCenterX()-size.x()/2, (int)monitor.bounds.getCenterY()-size.y()/2);
	}
	
	public void pollEventsWhileOpen(){
		whileOpen(()->{
			sleep(0, 1000);
			pollEvents();
		});
		hide();
	}
	
	public void autoHandleStateSaving(File saveFile){
		autoHandleStateSaving(saveFile, 1000);
	}
	
	public void autoHandleStateSaving(File saveFile, int timeoutInterval){
		loadState(saveFile);
		
		class Runner extends Thread{
			private       long lastChange=System.nanoTime();
			private       long lastSave  =lastChange;
			private final long timeoutIntervalNs;
			
			private Runner(long timeoutIntervalNs){
				super("GlfwStateMonitor");
				this.timeoutIntervalNs=timeoutIntervalNs;
			}
			
			@Override
			public void run(){
				UtilL.sleepWhile(()->!isCreated());
				whileOpen(()->{
					long lastChange=this.lastChange;
					if(iconified.get()||lastChange==lastSave||System.nanoTime()<lastChange+timeoutIntervalNs){
						UtilL.sleep(timeoutInterval);
						return;
					}
					
					this.lastSave=lastChange;
					
					saveState(saveFile);
					UtilL.sleep(timeoutInterval);
				});
			}
		}
		
		Runner runner=new Runner(timeoutInterval*1000_000L);
		runner.setDaemon(true);
		runner.start();
		
		Runnable changeEvent=()->{
			runner.lastChange=System.nanoTime();
			runner.interrupt();
		};
		
		size.register(changeEvent);
		pos.register(changeEvent);
		monitor.register(m->changeEvent.run());
		onDestroy(()->saveState(saveFile));
		
	}
	
	public void autoF11Toggle(){
		registryKeyboardKey.register(GLFW_KEY_F11, DOWN, e->toggleFullScreen());
	}
	
	public long getHandle(){
		return handle;
	}
	
	public void onDestroy(Runnable event){
		onDestroy.add(event);
	}
	
	public boolean isIconified(){
		return iconified.get();
	}
	
	public void grabContext(){
		requireCreated();
		glfwMakeContextCurrent(handle);
	}
}

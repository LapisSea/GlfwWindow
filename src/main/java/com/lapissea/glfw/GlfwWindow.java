package com.lapissea.glfw;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * A GLFW window.
 * <p>
 * State getters ({@link #size()}, {@link #isMaximized()}, ...) report the window's state as last reported by the
 * platform. Requests ({@link #setSize(int, int)}, {@link #maximize()}, ...) always go through GLFW and take effect when the
 * platform applies them \u2014 on some platforms, several events later. Before {@link #init()}, the same requests configure
 * how the window is created instead.
 * <p>
 * As with GLFW itself, everything except reading state must happen on the main thread. Listen to events through
 * {@link #callbacks()} or the higher-level {@code on*} methods, never by setting GLFW callbacks on
 * {@link #getHandle()} directly.
 */
@SuppressWarnings("unused")
public class GlfwWindow{
	
	static{ initGLFW(); }
	
	private static boolean INITED;
	public static synchronized void initGLFW(){
		if(INITED) return;
		INITED = true;
		GlfwMonitor.init();
	}
	
	public enum FullScreenStyle{
		EXCLUSIVE,
		BORDERLESS
	}
	
	public enum Cursor{
		NORMAL(GLFW_CURSOR_NORMAL),
		HIDDEN(GLFW_CURSOR_HIDDEN),
		DISABLED(GLFW_CURSOR_DISABLED),
		CAPTURED(GLFW_CURSOR_CAPTURED),
		;
		
		private final int flag;
		
		Cursor(int flag){ this.flag = flag; }
	}
	
	protected long handle = NULL;
	
	private final GlfwCallbacks callbacks = new GlfwCallbacks();
	
	private final Listeners<Consumer<GlfwKeyboardEvent>>  keyEvents         = new Listeners<>();
	private final Listeners<Consumer<GlfwMouseEvent>>     mouseButtonEvents = new Listeners<>();
	private final Listeners<Consumer<GlfwMouseMoveEvent>> mouseMoveEvents   = new Listeners<>();
	private final Listeners<Consumer<GlfwScrollEvent>>    scrollEvents      = new Listeners<>();
	private final Listeners<Runnable>                     placementChanges  = new Listeners<>();
	
	private String title = "";
	
	private Size  size = new Size(600, 400);
	private Point pos;
	
	private boolean maximized;
	private boolean iconified;
	private boolean focused;
	
	private GlfwMonitor     fullScreenMonitor;
	private FullScreenStyle fullScreenStyle        = FullScreenStyle.EXCLUSIVE;
	private FullScreenStyle defaultFullScreenStyle = FullScreenStyle.EXCLUSIVE;
	private boolean         decoratedBeforeFullScreen;
	
	private boolean visibleOnCreate;
	
	private BorderlessCover borderlessCover;
	private boolean         borderlessOversize;
	private Cursor          cursorMode = Cursor.NORMAL;
	private Point           cursorPos  = new Point(0, 0);
	
	private Point normalPos, prevNormalPos;
	private Size normalSize, prevNormalSize;
	private long normalPosChangedNs, normalSizeChangedNs;
	private boolean maximizedBeforeIconify, maximizedBeforeFullScreen;
	
	private final ArrayList<Runnable> onDestroy = new ArrayList<>(1);
	
	@SuppressWarnings("resource")
	public GlfwWindow(){
		callbacks.windowPos.add((w, x, y) -> {
			pos = new Point(x, y);
			if(inNormalState()){
				long now = System.nanoTime();
				if(!isRecent(normalPosChangedNs, now)) prevNormalPos = normalPos;
				normalPos = pos;
				normalPosChangedNs = now;
			}
			placementChanged();
			syncModeFromGlfw();
		});
		callbacks.windowSize.add((w, width, height) -> {
			size = new Size(width, height);
			if(justUnmaximized()){
				enforceNormalSize();
			}else if(inNormalState() && !size.isEmpty()){
				long now = System.nanoTime();
				if(!isRecent(normalSizeChangedNs, now)) prevNormalSize = normalSize;
				normalSize = size;
				normalSizeChangedNs = now;
			}
			placementChanged();
			syncModeFromGlfw();
		});
		callbacks.windowMaximize.add((w, max) -> {
			boolean was = maximized;
			maximized = max;
			if(max) undoRecentNormalChange();
			else if(was){
				unmaximizedNs = System.nanoTime();
				enforceNormalSize();
			}
			placementChanged();
		});
		callbacks.windowIconify.add((w, ico) -> {
			if(ico) maximizedBeforeIconify = maximized;
			iconified = ico;
			placementChanged();
		});
		callbacks.windowFocus.add((w, f) -> focused = f);
		callbacks.key.add((w, key, scancode, action, mods) -> {
			var type = action == GLFW_PRESS? GlfwKeyboardEvent.Type.DOWN :
			           action == GLFW_REPEAT? GlfwKeyboardEvent.Type.HOLD : GlfwKeyboardEvent.Type.UP;
			if(!keyEvents.isEmpty()){
				var e = new GlfwKeyboardEvent(this, key, type);
				keyEvents.dispatch(l -> l.accept(e));
			}
		});
		callbacks.mouseButton.add((w, button, action, mods) -> {
			if(mouseButtonEvents.isEmpty()) return;
			var type = action == GLFW_PRESS? GlfwMouseEvent.Type.DOWN :
			           action == GLFW_REPEAT? GlfwMouseEvent.Type.HOLD : GlfwMouseEvent.Type.UP;
			var e = new GlfwMouseEvent(this, button, type);
			mouseButtonEvents.dispatch(l -> l.accept(e));
		});
		callbacks.cursorPos.add((w, x, y) -> {
			var prev = cursorPos;
			cursorPos = new Point((int)x, (int)y);
			if(!mouseMoveEvents.isEmpty()){
				var e = new GlfwMouseMoveEvent(this, prev, cursorPos);
				mouseMoveEvents.dispatch(l -> l.accept(e));
			}
		});
		callbacks.scroll.add((w, x, y) -> {
			if(scrollEvents.isEmpty()) return;
			var e = new GlfwScrollEvent(this, x, y);
			scrollEvents.dispatch(l -> l.accept(e));
		});
	}
	
	/**
	 * Some platforms omit size events (e.g., Wayland for self-initiated resizes, X11 for windows created maximized).
	 * If GLFW already reflects the change, the missing event is synthesized here so listeners observe consistent behavior.
	 */
	private void syncFromGlfw(){
		if(!isCreated()) return;
		
		var siz = getSizePtr(GLFW::glfwGetWindowSize);
		if(siz.width() != size.width() || siz.height() != size.height()){
			int w = siz.width(), h = siz.height();
			callbacks.windowSize.dispatch(l -> l.invoke(handle, w, h));
		}
		
		if(hasPositions() && fullScreenMonitor == null){
			var poz = getPointPtr(GLFW::glfwGetWindowPos);
			if(!poz.equals(pos)){
				int x = poz.x(), y = poz.y();
				callbacks.windowPos.dispatch(l -> l.invoke(handle, x, y));
			}
		}
		
		syncModeFromGlfw();
	}
	
	private void syncModeFromGlfw(){
		boolean max = glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE;
		if(max != maximized){
			callbacks.windowMaximize.dispatch(l -> l.invoke(handle, max));
		}
		boolean ico = glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE;
		if(ico != iconified){
			callbacks.windowIconify.dispatch(l -> l.invoke(handle, ico));
		}
	}
	
	private long    unmaximizedNs;
	private boolean enforcingNormalSize;
	
	private boolean justUnmaximized(){
		return unmaximizedNs != 0 && System.nanoTime() - unmaximizedNs<500_000_000L;
	}
	
	/**
	 * Some platforms do not restore the pre-maximize size (GLFW on Wayland keeps the maximized size, X11 window managers may subtract the frame from a window created maximized).
	 * Sizes reported right after unmaximizing are corrected to the remembered pre-maximize value.
	 */
	private void enforceNormalSize(){
		if(enforcingNormalSize || maximized || iconified || fullScreenMonitor != null) return;
		if(normalSize == null || normalSize.equals(size) || !isCreated()) return;
		if(glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE) return;
		
		enforcingNormalSize = true;
		try{
			glfwSetWindowSize(handle, normalSize.width(), normalSize.height());
			syncFromGlfw();
		}finally{
			enforcingNormalSize = false;
		}
	}
	
	private boolean inNormalState(){
		if(maximized || iconified || fullScreenMonitor != null) return false;
		return !isCreated() ||
		       glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_FALSE &&
		       glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_FALSE;
	}
	
	/// Changes closer together than this are one burst, the prev values hold the geometry from before it.
	private static final long NORMAL_BURST_NS = 100_000_000L;
	private static boolean isRecent(long changedNs, long now){
		return changedNs != 0 && now - changedNs<NORMAL_BURST_NS;
	}
	
	/// The geometry reported just before maximizing was most likely already maximized geometry.
	/// macOS animates zooming, reporting a whole burst of in-between sizes first.
	private void undoRecentNormalChange(){
		long now = System.nanoTime();
		if(prevNormalSize != null && isRecent(normalSizeChangedNs, now) && normalSize.equals(size)) normalSize = prevNormalSize;
		if(prevNormalPos != null && isRecent(normalPosChangedNs, now) && normalPos.equals(pos)) normalPos = prevNormalPos;
	}
	private void placementChanged(){
		placementChanges.dispatch(Runnable::run);
	}
	
	//=============== Events ================//
	
	/// Usable before {@link #init}.
	public GlfwCallbacks callbacks(){ return callbacks; }
	
	public Registration onKey(Consumer<GlfwKeyboardEvent> listener){ return keyEvents.add(listener); }
	public Registration onKey(int key, GlfwKeyboardEvent.Type type, Consumer<GlfwKeyboardEvent> listener){
		Objects.requireNonNull(type);
		Objects.requireNonNull(listener);
		return keyEvents.add(e -> {
			if(e.getKey() == key && e.getType() == type){
				listener.accept(e);
			}
		});
	}
	
	public Registration onMouseButton(Consumer<GlfwMouseEvent> listener)  { return mouseButtonEvents.add(listener); }
	public Registration onMouseMove(Consumer<GlfwMouseMoveEvent> listener){ return mouseMoveEvents.add(listener); }
	public Registration onScroll(Consumer<GlfwScrollEvent> listener)      { return scrollEvents.add(listener); }
	/// Called on window, moved, resized, maximized, minimized or restored
	public Registration onPlacementChange(Runnable listener){ return placementChanges.add(listener); }
	
	//=============== Creation ================//
	
	public static final class Initializer{
		
		private interface SurfaceAPI<SELF extends SurfaceAPI<SELF>>{
			void preInit();
			void postInit(GlfwWindow window);
			
			default SELF withVersion(double version){
				return withVersion((int)version, (int)((version%1)*10));
			}
			SELF withVersion(int major, int minor);
		}
		
		public interface OpenGLSurfaceAPI extends SurfaceAPI<OpenGLSurfaceAPI>{
			
			enum Profile{
				ANY(GLFW_OPENGL_ANY_PROFILE),
				CORE(GLFW_OPENGL_CORE_PROFILE),
				COMPATIBILITY(GLFW_OPENGL_COMPAT_PROFILE),
				;
				private final int handle;
				
				Profile(int handle){
					this.handle = handle;
				}
			}
			
			OpenGLSurfaceAPI withSamples(int samples);
			OpenGLSurfaceAPI forwardCompatible();
			OpenGLSurfaceAPI withProfile(Profile profile);
			OpenGLSurfaceAPI withDebugContext();
		}
		
		public interface VulkanSurfaceAPI extends SurfaceAPI<VulkanSurfaceAPI>{
		}
		
		private static final class OpenGLImpl implements OpenGLSurfaceAPI{
			
			private int     versionMajor      = 3;
			private int     versionMinor      = 3;
			private int     samples           = 1;
			private boolean forwardCompatible = false;
			private boolean debugContext      = false;
			private Profile profile           = Profile.CORE;
			
			@Override
			public void preInit(){
				glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
				if(samples>1) glfwWindowHint(GLFW_SAMPLES, samples);
				glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, versionMajor);
				glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, versionMinor);
				hint(GLFW_OPENGL_FORWARD_COMPAT, forwardCompatible);
				glfwWindowHint(GLFW_OPENGL_PROFILE, profile.handle);
				hint(GLFW_OPENGL_DEBUG_CONTEXT, debugContext);
			}
			
			@Override
			public void postInit(GlfwWindow window){
				window.makeContextCurrent();
				GL.createCapabilities();
			}
			
			@Override
			public OpenGLSurfaceAPI withVersion(int major, int minor){
				versionMajor = major;
				versionMinor = minor;
				return this;
			}
			@Override
			public OpenGLSurfaceAPI withSamples(int samples){
				if(samples<=0) throw new IllegalArgumentException("Samples must be 1 or greater");
				this.samples = samples;
				return this;
			}
			@Override
			public OpenGLSurfaceAPI forwardCompatible(){
				forwardCompatible = true;
				return this;
			}
			@Override
			public OpenGLSurfaceAPI withProfile(Profile profile){
				this.profile = Objects.requireNonNull(profile);
				return this;
			}
			@Override
			public OpenGLSurfaceAPI withDebugContext(){
				this.debugContext = true;
				return this;
			}
		}
		
		private static final class VulkanImpl implements VulkanSurfaceAPI{
			
			@Override
			public void preInit(){
				glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
			}
			@Override
			public void postInit(GlfwWindow window){ }
			
			//Retained only for source compatibility. The Vulkan version is chosen at Vulkan instance creation
			@Override
			public VulkanSurfaceAPI withVersion(int major, int minor){ return this; }
		}
		
		private SurfaceAPI<?> api              = new OpenGLImpl();
		private boolean       resizeable       = true;
		private boolean       decorated        = true;
		private boolean       transparent      = false;
		private boolean       alwaysOnTop      = false;
		private boolean       mousePassthrough = false;
		private boolean       doubleBuffer     = false;
		private boolean       focusOnShow      = false;
		
		private Initializer(){
		}
		
		public Initializer withVulkan(){ return withVulkan(Function.identity()); }
		public Initializer withVulkan(Function<VulkanSurfaceAPI, VulkanSurfaceAPI> settings){
			api = Objects.requireNonNull(settings.apply(new VulkanImpl()));
			return this;
		}
		public Initializer withOpenGL(){ return withOpenGL(Function.identity()); }
		public Initializer withOpenGL(Function<OpenGLSurfaceAPI, OpenGLSurfaceAPI> settings){
			api = Objects.requireNonNull(settings.apply(new OpenGLImpl()));
			return this;
		}
		
		public Initializer resizeable(boolean resizeable){
			this.resizeable = resizeable;
			return this;
		}
		public Initializer decorated(boolean decorated){
			this.decorated = decorated;
			return this;
		}
		public Initializer transparent(boolean transparent){
			this.transparent = transparent;
			return this;
		}
		public Initializer alwaysOnTop(boolean alwaysOnTop){
			this.alwaysOnTop = alwaysOnTop;
			return this;
		}
		public Initializer mousePassthrough(boolean mousePassthrough){
			this.mousePassthrough = mousePassthrough;
			return this;
		}
		public Initializer doubleBuffer(boolean doubleBuffer){
			this.doubleBuffer = doubleBuffer;
			return this;
		}
		public Initializer focusOnShow(boolean focusOnShow){
			this.focusOnShow = focusOnShow;
			return this;
		}
	}
	
	public GlfwWindow init()                                        { return init(new Initializer()); }
	public GlfwWindow init(Function<Initializer, Initializer> setup){ return init(setup.apply(new Initializer())); }
	
	private GlfwWindow init(Initializer args){
		if(isCreated()) throw new IllegalStateException("Already created");
		
		hint(GLFW_RESIZABLE, args.resizeable);
		hint(GLFW_DECORATED, args.decorated);
		
		// Show when done, after window is fully initialized
		hint(GLFW_VISIBLE, false);
		
		hint(GLFW_TRANSPARENT_FRAMEBUFFER, args.transparent);
		hint(GLFW_FLOATING, args.alwaysOnTop);
		hint(GLFW_MOUSE_PASSTHROUGH, args.mousePassthrough);
		hint(GLFW_DOUBLEBUFFER, args.doubleBuffer);
		hint(GLFW_FOCUS_ON_SHOW, args.focusOnShow);
		
		// Maximized after placement:
		// moving a window that is already maximized drags it off its monitor, instead of the placement becoming the geometry it restores to.
		hint(GLFW_MAXIMIZED, false);
		
		var fullScreenOn = fullScreenMonitor;
		var fullStyle    = fullScreenStyle;
		
		fullScreenMonitor = null;
		boolean createMaximized = maximized;
		
		args.api.preInit();
		
		handle = glfwCreateWindow(size.width(), size.height(), title, NULL, NULL);
		if(handle == NULL){
			var desc  = BufferUtils.createPointerBuffer(1);
			int code  = glfwGetError(desc);
			var cause = code == GLFW_NO_ERROR? "" : ": " + desc.getStringUTF8(0);
			throw new IllegalStateException("Failed to create window" + cause);
		}
		
		if(args.api instanceof Initializer.VulkanSurfaceAPI){
			glfwSetWindowSizeLimits(handle, 1, 1, GLFW_DONT_CARE, GLFW_DONT_CARE);
		}
		
		normalSize = size;
		if(fullScreenMonitor == null && hasPositions()){
			if(pos == null){
				var m = bestMonitor();
				if(m != null){
					pos = new Point(
						(int)m.bounds.getCenterX() - size.width()/2,
						(int)m.bounds.getCenterY() - size.height()/2
					);
				}
			}
			if(pos != null){
				glfwSetWindowPos(handle, pos.x(), pos.y());
				placeOnShow = pos;
			}
			normalPos = pos;
		}
		
		callbacks.install(handle);
		readState();
		
		if(!maximized && fullScreenMonitor == null){
			normalSize = size;
			if(pos != null) normalPos = pos;
		}
		
		glfwSetInputMode(handle, GLFW_CURSOR, cursorMode.flag);
		if(createMaximized){
			maximize();
		}
		
		if(fullScreenOn != null){
			if(glfwGetPlatform() != GLFW_PLATFORM_WIN32 && !isVisible()){
				fullScreenOnShow = fullScreenOn;
			}else setFullScreen(fullScreenOn, fullStyle);
			fullScreenStyle = fullStyle;
		}
		
		args.api.postInit(this);
		
		if(visibleOnCreate) show();
		else syncFromGlfw();
		
		return this;
	}
	
	private boolean     maximizeOnShow;
	private Point       placeOnShow;
	private GlfwMonitor fullScreenOnShow;
	
	private void correctPlacement(){
		var target = placeOnShow;
		placeOnShow = null;
		
		if(target == null || glfwGetPlatform() != GLFW_PLATFORM_X11) return;
		
		int[] x = {0}, y = {0};
		glfwGetWindowPos(handle, x, y);
		
		if(x[0] != target.x() || y[0] != target.y()){
			glfwSetWindowPos(handle, target.x(), target.y());
		}
	}
	
	private static boolean maximizesOnShow(){
		var p = glfwGetPlatform();
		return p == GLFW_PLATFORM_WIN32 || p == GLFW_PLATFORM_X11;
	}
	
	private void readState(){
		size = getSizePtr(GLFW::glfwGetWindowSize);
		
		if(hasPositions()){
			pos = getPointPtr(GLFW::glfwGetWindowPos);
		}
		
		maximized = glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE;
		iconified = glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE;
		focused = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
		
		cursorPos = getPointD2IPtr(GLFW::glfwGetCursorPos);
	}
	
	// Fuck you. I get it but still, fuck you
	public static boolean hasPositions(){
		return glfwGetPlatform() != GLFW_PLATFORM_WAYLAND;
	}
	
	private static void hint(int prop, boolean val){
		glfwWindowHint(prop, val? GLFW_TRUE : GLFW_FALSE);
	}
	
	public boolean isCreated(){ return handle != NULL; }
	public long getHandle()   { return handle; }
	private void requireCreated(){
		if(!isCreated()) throw new IllegalStateException("Window is not created");
	}
	
	public void destroy(){
		requireCreated();
		
		onDestroy.forEach(Runnable::run);
		onDestroy.clear();
		
		GlfwCallbacks.free(handle);
		glfwDestroyWindow(handle);
		
		handle = NULL;
	}
	
	public void onDestroy(Runnable event){
		onDestroy.add(event);
	}
	
	//======== State ========//
	
	public String title(){ return title; }
	/// Window content area size in screen coords
	public Size size(){ return size; }
	/// @return content area pos in screen coords, null if unknown (Wayland)
	public Point pos(){ return pos; }
	public boolean isMaximized() { return maximized; }
	public boolean isIconified() { return iconified; }
	public boolean isFocused()   { return focused; }
	public boolean isFullScreen(){ return fullScreenMonitor != null; }
	/// @return null if not full screen
	public FullScreenStyle fullScreenStyle(){ return fullScreenMonitor != null? fullScreenStyle : null; }
	public GlfwMonitor fullScreenMonitor(){ return fullScreenMonitor; }
	public Cursor cursorMode()            { return cursorMode; }
	public Point cursorPos()              { return cursorPos; }
	
	/**
	 * Snapped to a screen edge, quadrant or snap layout. Only Windows reports this.<br/>
	 * A snapped window is saved with the geometry it returns to when it is un-snapped
	 */
	public boolean isSnapped(){
		if(!isCreated() || maximized || iconified || fullScreenMonitor != null) return false;
		return glfwGetPlatform() == GLFW_PLATFORM_WIN32 && Win32Placement.isArranged(handle);
	}
	
	public boolean isVisible(){
		if(!isCreated()) return visibleOnCreate;
		return glfwGetWindowAttrib(handle, GLFW_VISIBLE) == GLFW_TRUE;
	}
	/**
	 * The onscreen part of the framebuffer, in framebuffer pixels with the origin at the top left.
	 * This is the whole framebuffer, except under {@link #setBorderlessOversize(boolean) borderless oversize},
	 * where the window extends one pixel past the monitor. <br/>
	 * For example, when rendering with OpenGL, whose viewport origin is at the bottom left, account for this area with:
	 * <pre>{@code
	 * var a = window.visibleArea();
	 * glViewport(a.x(), window.framebufferSize().height() - a.y() - a.height(), a.width(), a.height());
	 * }</pre>
	 */
	public Area visibleArea(){
		var fb      = framebufferSize();
		var cover   = borderlessCover;
		var monitor = fullScreenMonitor;
		
		if(cover == null || monitor == null || size.isEmpty()){
			return new Area(0, 0, fb.width(), fb.height());
		}
		
		double sx = fb.width()/(double)size.width(), sy = fb.height()/(double)size.height();
		return new Area(
			(int)Math.round(cover.visibleX()*sx), (int)Math.round(cover.visibleY()*sy),
			(int)Math.round(monitor.bounds.width*sx), (int)Math.round(monitor.bounds.height*sy)
		);
	}
	
	/**
	 * Get size from GLFW, can differ from {@link #size()} on high DPI displays.
	 */
	public Size framebufferSize(){
		requireCreated();
		return getSizePtr(GLFW::glfwGetFramebufferSize);
	}
	
	public boolean isKeyDown(int key){
		requireCreated();
		return glfwGetKey(handle, key) == GLFW_PRESS;
	}
	public boolean isMouseButtonDown(int button){
		requireCreated();
		return glfwGetMouseButton(handle, button) == GLFW_PRESS;
	}
	public boolean shouldClose(){
		if(!isCreated()){
			return true;
		}
		return glfwWindowShouldClose(handle);
	}
	
	//======== Requests ========//
	
	public void setTitle(String title){
		this.title = Objects.requireNonNull(title);
		if(isCreated()){
			glfwSetWindowTitle(handle, title);
		}
	}
	public void setSize(int width, int height){
		if(isCreated()){
			glfwSetWindowSize(handle, width, height);
			syncFromGlfw();
		}else{
			size = new Size(width, height);
		}
	}
	
	/// May be ignored on Wayland
	public void setPos(int x, int y){
		if(isCreated()){
			if(!isVisible()) placeOnShow = new Point(x, y);
			if(hasPositions()) glfwSetWindowPos(handle, x, y);
			syncFromGlfw();
		}else pos = new Point(x, y);
	}
	
	public void show(){
		if(!isCreated()){
			visibleOnCreate = true;
			return;
		}
		
		boolean max = maximizeOnShow;
		maximizeOnShow = false;
		
		if(max && glfwGetPlatform() == GLFW_PLATFORM_WIN32){
			Win32Placement.showMaximized(handle);
		}else{
			glfwShowWindow(handle);
			correctPlacement();
			if(max) glfwMaximizeWindow(handle);
		}
		
		var fullScreen = fullScreenOnShow;
		fullScreenOnShow = null;
		
		if(fullScreen != null){
			setFullScreen(fullScreen, fullScreenStyle);
		}
		syncFromGlfw();
	}
	
	public void hide(){
		if(isCreated()){
			glfwHideWindow(handle);
		}else{
			visibleOnCreate = false;
		}
	}
	
	public void maximize(){
		if(!isCreated()){
			maximized = true;
			return;
		}
		if(maximizesOnShow() && !isVisible()){
			maximizeOnShow = true;
			return;
		}
		glfwMaximizeWindow(handle);
		syncFromGlfw();
	}
	
	public void iconify(){
		requireCreated();
		glfwIconifyWindow(handle);
		syncFromGlfw();
	}
	
	/// Exit maximized or minimized state
	public void restore(){
		if(isCreated()){
			maximizeOnShow = false;
			glfwRestoreWindow(handle);
			syncFromGlfw();
		}else maximized = false;
	}
	
	public void focus(){
		requireCreated();
		glfwFocusWindow(handle);
	}
	
	public void requestClose(){
		requireCreated();
		glfwSetWindowShouldClose(handle, true);
	}
	
	/**
	 * Full screen in the {@link #setFullScreenStyle default style}.
	 *
	 * @param monitor monitor to cover. Null means to exit full screen
	 */
	public void setFullScreen(GlfwMonitor monitor){
		setFullScreen(monitor, defaultFullScreenStyle);
	}
	/**
	 * @param monitor monitor to cover. Null means to exit full screen
	 */
	public void setFullScreen(GlfwMonitor monitor, FullScreenStyle style){
		Objects.requireNonNull(style);
		if(!isCreated()){
			fullScreenMonitor = monitor;
			fullScreenStyle = style;
			return;
		}
		if(Objects.equals(monitor, fullScreenMonitor) && (monitor == null || style == fullScreenStyle)){
			return;
		}
		
		boolean wasFullScreen = fullScreenMonitor != null;
		if(wasFullScreen && monitor != null && monitor.equals(fullScreenMonitor) && usesBorderless(style) == usesBorderless(fullScreenStyle)){
			// Both styles are the same full screen on this platform.
			fullScreenStyle = style;
			placementChanged();
			return;
		}
		
		if(wasFullScreen){
			leaveFullScreen(monitor == null);
		}
		
		if(monitor != null){
			if(!wasFullScreen){
				maximizedBeforeFullScreen = maximized;
				// Windows and Wayland keep the maximized flag set during full screen, so a later maximize is ignored and Windows loses
				// the restore geometry. X11 window managers handle this themselves
				if(maximized && glfwGetPlatform() != GLFW_PLATFORM_X11) glfwRestoreWindow(handle);
			}
			
			fullScreenMonitor = monitor;
			fullScreenStyle = style;
			
			var b = monitor.bounds;
			if(usesBorderless(style)){
				decoratedBeforeFullScreen = glfwGetWindowAttrib(handle, GLFW_DECORATED) == GLFW_TRUE;
				glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE);
				applyBorderlessCover();
			}else{
				glfwSetWindowMonitor(handle, monitor.handle, b.x, b.y, b.width, b.height, monitor.refreshRate);
			}
		}
		placementChanged();
		syncFromGlfw();
	}
	
	/**
	 * @param restoreMaximized false when switching straight to another full screen
	 */
	private void leaveFullScreen(boolean restoreMaximized){
		var style = fullScreenStyle;
		fullScreenMonitor = null;
		
		borderlessCover = null;
		
		var p = normalPos != null? normalPos : new Point(0, 0);
		var s = normalSize != null? normalSize : size;
		
		if(usesBorderless(style)){
			glfwSetWindowAttrib(handle, GLFW_DECORATED, decoratedBeforeFullScreen? GLFW_TRUE : GLFW_FALSE);
			glfwSetWindowSize(handle, s.width(), s.height());
			glfwSetWindowPos(handle, p.x(), p.y());
		}else{
			glfwSetWindowMonitor(handle, NULL, p.x(), p.y(), s.width(), s.height(), GLFW_DONT_CARE);
		}
		
		if(restoreMaximized && maximizedBeforeFullScreen && glfwGetPlatform() != GLFW_PLATFORM_X11) glfwMaximizeWindow(handle);
	}
	
	private void applyBorderlessCover(){
		var monitor = fullScreenMonitor;
		var b       = monitor.bounds;
		var cover   = borderlessOversize? BorderlessCover.of(monitor) : new BorderlessCover(b.x, b.y, b.width, b.height, 0, 0);
		borderlessCover = cover;
		glfwSetWindowPos(handle, cover.x(), cover.y());
		glfwSetWindowSize(handle, cover.width(), cover.height());
	}
	
	/**
	 * Some GPU drivers switch a window that exactly covers a monitor into exclusive full screen, which changes the
	 * display mode just like {@link FullScreenStyle#EXCLUSIVE} would. With this on, a
	 * {@link FullScreenStyle#BORDERLESS borderless} window on Windows is a pixel larger than its monitor instead,
	 * past an edge with no other monitor beyond it. Render into {@link #visibleArea()} to keep that pixel off screen.
	 * Has no effect elsewhere, other platforms do not switch display modes for full screen.
	 */
	public void setBorderlessOversize(boolean oversize){
		if(borderlessOversize == oversize) return;
		borderlessOversize = oversize;
		if(isCreated() && borderlessCover != null){
			applyBorderlessCover();
			placementChanged();
			syncFromGlfw();
		}
	}
	public boolean borderlessOversize(){ return borderlessOversize; }
	
	private static boolean usesBorderless(FullScreenStyle style){
		return style == FullScreenStyle.BORDERLESS && glfwGetPlatform() == GLFW_PLATFORM_WIN32;
	}
	
	/**
	 * The style {@link #setFullScreen(GlfwMonitor)} and {@link #toggleFullScreen()} use. Switches a window that is
	 * already full screen over to it.
	 */
	public void setFullScreenStyle(FullScreenStyle style){
		defaultFullScreenStyle = Objects.requireNonNull(style);
		if(isCreated() && fullScreenMonitor != null && fullScreenStyle != style) setFullScreen(fullScreenMonitor, style);
	}
	public FullScreenStyle defaultFullScreenStyle(){ return defaultFullScreenStyle; }
	
	public void toggleFullScreen(){
		setFullScreen(isFullScreen()? null : bestMonitor());
	}
	
	public Registration autoF11Toggle(){
		return onKey(GLFW_KEY_F11, GlfwKeyboardEvent.Type.DOWN, e -> toggleFullScreen());
	}
	
	public void setCursorMode(Cursor mode){
		cursorMode = Objects.requireNonNull(mode);
		if(isCreated()) glfwSetInputMode(handle, GLFW_CURSOR, mode.flag);
	}
	
	public void setCursorPos(double x, double y){
		requireCreated();
		glfwSetCursorPos(handle, x, y);
	}
	public void centerMouse(){
		setCursorPos(size.width()/2D, size.height()/2D);
	}
	
	public void centerWindow(){
		var m = bestMonitor();
		if(m == null) return;
		setPos((int)m.bounds.getCenterX() - size.width()/2, (int)m.bounds.getCenterY() - size.height()/2);
	}
	
	/**
	 * @return find a monitor that best fits the window
	 */
	public GlfwMonitor bestMonitor(){
		Rectangle2D.Float rect = pos != null? new Rectangle2D.Float(pos.x(), pos.y(), size.width(), size.height()) : null;
		return GlfwMonitor.bestMonitor(rect);
	}
	
	public void setIcon(BufferedImage... images){
		if(images.length == 0) return;
		
		GLFWImage[] gImages = new GLFWImage[images.length];
		for(int i = 0; i<images.length; i++){
			var image = images[i];
			int w     = image.getWidth();
			int h     = image.getHeight();
			var bb    = BufferUtils.createByteBuffer(w*h*4);
			gImages[i] = GLFWImage.create().set(w, h, BuffUtil.imageToBuffer(image, bb));
		}
		setIcon(gImages);
	}
	public void setIcon(GLFWImage... images){
		requireCreated();
		if(images.length == 0) return;
		try(final GLFWImage.Buffer iconSet = GLFWImage.malloc(images.length)){
			for(int i = images.length - 1; i>=0; i--){
				iconSet.put(i, images[i]);
			}
			glfwSetWindowIcon(handle, iconSet);
		}
	}
	
	public void setUserPointer(PointerBuffer pointer){
		setUserPointer(pointer.address());
	}
	public void setUserPointer(long pointer){
		requireCreated();
		glfwSetWindowUserPointer(handle, pointer);
	}
	public long getUserPointer(){
		requireCreated();
		return glfwGetWindowUserPointer(handle);
	}
	
	public void makeContextCurrent(){
		requireCreated();
		glfwMakeContextCurrent(handle);
	}
	public void swapBuffers(){
		requireCreated();
		glfwSwapBuffers(handle);
	}
	
	//======== Event loop ========//
	
	public void pollEvents(){ glfwPollEvents(); }
	public void waitEvents(){ glfwWaitEvents(); }
	
	public void whileOpen(Runnable run){
		while(!shouldClose()){
			run.run();
		}
	}
	public CompletableFuture<Void> whileOpen(Runnable run, String name){
		return whileOpen(run, name, Thread::new);
	}
	public CompletableFuture<Void> whileOpen(Runnable run, String name, BiFunction<Runnable, String, Thread> newThread){
		var future = new CompletableFuture<Void>();
		newThread.apply(() -> {
			whileOpen(run);
			future.complete(null);
		}, name).start();
		return future;
	}
	/**
	 * Polls events until the window is asked to close, then hides it. Must run on the main thread.
	 */
	public void pollEventsWhileOpen(){
		whileOpen(() -> {
			LockSupport.parkNanos(1_000_000);
			pollEvents();
		});
		hide();
	}
	
	//======== Remembered placement ========//
	
	/**
	 * @return how the window is placed, with the geometry to restore to when it is maximized, minimized or full screen
	 */
	public WindowState captureState(){
		requireCreated();
		var              fullScreenOn = fullScreenMonitor != null? fullScreenMonitor : fullScreenOnShow;
		WindowState.Mode mode;
		if(fullScreenOn != null){
			mode = fullScreenStyle == FullScreenStyle.BORDERLESS? WindowState.Mode.BORDERLESS_FULL_SCREEN : WindowState.Mode.FULL_SCREEN;
		}else{
			mode = (maximized || maximizeOnShow || (iconified && maximizedBeforeIconify))? WindowState.Mode.MAXIMIZED : WindowState.Mode.NORMAL;
		}
		
		Point p = normalPos;
		Size  s = normalSize != null? normalSize : size;
		if((maximized || iconified || isSnapped()) &&
		   fullScreenMonitor == null &&
		   glfwGetPlatform() == GLFW_PLATFORM_WIN32){
			// Windows has the geometry it restores to so tracking can miss changes made while maximized
			var restored = Win32Placement.read(handle);
			if(restored != null){
				p = restored.pos();
				s = restored.size();
				if(iconified){
					mode = restored.restoreToMaximized()? WindowState.Mode.MAXIMIZED : WindowState.Mode.NORMAL;
				}
			}
		}
		if(p == null && hasPositions()) p = pos;
		
		return new WindowState(mode, hasPositions()? p : null, s, fullScreenOn != null? WindowState.MonitorRef.of(fullScreenOn) : null);
	}
	
	/**
	 * Before {@link #init} this sets how the window is created
	 */
	public void applyState(WindowState state){
		Size  s = state.normalSize();
		Point p = hasPositions()? state.normalPos() : null;
		if(p != null){
			var rect = new Rectangle2D.Double(p.x(), p.y(), s.width(), s.height());
			if(GlfwMonitor.moveToVisible(rect)){
				p = new Point((int)rect.getX(), (int)rect.getY());
				s = new Size((int)rect.getWidth(), (int)rect.getHeight());
			}
		}
		var fullScreen = state.mode().isFullScreen();
		var style      = state.mode() == WindowState.Mode.BORDERLESS_FULL_SCREEN? FullScreenStyle.BORDERLESS : FullScreenStyle.EXCLUSIVE;
		var monitor    = fullScreen? state.monitor().find().orElse(null) : null;
		var max        = state.mode() == WindowState.Mode.MAXIMIZED || fullScreen && monitor == null;
		
		if(!isCreated()){
			size = s;
			pos = p;
			maximized = max;
			fullScreenMonitor = monitor;
			fullScreenStyle = style;
			if(monitor != null) defaultFullScreenStyle = style;
			maximizedBeforeFullScreen = false;
			return;
		}
		
		setFullScreen(null);
		
		if(maximized || iconified){
			glfwRestoreWindow(handle);
		}
		
		glfwSetWindowSize(handle, s.width(), s.height());
		
		if(p != null){
			glfwSetWindowPos(handle, p.x(), p.y());
		}
		
		normalSize = s;
		normalPos = p;
		
		if(max) glfwMaximizeWindow(handle);
		if(monitor != null){
			defaultFullScreenStyle = style;
			setFullScreen(monitor, style);
		}
		syncFromGlfw();
	}
	
	/**
	 * Restores the placement saved in the file path and keeps the file up to date. Saves when window idle and on exit
	 */
	public void autoSaveState(Path file){ autoSaveState(file, Duration.ofSeconds(1)); }
	public void autoSaveState(Path file, Duration settleTime){
		Objects.requireNonNull(file);
		WindowState.load(file).ifPresent(this::applyState);
		
		var saver = new StateSaver(file, settleTime.toNanos());
		var reg = onPlacementChange(() -> {
			if(isCreated()){
				saver.offer(captureState());
			}
		});
		onDestroy(() -> {
			reg.remove();
			saver.finish(captureState());
		});
	}
	
	private static final class StateSaver{
		
		private final Path file;
		
		private final    long        settleNs;
		private final    Thread      thread;
		private volatile WindowState pending;
		private volatile long        pendingAt;
		
		private WindowState saved;
		
		private volatile boolean done;
		
		StateSaver(Path file, long settleNs){
			this.file = file;
			this.settleNs = settleNs;
			thread = new Thread(this::run, "GlfwWindowStateSaver");
			thread.setDaemon(true);
			thread.start();
		}
		
		void offer(WindowState state){
			pendingAt = System.nanoTime();
			pending = state;
			LockSupport.unpark(thread);
		}
		
		private void run(){
			while(!done){
				var state = pending;
				
				if(state == null){
					LockSupport.park(this);
					continue;
				}
				
				long wait = pendingAt + settleNs - System.nanoTime();
				if(wait>0){
					LockSupport.parkNanos(this, wait);
					continue;
				}
				
				if(pending == state){
					pending = null;
					write(state);
				}
			}
		}
		
		private synchronized void write(WindowState state){
			if(state.equals(saved)) return;
			try{
				state.save(file);
				saved = state;
			}catch(IOException e){
				throw new UncheckedIOException("Failed to save window state to " + file, e);
			}
		}
		
		void finish(WindowState last){
			done = true;
			pending = null;
			LockSupport.unpark(thread);
			write(last);
		}
	}
	
	@Override
	public String toString(){
		return "GlfwWindow{" + title + "}";
	}
	
	
	private interface Int2PtrGet{
		void get(long window, int[] a, int[] b);
	}
	
	private interface Double2PtrGet{
		void get(long window, double[] a, double[] b);
	}
	
	private Size getSizePtr(Int2PtrGet fn){
		int[] w = {0}, h = {0};
		fn.get(handle, w, h);
		return new Size(w[0], h[0]);
	}
	private Point getPointPtr(Int2PtrGet fn){
		int[] w = {0}, h = {0};
		fn.get(handle, w, h);
		return new Point(w[0], h[0]);
	}
	private Point getPointD2IPtr(Double2PtrGet fn){
		double[] w = {0}, h = {0};
		fn.get(handle, w, h);
		return new Point((int)w[0], (int)h[0]);
	}
}

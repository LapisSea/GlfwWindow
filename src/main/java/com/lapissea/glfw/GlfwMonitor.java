package com.lapissea.glfw;

import com.lapissea.util.PairM;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWVidMode;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.*;

public class GlfwMonitor{
	
	public static final class Rect extends Rectangle2D{
		
		public final int x, y, width, height;
		
		public Rect(int x, int y, int w, int h){
			this.x = x;
			this.y = y;
			width = w;
			height = h;
		}
		
		@Override
		public double getX(){
			return x;
		}
		
		@Override
		public double getY(){
			return y;
		}
		
		@Override
		public double getWidth(){
			return width;
		}
		
		@Override
		public double getHeight(){
			return height;
		}
		
		@Override
		public boolean isEmpty(){
			return (width<=0.0f) || (height<=0.0f);
		}
		
		
		@Override
		public void setRect(double x, double y, double w, double h){
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int outcode(double x, double y){
			int out = 0;
			if(width<=0){
				out |= OUT_LEFT|OUT_RIGHT;
			}else if(x<this.x){
				out |= OUT_LEFT;
			}else if(x>this.x + (double)width){
				out |= OUT_RIGHT;
			}
			if(height<=0){
				out |= OUT_TOP|OUT_BOTTOM;
			}else if(y<this.y){
				out |= OUT_TOP;
			}else if(y>this.y + (double)height){
				out |= OUT_BOTTOM;
			}
			return out;
		}
		
		@Override
		public Rectangle2D getBounds2D(){
			return new Float(x, y, width, height);
		}
		
		@Override
		public Rectangle2D createIntersection(Rectangle2D r){
			Rectangle2D dest;
			if(r instanceof Float){
				dest = new Rectangle2D.Float();
			}else{
				dest = new Rectangle2D.Double();
			}
			Rectangle2D.intersect(this, r, dest);
			return dest;
		}
		
		@Override
		public Rectangle2D createUnion(Rectangle2D r){
			Rectangle2D dest;
			if(r instanceof Float){
				dest = new Rectangle2D.Float();
			}else{
				dest = new Rectangle2D.Double();
			}
			Rectangle2D.union(this, r, dest);
			return dest;
		}
		
		@Override
		public String toString(){
			return "Rect[x=" + x +
			       ",y=" + y +
			       ",w=" + width +
			       ",h=" + height + "]";
		}
	}
	
	
	private static GlfwMonitor       PRIMARY_MONITOR = null;
	private static List<GlfwMonitor> MONITORS        = List.of();
	private static List<Rect>        MONITOR_GROUPS  = List.of();
	
	static void init(){
		glfwInit();
		update();
		glfwSetMonitorCallback(GLFWMonitorCallback.create((monitor, event) -> update()));
	}
	
	private static synchronized void update(){
		List<GlfwMonitor> monitors      = new ArrayList<>(1);
		List<Rect>        monitorGroups = new ArrayList<>(1);
		
		{
			PointerBuffer monitorPtrs = glfwGetMonitors();
			if(monitorPtrs == null || !monitorPtrs.hasRemaining()) return;
			for(int i = 0; i<monitorPtrs.capacity(); i++){
				monitors.add(new GlfwMonitor(monitorPtrs.get(i)));
			}
		}
		
		List<Rectangle2D> groups = new ArrayList<>(1);
		
		List<Rectangle2D> all = new ArrayList<>(1);
		all.addAll(monitors.stream().map(a -> a.bounds).collect(Collectors.toList()));
		for(GlfwMonitor m : monitors){
			
			Rectangle2D group  = new Rectangle2D.Float(m.bounds.x, m.bounds.y, m.bounds.width, m.bounds.height);
			boolean     change = true;
			while(change){
				change = false;
				
				for(Rectangle2D monitor : all){
					if(monitor.equals(group)) continue;
					
					boolean yFlush = group.getMaxY() == monitor.getMaxY() && group.getMinY() == monitor.getMinY();
					if(yFlush){
						boolean rightCon = group.getMaxX() == monitor.getMinX();
						boolean leftCon  = monitor.getMaxX() == group.getMinX();
						if(rightCon || leftCon){
							change = true;
							group = group.createUnion(monitor);
							
						}
					}
					
					boolean xFlush = group.getMaxX() == monitor.getMaxX() && group.getMinX() == monitor.getMinX();
					if(xFlush){
						boolean topCon    = group.getMaxY() == monitor.getMinY();
						boolean bottomCon = monitor.getMaxY() == group.getMinY();
						if(topCon || bottomCon){
							change = true;
							group = group.createUnion(monitor);
						}
					}
					
				}
			}
			
			if(!groups.contains(group)) groups.add(group);
			if(!all.contains(group)) all.add(group);
			
		}
		groups.stream().map(g -> new Rect((int)g.getX(), (int)g.getY(), (int)g.getWidth(), (int)g.getHeight())).forEach(monitorGroups::add);
		
		long ph = glfwGetPrimaryMonitor();
		PRIMARY_MONITOR = monitors.stream().filter(m -> m.handle == ph).findAny().orElse(null);
		
		MONITORS = List.copyOf(monitors);
		MONITOR_GROUPS = List.copyOf(monitorGroups);
	}
	
	public static synchronized GlfwMonitor getPrimaryMonitor(){
		if(PRIMARY_MONITOR == null) throw new IllegalStateException("No monitor");
		return PRIMARY_MONITOR;
	}
	
	public static synchronized List<GlfwMonitor> getMonitors(){ return MONITORS; }
	public static synchronized List<Rect> getGroups()         { return MONITOR_GROUPS; }
	
	
	/**
	 * @return true if rectangle has been modified
	 */
	public static synchronized boolean moveToVisible(Rectangle2D windowRect){
		if(getGroups().isEmpty()){
			return false;
		}
		
		if(getGroups().stream().noneMatch(group -> group.contains(windowRect))){
			
			Optional<PairM<Rect, Rectangle2D>> r =
				getGroups().stream()
				           .map(group -> new PairM<>(group, new Rectangle2D.Double(group.getX(), group.getY(), windowRect.getWidth(), windowRect.getHeight()).createIntersection(group)))
				           .max(Comparator.comparingDouble(a -> a.obj2.getWidth()*a.obj2.getHeight()));
			if(r.isPresent()){
				Rect group = r.get().obj1;
				
				double x, y,
					w = Math.min(windowRect.getWidth(), group.getWidth()),
					h = Math.min(windowRect.getHeight(), group.getHeight());
				
				if(windowRect.getMinX()<group.getMinX()) x = group.getMinX();
				else if(windowRect.getMaxX()>group.getMaxX()) x = group.getMaxX() - w;
				else x = windowRect.getX();
				
				if(windowRect.getMinY()<group.getMinY()) y = group.getMinY();
				else if(windowRect.getMaxY()>group.getMaxY()) y = group.getMaxY() - h;
				else y = windowRect.getY();
				
				windowRect.setRect(x, y, w, h);
				
				return true;
			}
		}
		
		return false;
	}
	
	//////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////
	
	final        long handle;
	public final int  refreshRate;
	public final Rect bounds;
	
	
	public GlfwMonitor(long handle){
		this.handle = handle;
		
		
		GLFWVidMode mode = glfwGetVideoMode(handle);
		
		refreshRate = mode.refreshRate();
		
		int[] xBuf = {0}, yBuf = {0};
		glfwGetMonitorPos(handle, xBuf, yBuf);
		bounds = new Rect(xBuf[0], yBuf[0], mode.width(), mode.height());
	}
	
	@Override
	public int hashCode(){
		return bounds.hashCode() + refreshRate;
	}
	
	@Override
	public boolean equals(Object obj){
		if(obj == this) return true;
		if(!(obj instanceof GlfwMonitor)) return false;
		GlfwMonitor other = (GlfwMonitor)obj;
		return other.handle == handle;
	}
	
	@Override
	public String toString(){
		return "GlfwMonitor{refreshRate=" + refreshRate + ", bounds=" + bounds + "}";
	}
	
}

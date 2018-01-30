package com.lapissea.glfw;

import com.lapissea.util.PairM;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWVidMode;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.*;

public class GlfwMonitor{
	
	public static final class Rect extends Rectangle2D{
		
		public final int x, y, width, height;
		
		public Rect(int x, int y, int w, int h){
			this.x=x;
			this.y=y;
			width=w;
			height=h;
		}
		
		@Override
		public double getX(){
			return (double)x;
		}
		
		@Override
		public double getY(){
			return (double)y;
		}
		
		@Override
		public double getWidth(){
			return (double)width;
		}
		
		@Override
		public double getHeight(){
			return (double)height;
		}
		
		@Override
		public boolean isEmpty(){
			return (width<=0.0f)||(height<=0.0f);
		}
		
		
		@Override
		public void setRect(double x, double y, double w, double h){
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int outcode(double x, double y){
			int out=0;
			if(width<=0){
				out|=OUT_LEFT|OUT_RIGHT;
			}else if(x<this.x){
				out|=OUT_LEFT;
			}else if(x>this.x+(double)width){
				out|=OUT_RIGHT;
			}
			if(height<=0){
				out|=OUT_TOP|OUT_BOTTOM;
			}else if(y<this.y){
				out|=OUT_TOP;
			}else if(y>this.y+(double)height){
				out|=OUT_BOTTOM;
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
				dest=new Rectangle2D.Float();
			}else{
				dest=new Rectangle2D.Double();
			}
			Rectangle2D.intersect(this, r, dest);
			return dest;
		}
		
		@Override
		public Rectangle2D createUnion(Rectangle2D r){
			Rectangle2D dest;
			if(r instanceof Float){
				dest=new Rectangle2D.Float();
			}else{
				dest=new Rectangle2D.Double();
			}
			Rectangle2D.union(this, r, dest);
			return dest;
		}
		
		@Override
		public String toString(){
			return getClass().getName()
			       +"[x="+x+
			       ",y="+y+
			       ",w="+width+
			       ",h="+height+"]";
		}
	}
	
	private static boolean INITED;
	
	private static       GlfwMonitor       PRIMARY_MONITOR =null;
	private static final List<GlfwMonitor> MONITORS        =new ArrayList<>(1);
	private static final List<GlfwMonitor> MONITORS_F      =Collections.unmodifiableList(MONITORS);
	private static final List<Rect>        MONITOR_GROUPS  =new ArrayList<>(1);
	private static final List<Rect>        MONITOR_GROUPS_F=Collections.unmodifiableList(MONITOR_GROUPS);
	
	public static void init(){
		if(INITED) return;
		INITED=true;
		
		glfwInit();
		update();
		glfwSetMonitorCallback(GLFWMonitorCallback.create((monitor, event)->update()));
	}
	
	private static void check(){
		if(!INITED) throw new IllegalStateException("\"init\" FUNCTION WAS NOT CALLED");
	}
	
	private static void update(){
		
		MONITORS.clear();
		MONITOR_GROUPS.clear();
		
		PointerBuffer monitors=glfwGetMonitors();
		for(int i=0;i<monitors.capacity();i++){
			MONITORS.add(new GlfwMonitor(monitors.get(i)));
		}
		
		List<Rectangle2D> groups=new ArrayList<>(1);
		
		List<Rectangle2D> all=new ArrayList<>(1);
		all.addAll(MONITORS.stream().map(a->a.bounds).collect(Collectors.toList()));
		for(GlfwMonitor m : MONITORS){
			
			Rectangle2D group =new Rectangle2D.Float(m.bounds.x, m.bounds.y, m.bounds.width, m.bounds.height);
			boolean     change=true;
			while(change){
				change=false;
				
				for(Rectangle2D monitor : all){
					if(monitor.equals(group)) continue;
					
					boolean yFlush=group.getMaxY()==monitor.getMaxY()&&group.getMinY()==monitor.getMinY();
					if(yFlush){
						boolean rightCon=group.getMaxX()==monitor.getMinX();
						boolean leftCon =monitor.getMaxX()==group.getMinX();
						if(rightCon||leftCon){
							change=true;
							group=group.createUnion(monitor);
							
						}
					}
					
					boolean xFlush=group.getMaxX()==monitor.getMaxX()&&group.getMinX()==monitor.getMinX();
					if(xFlush){
						boolean topCon   =group.getMaxY()==monitor.getMinY();
						boolean bottomCon=monitor.getMaxY()==group.getMinY();
						if(topCon||bottomCon){
							change=true;
							group=group.createUnion(monitor);
						}
					}
					
				}
			}
			
			if(!groups.contains(group)) groups.add(group);
			if(!all.contains(group)) all.add(group);
			
		}
		groups.stream().map(g->new Rect((int)g.getX(), (int)g.getY(), (int)g.getWidth(), (int)g.getHeight())).forEach(MONITOR_GROUPS::add);
		long ph=glfwGetPrimaryMonitor();
		PRIMARY_MONITOR=MONITORS.stream().filter(m->m.handle==ph).findAny().orElseThrow(()->new RuntimeException("No primary monitor in monitor list?? (bug)"));
	}
	
	public static GlfwMonitor getPrimaryMonitor(){
		check();
		return PRIMARY_MONITOR;
	}
	
	public static List<GlfwMonitor> getMonitors(){
		check();
		return MONITORS_F;
	}
	
	public static List<Rect> getGroups(){
		check();
		return MONITOR_GROUPS_F;
	}
	
	public static boolean moveToVisible(Rectangle2D windowRect){
		
		if(getGroups().stream().noneMatch(group->group.contains(windowRect))){
			
			Optional<PairM<Rect, Rectangle2D>> r=
					getGroups().stream()
					           .map(group->new PairM<>(group, new Rectangle2D.Double(group.getX(), group.getY(), windowRect.getWidth(), windowRect.getHeight()).createIntersection(group)))
					           .max(Comparator.comparingDouble(a->a.obj2.getWidth()*a.obj2.getHeight()));
			if(r.isPresent()){
				Rectangle2D intersect=r.get().obj2;
				Rect        group    =r.get().obj1;
				
				double x, y,
						w=Math.min(windowRect.getWidth(), group.getWidth()),
						h=Math.min(windowRect.getHeight(), group.getHeight());
				
				if(windowRect.getMinX()<group.getMinX()) x=group.getMinX();
				else if(windowRect.getMaxX()>group.getMaxX()) x=group.getMaxX()-w;
				else x=windowRect.getX();
				
				if(windowRect.getMinY()<group.getMinY()) y=group.getMinY();
				else if(windowRect.getMaxY()>group.getMaxY()) y=group.getMaxY()-h;
				else y=windowRect.getY();
				
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
		this.handle=handle;
		
		
		GLFWVidMode mode=glfwGetVideoMode(handle);
		
		refreshRate=mode.refreshRate();
		
		int[] xBuf={0}, yBuf={0};
		glfwGetMonitorPos(handle, xBuf, yBuf);
		bounds=new Rect(xBuf[0], yBuf[0], mode.width(), mode.height());
	}
	
	@Override
	public int hashCode(){
		return bounds.hashCode()+refreshRate;
	}
	
	@Override
	public boolean equals(Object obj){
		if(obj==this) return true;
		if(!(obj instanceof GlfwMonitor)) return false;
		GlfwMonitor other=(GlfwMonitor)obj;
		return other.handle==handle;
	}
	
	@Override
	public String toString(){
		return "GlfwMonitor{refreshRate="+refreshRate+", bounds="+bounds+"}";
	}
	
}

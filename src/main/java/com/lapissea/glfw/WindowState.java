package com.lapissea.glfw;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * For storing and IO of window state. Normal mode means a regular floating window
 */
public record WindowState(Mode mode, Point normalPos, Size normalSize, MonitorRef monitor){
	
	public enum Mode{
		NORMAL,
		MAXIMIZED,
		FULL_SCREEN,
		BORDERLESS_FULL_SCREEN,
		;
		public boolean isFullScreen(){
			return this == FULL_SCREEN || this == BORDERLESS_FULL_SCREEN;
		}
	}
	
	public record MonitorRef(String name, int x, int y, int width, int height){
		
		static MonitorRef of(GlfwMonitor monitor){
			var b = monitor.bounds;
			return new MonitorRef(monitor.name, b.x, b.y, b.width, b.height);
		}
		
		public Optional<GlfwMonitor> find(){
			var monitors = GlfwMonitor.getMonitors();
			
			for(var m : monitors){
				var b = m.bounds;
				if(Objects.equals(m.name, name) && b.x == x && b.y == y){
					return Optional.of(m);
				}
			}
			
			for(var m : monitors){
				var b = m.bounds;
				if(b.x == x && b.y == y && b.width == width && b.height == height){
					return Optional.of(m);
				}
			}
			
			for(var m : monitors){
				if(Objects.equals(m.name, name)){
					return Optional.of(m);
				}
			}
			
			return Optional.empty();
		}
		
		public JsonObject toJson(){
			var obj = new JsonObject();
			
			obj.addProperty("name", name);
			
			obj.addProperty("x", x);
			obj.addProperty("y", y);
			
			obj.addProperty("width", width);
			obj.addProperty("height", height);
			
			return obj;
		}
	}
	
	public WindowState{
		Objects.requireNonNull(mode);
		Objects.requireNonNull(normalSize);
		
		if(mode.isFullScreen()){
			Objects.requireNonNull(monitor);
		}
	}
	
	public String toJson(){
		var json = new JsonObject();
		json.addProperty("version", 2);
		json.addProperty("mode", mode.name());
		
		var normal = new JsonObject();
		
		if(normalPos != null){
			normal.addProperty("x", normalPos.x());
			normal.addProperty("y", normalPos.y());
		}
		
		normal.addProperty("width", normalSize.width());
		normal.addProperty("height", normalSize.height());
		
		json.add("normal", normal);
		
		if(monitor != null){
			json.add("monitor", monitor.toJson());
		}
		
		return new GsonBuilder().setPrettyPrinting().create().toJson(json);
	}
	
	public static WindowState fromJson(String text){
		try{
			var json = JsonParser.parseString(text).getAsJsonObject();
			return parse(json);
		}catch(RuntimeException e){
			throw new IllegalArgumentException("Not a window state: " + e.getMessage(), e);
		}
	}
	
	private static WindowState parse(JsonObject json){
		var mode = Mode.valueOf(json.get("mode").getAsString());
		
		var normal = json.getAsJsonObject("normal");
		var pos    = normal.has("x") && normal.has("y")? new Point(normal.get("x").getAsInt(), normal.get("y").getAsInt()) : null;
		var size   = new Size(normal.get("width").getAsInt(), normal.get("height").getAsInt());
		
		MonitorRef monitor = null;
		if(json.get("monitor") instanceof JsonObject m){
			var name = m.get("name");
			monitor = new MonitorRef(
				name == null || name.isJsonNull()? null : name.getAsString(),
				m.get("x").getAsInt(),
				m.get("y").getAsInt(),
				m.get("width").getAsInt(),
				m.get("height").getAsInt()
			);
		}
		
		return new WindowState(mode, pos, size, monitor);
	}
	
	public static Optional<WindowState> load(Path file){
		try{
			if(!Files.isRegularFile(file)) return Optional.empty();
			return Optional.of(fromJson(Files.readString(file)));
		}catch(IOException|IllegalArgumentException e){
			return Optional.empty();
		}
	}
	
	public void save(Path file) throws IOException{
		var tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, toJson());
		try{
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}catch(IOException e){
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

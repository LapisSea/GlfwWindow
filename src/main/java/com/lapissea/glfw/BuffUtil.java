package com.lapissea.glfw;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

@SuppressWarnings("PointlessBitwiseExpression")
public final class BuffUtil{
	
	public static ByteBuffer imageToBuffer(BufferedImage image, ByteBuffer buffer){
		
		int bitsPP = image.getColorModel().getPixelSize();
		if(bitsPP>32) throw new IllegalArgumentException("unsupported image format");
		
		for(int i = 0; i<image.getHeight(); i++){
			for(int j = 0; j<image.getWidth(); j++){
				int colorSpace = image.getRGB(j, i);
				buffer.put((byte)((colorSpace>>16)&0xFF));
				buffer.put((byte)((colorSpace>>8)&0xFF));
				buffer.put((byte)((colorSpace>>0)&0xFF));
				buffer.put((byte)((colorSpace>>24)&0xFF));
			}
		}
		
		return buffer.flip();
	}
	
}

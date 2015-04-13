package com.teamstudio.continuity.utils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import javax.imageio.ImageIO;

public class ImageResizer {
	
	private static final int	DEFAULT_IMAGE_TYPE	= BufferedImage.TYPE_INT_RGB;
	private Image				image				= null;
	private int					srcImageHeight;
	private int					srcImageWidth;
	public String				srcFilePath			= null;
	public String				srcFileName			= null;	
	
	private int width_temp = 0;
	private int height_temp = 0;
	
	private int			targetImageHeight;
	private int			targetImageWidth;
	private String		targetFileName;
	private long		targetFileLength;
	private boolean		targetCrop			= false;		
	
	private String		outputFormat		= "jpg";
	
	private Vector<File> images = new Vector<File>();
	
	public ImageResizer( File file) {
		
		try {
			image = ImageIO.read(file);
			this.getSourceImageParameters();
		} catch (IOException e) {
			e.printStackTrace();
		}	
		
	}
	
	public void setTargetSize(int x, int y) {
		
		Logger.debug("set size (x: "+ x + ", y: " + y + ")");

		double scaleH = (double)y / (double)srcImageHeight;
		double scaleW = (double)x / (double)srcImageWidth;
		
		double scale = Math.min(scaleH, scaleW);

		width_temp = (int) (srcImageWidth * scale);
		height_temp = (int) (srcImageHeight * scale);
		
		//target size is square, but input file is not: use crop method
		if ( ((double)y/(double)x) == 1 && ((double)srcImageHeight/(double)srcImageWidth) != 1) {
			targetCrop = true;
		}
		
	}
	
	/*
	 * start the resize process, writes the output file
	 */
	public File resize(String outputFile) throws IOException {
		
		File saveFile = null;
		
		if (image != null) {
			targetImageWidth = width_temp;
			targetImageHeight = height_temp;
			saveFile = this.writeFile(outputFile);
		}
	
		return saveFile;
	}
	
	private void getSourceImageParameters() {
		this.srcImageWidth = image.getWidth(null);
		this.srcImageHeight = image.getHeight(null);
	}
	
	public int getSourceImageWidth() {
		return this.srcImageWidth;
	}
	public int getSourceImageHeight() {
		return this.srcImageHeight;
	}
	public int getTargetImageWidth() {
		return this.targetImageWidth;
	}
	public int getTargetImageHeight() {
		return this.targetImageHeight;
	}
	public String getTargetFileName() {
		return targetFileName;
	}

	public long getTargetFileLength() {
		return targetFileLength;
	}
	
	/*remove all created files from disk*/
	public void cleanup() {
		for (File f : this.images) {
			f.delete();
			Logger.debug("cleanup - removed file " + f.toString());
		}
	}
	
	private File writeFile(String filePath ) throws IOException {
		//this.log("writing file (target width:  " + targetImageWidth + ", target height: " + targetImageHeight + ")");
		
		File saveFile = new File(filePath);
		
		//this.log("target file: " + filePath);
		
		BufferedImage bufScaledImage = getScaledInstance(targetImageWidth, targetImageHeight, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		if (bufScaledImage != null) {
			//this.log("buffered image retrieved, write file");
			ImageIO.write(bufScaledImage, this.outputFormat, saveFile);
			//this.log("file written");
		}
		
		this.images.add(saveFile);
		this.targetFileLength = saveFile.length();
		this.targetFileName = saveFile.getName();
		
		return saveFile;
	}

	/**
	 * Convenience method that returns a scaled instance of the provided {@code BufferedImage}.
	 * 
	 * @param targetWidth
	 *            the desired width of the scaled instance, in pixels
	 * @param targetHeight
	 *            the desired height of the scaled instance, in pixels
	 * @param hint
	 *            one of the rendering hints that corresponds to {@code RenderingHints.KEY_INTERPOLATION} (e.g. {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR}, {@code
	 *            RenderingHints.VALUE_INTERPOLATION_BILINEAR}, {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
	 * @return a scaled version of the original {@code BufferedImage}
	 */
	private BufferedImage getScaledInstance (int targetWidth, int targetHeight, Object hint) {
		
		BufferedImage bufScaledImage = null;
		
		try {
			int type = DEFAULT_IMAGE_TYPE;
	
			Logger.debug("source width: " + srcImageWidth + ", height: " + srcImageHeight );
				
			if (this.targetCrop && srcImageHeight != srcImageWidth) {	//crop non-square images
				
				//this.log("crop target image");
				
				int x, y, size;
				
				if (srcImageHeight > srcImageWidth) {		//tall image
					
					x = 0;
					y = ((srcImageHeight - srcImageWidth) / 2);
					size = srcImageWidth;
					
				} else {		//wide image
					
					x = ((srcImageWidth - srcImageHeight) / 2);
					y = 0;
					size = srcImageHeight;
				
				}
				
				targetWidth=Math.max(targetWidth, targetHeight);
				targetHeight=Math.max(targetWidth, targetHeight);
				
				targetImageWidth = targetWidth;
				targetImageHeight = targetHeight;
							
				bufScaledImage = ((BufferedImage) image).getSubimage(x,y,size,size);
				
			} else {
				
				bufScaledImage = (BufferedImage) image;
				
			}
			//this.log("got bufScaledImage");
			
			int w, h;

			if (srcImageWidth > targetWidth ||
				srcImageHeight > targetHeight) {
				
				// Use multi-step technique: start with original size, then
				// scale down in multiple passes with drawImage()
				// until the target size is reached
				w = srcImageWidth;
				h = srcImageWidth;
			} else {
				// Use one-step technique: scale directly from original
				// size to target size with a single drawImage() call
				w = targetWidth;
				h = targetHeight;
			}
			
		do {
				if (w > targetWidth) {
					w /= 2;
					if (w < targetWidth) {
						w = targetWidth;
					}
				}
	
				if (h > targetHeight) {
					h /= 2;
					if (h < targetHeight) {
						h = targetHeight;
					}
				}
				
				//this.log("w: " + w + ", h " + h);
				BufferedImage bufImage = new BufferedImage(w, h, type);
				//this.log("create graphics...");
				Graphics2D g2 = bufImage.createGraphics();
				
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
				g2.drawImage(bufScaledImage, 0, 0, w, h, null);
				g2.dispose();
	
				bufScaledImage = bufImage;
				
			} while (w != targetWidth || h != targetHeight);
		
		} catch (Exception e) {
			Logger.error("error while resizing image");
			Logger.error(e.getMessage());
			e.printStackTrace();
		} finally {
			
		}

		return bufScaledImage;
	}
	
}
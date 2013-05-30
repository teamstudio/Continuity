package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.List;

import lotus.domino.Document;
import lotus.domino.NotesException;

import com.ibm.xsp.model.FileRowData;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;

public class File implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public static final String TYPE_DOC = "doc";
	public static final String TYPE_PDF = "pdf";
	public static final String TYPE_PPT = "ppt";
	public static final String TYPE_XLS = "xls";
	public static final String TYPE_IMAGE = "image";
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_UNKNOWN = "unknown";

	public File() {
		
	}
	
	public boolean save( DominoDocument xspDocFile ) {
		
		try {
			boolean blnIsNew = xspDocFile.isNewNote();
			
			if ( blnIsNew ) {
									
				//set the owners: [bcEditor] and owner
				xspDocFile.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR );
			
				Document docFile = xspDocFile.getDocument(true);
				docFile.getFirstItem("docAuthors").setAuthors(true);
				docFile.getFirstItem("owner").setAuthors(true);

				xspDocFile.replaceItemValue("id", "q" + xspDocFile.getDocument().getUniversalID().toLowerCase() );
				
			}
			
			//get file type/ name/ size
			List<FileRowData> files = xspDocFile.getAttachmentList("files");
			
			Logger.debug("found" + files.size() + "files");
			
			String type = "";
			long size = 0;
			String name = "";
			
			if (files.size()>0) {
				
				FileRowData file = files.get(0);
			
				
				name = file.getName();
				
				Logger.debug("found" + name );
			
				
				String ext = name.substring( name.lastIndexOf(".") + 1).toLowerCase();
				
				Logger.debug("exd" + ext);
				
				if (ext.equals("doc")) {
					type = TYPE_DOC;
				} else if (ext.equals("xls") ) {
					type = TYPE_XLS;
				} else if (ext.equals("pdf") ) {
					type = TYPE_PDF;
				} else if (ext.equals("ppt") ) {
					type = TYPE_PPT;
				} else if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif") ) {
					type = TYPE_IMAGE;
				} else if (ext.equals("mov") || ext.equals("avi") || ext.equals("mpeg") || ext.equals("mp4") || ext.equals("mkv") ) {
					type = TYPE_VIDEO;
				} else {
					type = TYPE_UNKNOWN;
				}
				
				size = file.getLength();
				
				Logger.debug("s"  + size);
				
			}
			
			xspDocFile.replaceItemValue("fileName", name);
			xspDocFile.replaceItemValue("fileType", type);
			xspDocFile.replaceItemValue("fileSize", size);
			xspDocFile.save();

			if (blnIsNew) {	
				//update menu items count
				User.get().updateMenuOptionCounts();
			}
			
			Logger.debug("save");
			
			boolean result = xspDocFile.save();
			Logger.debug( result + "");
			
			return result;
			
		} catch (Exception e) {
			
			Logger.debug("error");
			Logger.error(e);
			
			return false;
		}
		
		
		
	}
	

}

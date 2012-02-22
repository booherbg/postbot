/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

public class FileManager
{

	private static FileManager fm;
	
	private static File mCacheDir;
	
	private FileManager(File cacheDir)
	{
		mCacheDir = cacheDir;
	}

	public static FileManager setCacheDir(File cacheDir)
	{
		if( fm == null )
			fm = new FileManager(cacheDir);
		else
			mCacheDir = cacheDir;
		
		return fm;
	}
	
	public static String getThumbnailFilename(Image img)
	{
		try
		{
			return mCacheDir.getCanonicalPath() + File.separator + img.getPostId() + "-" + img.getId();
		}
		catch( IOException e )
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public static Bitmap getImageThumbnail(Image img)
	{
		return BitmapFactory.decodeFile(getThumbnailFilename(img));
	}
	
	public static String putImageThumbnail(Image img, Bitmap bm)
	{
		// don't bother if there is no postId set yet, it will just change anyway
		if( img.getPostId() != 0 )
		{
			try
			{
				String filename = getThumbnailFilename(img);
				FileOutputStream os = new FileOutputStream(new File(filename));
				bm.compress(CompressFormat.JPEG, 100, os);
				os.close();
				return filename;
			}
			catch( IOException e )
			{
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public static boolean deleteThumbnail(Image img)
	{
		File file = new File(getThumbnailFilename(img));
		return file.delete();
	}
	
	public static int emptyThumbnailCache()
	{
		int deleted = 0;
		for( File file: mCacheDir.listFiles() )
		{
			if( file.delete() )
				deleted++;
		}
		return deleted;
	}
	
	public static void deleteThumbnailsForPost(final Post post)
	{
		FilenameFilter filter = new FilenameFilter()
		{
			public boolean accept(File dir, String filename)
			{
				return filename.split("-")[0].equals(Long.toString(post.getId()));
			}
		};
		
		for( File file: mCacheDir.listFiles(filter) )
			file.delete();
	}
}

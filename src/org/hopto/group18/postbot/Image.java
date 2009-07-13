/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;


public class Image
{
	public static final String TAG = "Image";
	
	public static final String extension = "jpg";
	
	public static final int BUFSIZ = 2000000;
	public static final int MAX_BUFSIZ = 3500000;
	public static final int TMP_BUFSIZ = 4096;
	
	public static final int WIDTH_INDEX = 0;
	public static final int HEIGHT_INDEX = 1;
	
	private static int maxThumbnailSize = 320;
	private static int maxImageSize = 1024;
	private static int maxGalleryImageSize = 64;
	private static int thumbnailQuality = 100;
	
	private long mId;
	private String mUri;
	private String mUrl;
	private String mThumbnailUrl;
	
	private String mName;
	private String mThumbnailName;
	
	private byte[] mContent;
	private byte[] mThumbnailContent;
	private int mWidth;
	private int mHeight;
	private int mThumbnailWidth;
	private int mThumbnailHeight;
	
	private long mPostId;
	
	public Image(String uri, String url)
	{
		setUri(uri);
		mUrl = url;
		
		mContent = null;
		mId = 0;
		mPostId = 0;
		
		mWidth = 0;
		mHeight = 0;
	}

	public boolean loadFileContents(ContentResolver resolver) throws FileNotFoundException, IOException
	{
		// read file contents from disk
		InputStream is = resolver.openInputStream(Uri.parse(mUri));
		try
		{
			// try with a regular buffer
			mContent = getBytesFromInputStream(is, BUFSIZ);
		}
		catch( BufferOverflowException e )
		{
			e.printStackTrace();
			Log.e(TAG, "Attempting to handle BufferOverflowException...");
			// that didn't work - try with a bigger one (may cause OOME later)
			is.close();
			is = resolver.openInputStream(Uri.parse(mUri));
			try
			{
				mContent = getBytesFromInputStream(is, MAX_BUFSIZ);
			}
			catch( BufferOverflowException f )
			{
				f.printStackTrace();
				Log.e(TAG, "AttemptUnsuccessful. Throwing IOException...");
				throw new IOException("Unable to load image: it is too large.");
			}
		}
		finally
		{
			is.close();
		}
	
		if( mContent == null )
			return false;
		
		// get image dimensions
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		Bitmap bm = getPicFromBytes(mContent, opts);
		
		// now calculate the sample size required and create a bitmap
		opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, maxImageSize);
		Log.d(TAG, "Using SAMPLESIZE " + opts.inSampleSize + " to get image size " + maxImageSize + "...");
		opts.inJustDecodeBounds = false;
		bm = getPicFromBytes(mContent, opts);
		mContent = getBytesFromPic(bm);
		Log.d(TAG, "Got " + mContent.length + " bytes.");
		setWidth(bm.getWidth());
		setHeight(bm.getHeight());
		
		// generate a thumbnail
		int[] dimensions = calculateImageDimensions(mWidth, mHeight, maxThumbnailSize);
		setThumbnailWidth(dimensions[WIDTH_INDEX]);
		setThumbnailHeight(dimensions[HEIGHT_INDEX]);
		mThumbnailContent = getBytesFromPic(Bitmap.createScaledBitmap(bm, mThumbnailWidth, mThumbnailHeight, true));
		mThumbnailName = "thumb-" + mName;
		
		Log.d(TAG, "Actual DIMENSIONS " + bm.getWidth() + "x" + bm.getHeight());
		bm.recycle();
		
		printMemoryReport();
		
		return true;
	}

	public void printMemoryReport()
	{
		Log.d(TAG, "Image Memory Report:");
		Log.d(TAG, "   Image Size:     " + mContent.length);
		Log.d(TAG, "   Thumbnail Size: " + mThumbnailContent.length);
		Log.d(TAG, "   Total Size:     " + (mContent.length + mThumbnailContent.length));
	}
	public void dropFileContents()
	{
		mContent = null;
		mThumbnailContent = null;
	}

	public Bitmap getBitmap()
	{
		return getPicFromBytes(mContent, null);
	}
	
	public boolean putBitmap(Bitmap pic)
	{
		byte[] content = getBytesFromPic(pic);
		if( content != null )
		{
			mContent = content;
			return true;
		}
		return false;
	}
	
	public Bitmap getGalleryThumbnail()
	{
		int[] dimensions = calculateImageDimensions(mWidth, mHeight, maxGalleryImageSize);
		return Bitmap.createScaledBitmap(getPicFromBytes(mContent, null), dimensions[WIDTH_INDEX], dimensions[HEIGHT_INDEX], true);
	}
	
	public Bitmap getThumbnail()
	{
		return getPicFromBytes(mThumbnailContent, null);
	}
	
	public boolean putThumbnail(Bitmap pic)
	{
		byte[] content = getBytesFromPic(pic);
		if( content != null )
		{
			mThumbnailContent = content;
			return true;
		}
		return false;
	}
	
	public static int calculateSampleSize(int width, int height, int maxSize)
	{
		int sampleSize = 0;
		
		if( maxSize == 0 )
			return sampleSize;
		
		float fsize = maxSize;
		
		if( width > height )
		{
			float fwidth = width;
			sampleSize = new Double(Math.ceil(fwidth / fsize)).intValue();
		}
		else
		{
			float fheight = height;
			sampleSize = new Double(Math.ceil(fheight / fsize)).intValue();
		}
		
		if( sampleSize == 3 )
			sampleSize = 4;
		else if( sampleSize > 4 && sampleSize < 8 )
			sampleSize = 8;

		return sampleSize;
	}
	
	public static int[] calculateImageDimensions(int width, int height, int maxSize)
	{
		int outWidth = -1;
		int outHeight = -1;
		
		if( width > height )
		{
			if( width > maxSize )
			{
				outWidth = maxSize;
				outHeight = (height * maxSize) / width;
			}
			else
			{
				outWidth = width;
				outHeight = height;
			}
		}
		else
		{
			if( height > maxSize )
			{
				outHeight = maxSize;
				outWidth = (width * maxSize) / height;
			}
			else
			{
				outWidth = width;
				outHeight = height;
			}
		}
		return new int[] { outWidth, outHeight };
	}
	
	public static byte[] getBytesFromInputStream(InputStream is, int bufsiz) throws IOException
	{
		int total = 0;
		byte[] bytes = new byte[TMP_BUFSIZ];
		ByteBuffer bb = ByteBuffer.allocate(bufsiz);
		
		while( true )
		{
			int read = is.read(bytes);
			if( read == -1 )
				break;
			bb.put(bytes, 0, read);
			total += read;
		}

		byte[] content = new byte[total];
		bb.flip();
		bb.get(content, 0, total);
		Log.d(TAG, "Loaded " + total + " bytes.");
		return content;
	}
	
	public static Bitmap getPicFromBytes(byte[] bytes, BitmapFactory.Options opts)
	{
		Log.d(TAG, "Attempting to decode Bitmap from " + bytes.length + " bytes...");
		if( bytes != null )
			if( opts != null )
				return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
			else
				return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		return null;
	}
	
	public static byte[] getBytesFromPic(Bitmap pic)
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		pic.compress(Bitmap.CompressFormat.JPEG, thumbnailQuality, os);
		return os.toByteArray();
	}
	
	public long getId()
	{
		return mId;
	}
	public void setId(long id)
	{
		this.mId = id;
	}
	public String getUri()
	{
		return mUri;
	}
	public void setUri(String uri)
	{
		String[] components = uri.split(File.separator);
		this.mName = components[components.length-1];
		this.mUri = uri;
	}
	public String getUrl()
	{
		return mUrl;
	}
	public void setUrl(String url)
	{
		this.mUrl = url;
	}
	public long getPostId()
	{
		return mPostId;
	}
	public void setPostId(long postId)
	{
		this.mPostId = postId;
	}
	public String getName()
	{
		return mName + "." + extension;
	}
	public byte[] getContent()
	{
		return mContent;
	}
	
	public String toString()
	{
		return mUri;
	}

	public int getWidth()
	{
		return mWidth;
	}

	public void setWidth(int width)
	{
		this.mWidth = width;
	}

	public int getHeight()
	{
		return mHeight;
	}

	public void setHeight(int height)
	{
		this.mHeight = height;
	}

	public int getThumbnailHeight()
	{
		return mThumbnailHeight;
	}

	public void setThumbnailHeight(int thumbnailHeight)
	{
		mThumbnailHeight = thumbnailHeight;
	}

	public String getThumbnailUrl()
	{
		return mThumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl)
	{
		mThumbnailUrl = thumbnailUrl;
	}

	public int getThumbnailWidth()
	{
		return mThumbnailWidth;
	}

	public void setThumbnailWidth(int thumbnailWidth)
	{
		mThumbnailWidth = thumbnailWidth;
	}

	public byte[] getThumbnailContent()
	{
		return mThumbnailContent;
	}

	public void setThumbnailContent(byte[] thumbnailContent)
	{
		mThumbnailContent = thumbnailContent;
	}

	public String getThumbnailName()
	{
		return mThumbnailName + "." + extension;
	}

	public static int getMaxThumbnailSize() {
		return maxThumbnailSize;
	}

	public static void setMaxThumbnailSize(int maxThumbnailSize) {
		Image.maxThumbnailSize = maxThumbnailSize;
	}

	public static int getMaxImageSize() {
		return maxImageSize;
	}

	public static void setMaxImageSize(int maxImageSize) {
		Image.maxImageSize = maxImageSize;
	}

	public static int getMaxGallerySize() {
		return maxGalleryImageSize;
	}

	public static void setMaxGallerySize(int maxGallerySize) {
		Image.maxGalleryImageSize = maxGallerySize;
	}
}

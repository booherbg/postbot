/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.sql.SQLException;
import java.util.Vector;

import org.hopto.group18.postbot.Blog.Alignment;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class PostBotDbAdapter
{
	public static final String DB_NAME = "postbot";
	public static final int DB_VERSION = 8;

	public static final int VERSION_ADDED_ALIGNMENT = 8;
	
	public static final String TABLE_CONFIG = "config";
	public static final String KEY_DEFAULT_BLOG = "defaultblog";

	public static final String TABLE_BLOGS = "blogs";
	public static final String KEY_BLOG_NAME = "name";
	public static final String KEY_USER = "user";
	public static final String KEY_PASSWORD = "password";
	public static final String KEY_SERVER = "server";
	public static final String KEY_PATH = "path";
	public static final String KEY_STOREPASSWORD = "storepass";
	public static final String KEY_ALIGNMENT = "align";
	//public static final String KEY_DEFAULT_TAGS = "defaulttags";

	public static final String TABLE_POSTS = "posts";
	public static final String KEY_TITLE = "title";
	public static final String KEY_CATEGORY_ID = "category";
	public static final String KEY_BODY = "body";
	public static final String KEY_SUBMITTED = "submitted";
	public static final String KEY_PUBLISHED = "published";
	public static final String KEY_BLOG_POST_ID = "post";
	public static final String KEY_BLOG_ID = "blog";
	//public static final String KEY_TAGS = "tags";

	public static final String TABLE_CATEGORIES = "categories";
	public static final String KEY_CATEGORY_NAME = "name";
	public static final String KEY_CATEGORY_DESC = "desc";

	public static final String TABLE_IMAGES = "images";
	public static final String KEY_IMAGE_URI = "uri";
	public static final String KEY_IMAGE_URL = "url";
	public static final String KEY_THUMBNAIL_URL = "turl";
	public static final String KEY_WIDTH = "width";
	public static final String KEY_HEIGHT = "height";
	public static final String KEY_TWIDTH = "twidth";
	public static final String KEY_THEIGHT = "theight";
	public static final String KEY_POST_ID = "post";

	public static final String KEY_ID = "_id";

	public static final String TAG = "PostBotDbAdapter";

	private Context mCtx;
	private Activity mActivity;
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	private static Exception sLastException;

	private static final String SQL_CREATE_TABLE_CONFIG = 
		"create table " + TABLE_CONFIG + " (" +
		KEY_ID + " integer primary key autoincrement, " +
		KEY_DEFAULT_BLOG + " text" +
		");";
	private static final String SQL_DROP_TABLE_CONFIG = "DROP TABLE IF EXISTS " + TABLE_CONFIG;
	private static final String SQL_CREATE_TABLE_BLOG = 
		"create table " + TABLE_BLOGS + " (" + 
		KEY_ID + " integer primary key autoincrement, " +
		KEY_BLOG_NAME + " text unique not null, " +
		KEY_USER + " text not null, " +
		KEY_PASSWORD + " text not null, " +
		KEY_SERVER + " text not null, " +
		KEY_PATH + " text not null, " +
		KEY_STOREPASSWORD + " integer default 0, " +
		KEY_ALIGNMENT + " integer default " + Alignment.left.ordinal() +
		//KEY_DEFAULT_TAGS + " text default ''" +
		");";
	private static final String SQL_DROP_TABLE_BLOGS = "DROP TABLE IF EXISTS " + TABLE_BLOGS;
	private static final String SQL_CREATE_TABLE_POSTS = 
		"create table " + TABLE_POSTS + " (" +
		KEY_ID + " integer primary key autoincrement, " +
		KEY_TITLE + " text not null, " +
		KEY_CATEGORY_ID + " text not null, " +
		KEY_BODY + " text not null, " +
		KEY_SUBMITTED + " integer not null default 0, " +
		KEY_PUBLISHED + " integer not null default 0, " +
		KEY_BLOG_POST_ID + " integer, " +
		KEY_BLOG_ID + " integer " +
		//KEY_TAGS + " text default ''" +
		");";
	private static final String SQL_DROP_TABLE_POSTS = "DROP TABLE IF EXISTS " + TABLE_POSTS;
	private static final String SQL_CREATE_TABLE_CATEGORIES =
		"create table " + TABLE_CATEGORIES + " (" +
		KEY_ID + " integer primary key autoincrement, " +
		KEY_CATEGORY_ID + " text not null, " +
		KEY_CATEGORY_NAME + " text not null, " +
		KEY_CATEGORY_DESC + " text not null, " +
		KEY_BLOG_ID + " integer default '0'" +
		");";
	private static final String SQL_DROP_TABLE_CATEGORIES = "DROP TABLE IF EXISTS " + TABLE_CATEGORIES;
	private static final String SQL_CREATE_TABLE_IMAGES =
		"create table " + TABLE_IMAGES + " (" +
		KEY_ID + " integer primary key autoincrement, " +
		KEY_IMAGE_URI + " text not null, " +
		KEY_IMAGE_URL + " text, " +
		KEY_THUMBNAIL_URL + " text, " +
		KEY_WIDTH + " integer default 0, " +
		KEY_HEIGHT + " integer default 0, " +
		KEY_TWIDTH + " integer default 0, " +
		KEY_THEIGHT + " integer default 0, " +
		KEY_POST_ID + " integer default 0" +
		");";
	private static final String SQL_DROP_TABLE_IMAGES = "DROP TABLE IF EXISTS " + TABLE_IMAGES;

	private static final String SQL_WHERE_BLOG_NAME = KEY_BLOG_NAME + " = ?";
	private static final String SQL_WHERE_BLOG_ID = KEY_BLOG_ID + " = ?";
	private static final String SQL_WHERE_ID = KEY_ID + " = ?";
	private static final String SQL_WHERE_POST_SUBMITTED = KEY_SUBMITTED + " = ?";
	private static final String SQL_WHERE_POST_PUBLISHED = KEY_PUBLISHED + " = ?";
	private static final String SQL_WHERE_BLOG_CATEGORY_ID = KEY_CATEGORY_ID + " = ?";
	private static final String SQL_WHERE_POST_ID = KEY_POST_ID + " = ?";

	private static final String SQL_DELETE_CATEGORIES_BY_BLOG_ID = "delete from " + TABLE_CATEGORIES + " where (" + SQL_WHERE_BLOG_ID + ")";
	private static final String SQL_DELETE_POSTS_BY_BLOG_ID = "delete from " + TABLE_POSTS + " where (" + SQL_WHERE_BLOG_ID + ")";
	
	private static final String SQL_ADD_ALIGNMENT_BLOGS = "alter table " + TABLE_BLOGS + " add column " + KEY_ALIGNMENT + " integer default " + Alignment.left.ordinal();
	
	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context ctx)
		{
			super(ctx, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db)
		{
			Log.i(TAG, "Creating PostBot database version " + DB_VERSION);
			db.execSQL(SQL_CREATE_TABLE_CONFIG);
			db.execSQL(SQL_CREATE_TABLE_BLOG);
			db.execSQL(SQL_CREATE_TABLE_POSTS);
			db.execSQL(SQL_CREATE_TABLE_CATEGORIES);
			db.execSQL(SQL_CREATE_TABLE_IMAGES);

			// we need exactly one row in the CONFIG table at all times
			ContentValues values = new ContentValues();
			values.put(KEY_DEFAULT_BLOG, "");
			db.insert(TABLE_CONFIG, KEY_DEFAULT_BLOG, values);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			if( oldVersion < VERSION_ADDED_ALIGNMENT )
			{
				Log.i(TAG, "Upgrading database: adding field '" + KEY_ALIGNMENT + "' to table '" + TABLE_BLOGS + "'...");
				db.execSQL(SQL_ADD_ALIGNMENT_BLOGS);
			}
			else
			{
				Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
						+ newVersion + ", which will destroy all old data");
				db.execSQL(SQL_DROP_TABLE_CONFIG);
				db.execSQL(SQL_DROP_TABLE_BLOGS);
				db.execSQL(SQL_DROP_TABLE_POSTS);
				db.execSQL(SQL_DROP_TABLE_CATEGORIES);
				db.execSQL(SQL_DROP_TABLE_IMAGES);
				onCreate(db);
			}
		}

	}

	public PostBotDbAdapter(Context ctx, Activity activity)
	{
		mCtx = ctx;
		mActivity = activity;
	}

	public PostBotDbAdapter open() throws SQLException
	{
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	public boolean isOpen()
	{
		return mDb.isOpen();
	}

	private Cursor initCursor(Cursor c)
	{
		if( c != null )
		{
			c.moveToFirst();
			mActivity.startManagingCursor(c);
			return c;
		}
		return null;
	}

	public String getDefaultBlogName()
	{
		Cursor c = mDb.query(true, TABLE_CONFIG, new String [] {KEY_DEFAULT_BLOG}, null, null, null, null, null, "1");
		initCursor(c);
		try
		{
			return c.getString(0);
		}
		catch( CursorIndexOutOfBoundsException e )
		{
			sLastException = e;
			return null;
		}
	}

	public Blog getDefaultBlog()
	{
		String name = getDefaultBlogName();
		if( name != null )
			return getBlog(name);
		return null;
	}

	public int getDbVersion()
	{
		return mDb.getVersion();
	}

	public boolean setDefaultBlog(String defaultBlog)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_DEFAULT_BLOG, defaultBlog);
		int rows = mDb.update(TABLE_CONFIG, values, null, null);
		if( rows == 0 )
		{
			Log.w(TAG, "No rows updated by setDefaultBlog().");
			return false;
		}
		return true;
	}

	/*
	 * Blog 
	 */

	private static ContentValues getValuesFromBlog(Blog blog)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_BLOG_NAME, blog.getBlogName());
		values.put(KEY_SERVER, blog.getServer());
		values.put(KEY_PATH, blog.getPath());
		values.put(KEY_USER, blog.getUsername());
		values.put(KEY_PASSWORD, blog.getPassword());
		values.put(KEY_ALIGNMENT, blog.getAlign().ordinal());
		//values.put(KEY_DEFAULT_TAGS, blog.getDefaultTags());
		return values;
	}

	public static Blog getBlogFromCursor(Cursor c)
	{
		try
		{
			Blog blog = new Blog(
					c.getString(c.getColumnIndexOrThrow(KEY_BLOG_NAME)),
					c.getString(c.getColumnIndexOrThrow(KEY_SERVER)),
					c.getString(c.getColumnIndexOrThrow(KEY_PATH)),
					c.getString(c.getColumnIndexOrThrow(KEY_USER)),
					c.getString(c.getColumnIndexOrThrow(KEY_PASSWORD)));
			blog.setBlogId(c.getLong(c.getColumnIndexOrThrow(KEY_ID)));
			blog.setAlign(Alignment.values()[c.getInt(c.getColumnIndexOrThrow(KEY_ALIGNMENT))]);
			//blog.setDefaultTags(c.getString(c.getColumnIndexOrThrow(KEY_DEFAULT_TAGS)));
			return blog;
		}
		catch( CursorIndexOutOfBoundsException e )
		{
			// this will happen if the query that created the cursor did not match any rows
			sLastException = e;
			return null;
		}
	}

	public boolean createBlog(Blog blog)
	{
		long rowId = mDb.insert(TABLE_BLOGS, null, getValuesFromBlog(blog));
		blog.setBlogId(rowId);
		for( Category category: blog.getCategories() )
			if( ! createCategory(category) )
				Log.w(TAG, "Unable to create category '" + category + "'!");
		return rowId != -1;
	}

	public Blog getBlog(String name)
	{
		Cursor c = mDb.query(TABLE_BLOGS, null, SQL_WHERE_BLOG_NAME, new String[]{name}, null, null, null, null);
		initCursor(c);
		Blog blog = getBlogFromCursor(c);
		if( blog != null )
			blog.setCategories(getCategoriesForBlog(blog.getBlogId()));
		return blog;
	}

	public Blog getBlog(long blogId)
	{
		Cursor c = mDb.query(TABLE_BLOGS, null, SQL_WHERE_ID, new String[] {Long.toString(blogId)}, null, null, null);
		initCursor(c);
		Blog blog = getBlogFromCursor(c);
		if( blog != null )
			blog.setCategories(getCategoriesForBlog(blogId));
		return blog;
	}

	public int getBlogIndex(long blogId)
	{
		String blogIdStr = Long.toString(blogId);
		Cursor c = getBlogsCursor();
		initCursor(c);
		for( int i=0; i<c.getCount(); i++ )
		{
			if( c.getString(c.getColumnIndexOrThrow(KEY_ID)).equals(blogIdStr) )
				return i;
			c.moveToNext();
		}
		return -1;
	}

	public boolean updateBlog(Blog blog)
	{
		int rows = mDb.update(TABLE_BLOGS, getValuesFromBlog(blog), SQL_WHERE_ID, new String[] {Long.toString(blog.getBlogId())});
		if( rows != 0 )
		{
			// TODO insert categories that are not already there instead of dropping and re-inserting
			Vector<Category> categories = blog.getCategories();
			// if there is at least one category there, update this blog's categories (wordpress will always have "Uncategorized" if nothing else)
			if( categories.size() > 0 )
			{
				mDb.execSQL(SQL_DELETE_CATEGORIES_BY_BLOG_ID, new String[]{ Long.toString(blog.getBlogId()) });
				for( Category category: blog.getCategories() )
					if( ! createCategory(category) )
						Log.w(TAG, "Unable to create category '" + category + "'!");
			}
		}
		else
			Log.w(TAG, "No rows updated by updateBlog().");

		return rows != 0;
	}

	public boolean deleteBlog(long blogId)
	{
		int rows = mDb.delete(TABLE_BLOGS, SQL_WHERE_ID, new String[] {Long.toString(blogId)});
		if( rows != 0 )
		{
			deleteCategoriesForBlog(blogId);
			deletePostsForBlog(blogId);
		}
		return rows > 0;
	}

	public Cursor getBlogsCursor()
	{
		return mDb.query(TABLE_BLOGS, null, null, null, null, null, null);
	}

	public Vector<Blog> getBlogs()
	{
		Cursor c = getBlogsCursor();
		initCursor(c);
		Vector<Blog> blogs = new Vector<Blog>();
		while( blogs.size() < c.getCount() )
		{
			Blog blog = getBlogFromCursor(c);
			blog.setCategories(getCategoriesForBlog(blog.getBlogId()));
			blogs.add(blog);
			c.moveToNext();
		}
		c.close();
		return blogs;
	}

	/*
	 * Category
	 */

	private static ContentValues getValuesFromCategory(Category category)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_CATEGORY_ID, category.getId());
		values.put(KEY_CATEGORY_NAME, category.getName());
		values.put(KEY_CATEGORY_DESC, category.getDescription());
		values.put(KEY_BLOG_ID, category.getBlogId());
		return values;
	}

	public static Category getCategoryFromCursor(Cursor c)
	{
		try
		{
			Category category = new Category(
					c.getString(c.getColumnIndexOrThrow(KEY_CATEGORY_ID)),
					c.getString(c.getColumnIndexOrThrow(KEY_CATEGORY_NAME)),
					c.getString(c.getColumnIndexOrThrow(KEY_CATEGORY_DESC))
			);
			category.setCategoryId(c.getLong(c.getColumnIndexOrThrow(KEY_ID)));
			category.setBlogId(c.getLong(c.getColumnIndexOrThrow(KEY_BLOG_ID)));
			return category;
		}
		catch( CursorIndexOutOfBoundsException e )
		{
			sLastException = e;
			return null;
		}
	}

	private static Vector<Category> getCategoriesFromCursor(Cursor c)
	{
		Vector<Category> categories = new Vector<Category>();
		while( categories.size() < c.getCount() )
		{
			categories.add(getCategoryFromCursor(c));
			c.moveToNext();
		}
		return categories;
	}
	public boolean createCategory(Category category)
	{
		long rowId = mDb.insert(TABLE_CATEGORIES, null, getValuesFromCategory(category));
		category.setCategoryId(rowId);
		return rowId != -1;
	}

	public Category getCategory(long categoryId)
	{
		Cursor c = mDb.query(TABLE_CATEGORIES, null, SQL_WHERE_ID, new String[]{ Long.toString(categoryId) }, null, null, null);
		initCursor(c);
		return getCategoryFromCursor(c);
	}

	public Category getCategory(String categoryId)
	{
		Cursor c = mDb.query(TABLE_CATEGORIES, null, SQL_WHERE_BLOG_CATEGORY_ID, new String[]{ categoryId }, null, null, null);
		initCursor(c);
		return getCategoryFromCursor(c);
	}

	public boolean updateCategory(Category category)
	{
		int rows = mDb.update(TABLE_CATEGORIES, getValuesFromCategory(category), SQL_WHERE_ID, new String[] {Long.toString(category.getCategoryId())});
		if( rows == 0 )
			Log.w(TAG, "No rows updated by updateCategory().");
		return rows != 0;
	}

	public boolean deleteCategory(long categoryId)
	{
		return mDb.delete(TABLE_CATEGORIES, SQL_WHERE_ID, new String[] {Long.toString(categoryId)}) > 0;
	}

	public void deleteCategoriesForBlog(long blogId)
	{
		mDb.execSQL(SQL_DELETE_CATEGORIES_BY_BLOG_ID, new String[]{ Long.toString(blogId) });
	}

	public Cursor getCategoriesCursor()
	{
		return mDb.query(TABLE_CATEGORIES, null, null, null, null, null, null);
	}

	public Vector<Category> getCategoriesForBlog(long blogId)
	{
		Cursor c = mDb.query(TABLE_CATEGORIES, null, SQL_WHERE_BLOG_ID, new String[]{ Long.toString(blogId) }, null, null, null);
		initCursor(c);
		return getCategoriesFromCursor(c);
	}

	public Vector<Category> getCategories()
	{
		Cursor c = getCategoriesCursor();
		initCursor(c);
		return getCategoriesFromCursor(c);
	}

	/*
	 * Post
	 */

	public static Post getPostFromCursor(Cursor c)
	{
		try
		{
			Post post = new Post(
					c.getString(c.getColumnIndexOrThrow(KEY_TITLE)),
					c.getString(c.getColumnIndexOrThrow(KEY_CATEGORY_ID)),
					c.getString(c.getColumnIndexOrThrow(KEY_BODY)));
			post.setBlogId(c.getLong(c.getColumnIndexOrThrow(KEY_BLOG_ID)));
			post.setId(c.getLong(c.getColumnIndexOrThrow(KEY_ID)));
			post.setBlogPostId(c.getInt(c.getColumnIndexOrThrow(KEY_BLOG_POST_ID)));
			post.setSubmitted(Integer.parseInt(c.getString(c.getColumnIndexOrThrow(KEY_SUBMITTED))) != 0);
			post.setPublished(Integer.parseInt(c.getString(c.getColumnIndexOrThrow(KEY_PUBLISHED))) != 0);
			//post.setTags(c.getString(c.getColumnIndexOrThrow(KEY_TAGS)));
			return post;
		}
		catch( CursorIndexOutOfBoundsException e )
		{
			// this will happen if the query that created the cursor did not match any rows
			sLastException = e;
			return null;
		}
	}

	private static ContentValues getValuesFromPost(Post post)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_TITLE, post.getTitle());
		values.put(KEY_CATEGORY_ID, post.getCategoryId());
		values.put(KEY_BODY, post.getBody());
		values.put(KEY_BLOG_ID, post.getBlogId());
		values.put(KEY_BLOG_POST_ID, post.getBlogPostId());
		values.put(KEY_SUBMITTED, post.isSubmitted() ? 1 : 0);
		values.put(KEY_PUBLISHED, post.isPublished() ? 1 : 0);
		//values.put(KEY_TAGS, post.getTags());
		return values;
	}

	public boolean createPost(Post post)
	{
		// post's id will be assigned by the db, so insert it, then get the id and set it on the object
		long rowId = mDb.insert(TABLE_POSTS, null, getValuesFromPost(post));
		if( rowId > -1 )
		{
			Cursor c = mDb.query(TABLE_POSTS, new String[]{ KEY_ID }, SQL_WHERE_ID, new String[]{ Long.toString(rowId) }, null, null, null);
			initCursor(c);
			post.setId(c.getInt(c.getColumnIndexOrThrow(KEY_ID)));
			return true;
		}
		return false;
	}

	public Post getPost(long postId)
	{
		Cursor c = mDb.query(TABLE_POSTS, null, SQL_WHERE_ID, new String[]{ Long.toString(postId) }, null, null, null);
		initCursor(c);
		return getPostFromCursor(c);
	}

	public boolean updatePost(Post post)
	{
		int rows = mDb.update(TABLE_POSTS, getValuesFromPost(post), SQL_WHERE_ID, new String[] {Long.toString(post.getId())});
		if( rows == 0 )
			Log.w(TAG, "No rows updated by updatePost().");
		return rows != 0;
	}

	public boolean deletePost(long postId)
	{
		int rows = mDb.delete(TABLE_POSTS, SQL_WHERE_ID, new String[] {Long.toString(postId)});
		if( rows > 0 )
			deleteImagesForPost(postId);
		return rows > 0;
	}

	public int deletePosts()
	{
		int rows = mDb.delete(TABLE_POSTS, null, null);
		deleteImages();
		return rows;
	}

	// TODO delete corresponding images
	public void deletePostsForBlog(long blogId)
	{
		mDb.execSQL(SQL_DELETE_POSTS_BY_BLOG_ID, new String[]{ Long.toString(blogId) });
	}

	// TODO delete corresponding images
	public int deleteSubmittedPosts()
	{
		return mDb.delete(TABLE_POSTS, SQL_WHERE_POST_SUBMITTED, new String[]{ Integer.toString(1) });
	}

	// TODO delete corresponding images
	public int deleteDrafts()
	{
		return mDb.delete(TABLE_POSTS, SQL_WHERE_POST_PUBLISHED, new String[]{ Integer.toString(0) });
	}

	public Cursor getPostsCursor()
	{
		return mDb.query(TABLE_POSTS, null, null, null, null, null, KEY_ID + " DESC");
	}

	public Cursor getPostsCursorForBlog(long blogId)
	{
		return mDb.query(TABLE_POSTS, null, SQL_WHERE_BLOG_ID, new String[]{ Long.toString(blogId) }, null, null, null);
	}

	public Cursor getPublishedPostsCursor()
	{
		return mDb.query(TABLE_POSTS, null, SQL_WHERE_POST_PUBLISHED, new String[]{ Integer.toString(1) }, null, null, null);
	}

	public Cursor getDraftsCursor()
	{
		return mDb.query(TABLE_POSTS, null, SQL_WHERE_POST_PUBLISHED, new String[]{ Integer.toString(0) }, null, null, null);
	}

	public Vector<Post> getPostsForBlog(long blogId)
	{
		Cursor c = getPostsCursorForBlog(blogId);
		initCursor(c);
		Vector<Post> posts = new Vector<Post>();
		while( posts.size() < c.getCount() )
		{
			posts.add(getPostFromCursor(c));
			c.moveToNext();
		}
		return posts;
	}

	public Vector<Post> getPosts()
	{
		Cursor c = getPostsCursor();
		initCursor(c);
		Vector<Post> posts = new Vector<Post>();
		while( posts.size() < c.getCount() )
		{
			posts.add(getPostFromCursor(c));
			c.moveToNext();
		}
		return posts;
	}

	/*
	 * Image
	 */

	public static Image getImageFromCursor(Cursor c)
	{
		try
		{
			Image img = new Image(
					c.getString(c.getColumnIndexOrThrow(KEY_IMAGE_URI)),
					c.getString(c.getColumnIndexOrThrow(KEY_IMAGE_URL))
			);
			img.setThumbnailUrl(c.getString(c.getColumnIndexOrThrow(KEY_THUMBNAIL_URL)));
			img.setWidth(c.getInt(c.getColumnIndexOrThrow(KEY_WIDTH)));
			img.setHeight(c.getInt(c.getColumnIndexOrThrow(KEY_HEIGHT)));
			img.setThumbnailWidth(c.getInt(c.getColumnIndexOrThrow(KEY_TWIDTH)));
			img.setThumbnailHeight(c.getInt(c.getColumnIndexOrThrow(KEY_THEIGHT)));
			img.setId(c.getLong(c.getColumnIndexOrThrow(KEY_ID)));
			img.setPostId(c.getLong(c.getColumnIndexOrThrow(KEY_POST_ID)));
			return img;
		}
		catch( CursorIndexOutOfBoundsException e )
		{
			sLastException = e;
			return null;
		}
	}

	private static ContentValues getValuesFromImage(Image img)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_IMAGE_URI, img.getUri());
		values.put(KEY_IMAGE_URL, img.getUrl());
		values.put(KEY_POST_ID, img.getPostId());
		values.put(KEY_THUMBNAIL_URL, img.getThumbnailUrl());
		values.put(KEY_WIDTH, img.getWidth());
		values.put(KEY_HEIGHT, img.getHeight());
		values.put(KEY_TWIDTH, img.getThumbnailWidth());
		values.put(KEY_THEIGHT, img.getThumbnailHeight());
		return values;
	}

	public boolean createImage(Image img)
	{
		long rowId = mDb.insert(TABLE_IMAGES, null, getValuesFromImage(img));
		if( rowId > -1 )
		{
			Cursor c = mDb.query(TABLE_IMAGES, new String[]{ KEY_ID }, SQL_WHERE_ID, new String[]{ Long.toString(rowId) }, null, null, null);
			initCursor(c);
			img.setId(c.getInt(c.getColumnIndexOrThrow(KEY_ID)));
			return true;
		}
		return false;
	}

	public Image getImage(long imgId)
	{
		Cursor c = mDb.query(TABLE_IMAGES, null, SQL_WHERE_ID, new String[]{ Long.toString(imgId) }, null, null, null);
		initCursor(c);
		return getImageFromCursor(c);
	}

	public boolean updateImage(Image img)
	{
		int rows = mDb.update(TABLE_IMAGES, getValuesFromImage(img), SQL_WHERE_ID, new String[] {Long.toString(img.getId())});
		if( rows == 0 )
			Log.w(TAG, "No rows updated by updatePost().");
		return rows != 0;
	}

	public boolean deleteImage(long imgId)
	{
		return mDb.delete(TABLE_IMAGES, SQL_WHERE_ID, new String[]{ Long.toString(imgId) }) > 0;
	}

	public int deleteImages()
	{
		return mDb.delete(TABLE_IMAGES, null, null);
	}

	public int deleteImagesForPost(long postId)
	{
		return mDb.delete(TABLE_IMAGES, SQL_WHERE_POST_ID, new String[]{ Long.toString(postId) });
	}

	public Cursor getImagesCursor()
	{
		return mDb.query(TABLE_IMAGES, null, null, null, null, null, null);
	}

	public Cursor getImagesCursorForPost(long postId)
	{
		return mDb.query(TABLE_IMAGES, null, SQL_WHERE_POST_ID, new String[]{ Long.toString(postId) }, null, null, null);
	}

	public Vector<Image> getImages()
	{
		Cursor c = getImagesCursor();
		initCursor(c);
		Vector<Image> images = new Vector<Image>();
		while( images.size() < c.getCount() )
		{
			images.add(getImageFromCursor(c));
			c.moveToNext();
		}
		return images;
	}

	public Vector<Image> getImagesForPost(long postId)
	{
		Cursor c = getImagesCursorForPost(postId);
		initCursor(c);
		Vector<Image> images = new Vector<Image>();
		while( images.size() < c.getCount() )
		{
			images.add(getImageFromCursor(c));
			c.moveToNext();
		}
		return images;
	}

	/*
	 * Testing
	 */
	public void rebuildDb()
	{
		mDb.execSQL(SQL_DROP_TABLE_CONFIG);
		mDb.execSQL(SQL_DROP_TABLE_BLOGS);
		mDb.execSQL(SQL_DROP_TABLE_POSTS);
		mDb.execSQL(SQL_DROP_TABLE_CATEGORIES);

		mDb.execSQL(SQL_CREATE_TABLE_CONFIG);
		mDb.execSQL(SQL_CREATE_TABLE_BLOG);
		mDb.execSQL(SQL_CREATE_TABLE_POSTS);
		mDb.execSQL(SQL_CREATE_TABLE_CATEGORIES);

		ContentValues values = new ContentValues();
		values.put(KEY_DEFAULT_BLOG, "");
		mDb.insert(TABLE_CONFIG, KEY_DEFAULT_BLOG, values);
	}

	public void populateTestData()
	{
		Post post = new Post("Frist Psot!", "4", "This is my frist psot.");
		post.setBlogId(getDefaultBlog().getBlogId());
		createPost(post);
		post = new Post("Sceond Psot...", "1", "This is my sceond psot.");
		post.setBlogId(getDefaultBlog().getBlogId());
		createPost(post);
	}

	public static Exception getLastException()
	{
		return sLastException;
	}
}

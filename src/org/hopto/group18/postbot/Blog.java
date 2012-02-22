/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Vector;

import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;

import android.util.Log;

public class Blog
{
	public enum Alignment { left, center, right};
	public static final Alignment defAlign = Alignment.left;

	public static final String TAG = "Blog";

	public static final String KEY_BLOG_NAME = "blogName";

	public static final String KEY_CATEGORY_NAME = "name";
	public static final String KEY_CATEGORY_DESCRIPTION = "description";
	
	public static final String KEY_FILE_NAME = "name";
	public static final String KEY_FILE_TYPE = "type";
	public static final String KEY_FILE_BITS = "bits";
	public static final String KEY_FILE_OVERWRITE = "overwrite";
	public static final String KEY_FILE_URL = "url";

	public static final boolean DEF_UPLOAD_FILE_OVERWRITE = false;
	
	public static final int IMG_WIDTH = 600;
	public static final int IMG_HEIGHT = 800;

	private String server;
	private String path;
	
	private String blogName;
	private String username;
	private String password;
	
	private long blogId;
	
	private Vector<Category> categories;

	private Alignment align;
	
	private String defaultTags;
	
	public Blog(String name, String server, String path, String username, String password)
	{
		this.blogName = name;
		this.server = server;
		this.path = path;
		this.username = username;
		this.password = password;
		
		this.blogId = 0;
		
		this.categories = new Vector<Category>();
		
		this.align = defAlign;
		
		this.defaultTags = "";
	}
	
	@SuppressWarnings("unchecked")
	public void retrieveBlogName() throws XMLRPCException
	{
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<String> parameters = new Vector<String>();
		parameters.add("0");
		parameters.add(username);
		parameters.add(new String(password));
		
               Object[] response = (Object[]) client.call("blogger.getUsersBlogs", parameters.toArray());
		// get a blog name to use when posting
		if( response.length > 0 )
		{
			HashMap<String,Object> map = (HashMap<String,Object>) response[0];
			Object blogName = map.get(KEY_BLOG_NAME);
			if( blogName != null )
				this.blogName = blogName.toString();
		}
	}

	@SuppressWarnings("unchecked")
	public void retrieveCategories() throws XMLRPCException
	{
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
               Object[] response;
		
		Vector<String> parameters = new Vector<String>();
		parameters.add("0"); // blog id
		parameters.add(username); // username
		parameters.add(password); // password

               // It is possible for there to be no categories.
               // The *new* Blogger API has categories...
		categories.clear();
               try
		{
                       response = (Object[]) client.call("wp.getCategories", parameters.toArray());
                       for( Object o: response )
                       {
                               Category category = new Category((HashMap<String,Object>) o);
                               category.setBlogId(blogId);
                               categories.add(category);
                       }
		}
               catch (org.xmlrpc.android.XMLRPCFault exNotWP)
               {
                       if (exNotWP.getFaultString().equals("Unknown method"))
                       {
                               try
                               {
                                       response = (Object[]) client.call("mt.getCategoryList", parameters.toArray());
                                       for( Object o: response )
                                       {
                                               Category category = new Category((HashMap<String,Object>) o);
                                               category.setBlogId(blogId);
                                               categories.add(category);
                                       }
                               }
                               catch (org.xmlrpc.android.XMLRPCFault exNotMT)
                               {
                                       if (exNotMT.getFaultString().equals("Unknown method"))
                                       {
//                                             try
//                                             {
//                                                     response = (Object[]) client.call("metaWeblog.getCategories", parameters.toArray());
//                                                     // TODO: Handle response.
//                                                     // Doc: http://msdn.microsoft.com/en-us/library/aa905667.aspx
//                                             }
//                                             catch (org.xmlrpc.android.XMLRPCFault exNotMWL)
//                                             {
//                                                     if (exNotMWL.getFaultString() != "Unknown method")
//                                                             throw exNotMWL;
//                                             }
                                       }
                                       else
                                               throw exNotMT;
                               }
                       }
                       else
                               throw exNotWP;
               }
	}
	
	public Object post(Post post, boolean publish, Vector<Image> images) throws IOException, XMLRPCException
	{
		// TODO Post should have the images that go with it so we don't need to take images
		StringBuffer xml = new StringBuffer();
		xml.append("<title>" + post.getTitle() + "</title>\n");
		xml.append("<category>" + post.getCategoryId() + "</category>\n");
		xml.append(post.getHtmlContent() + "\n");
		xml.append("<div align=\"" + getAlign().name() + "\">");
		for( Image img: images )
			xml.append("<br/>\n<a href=\"" + img.getUrl() + "\">\n   <img src=\"" + img.getThumbnailUrl() + "\" alt=\"thumbnail\"/>\n</a>\n");
		xml.append("</div>");
		
		// post to the blog!
		Log.d(TAG, "Sending post: " + xml);
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<Object> parameters = new Vector<Object>();
		Object response;

		// WordPress may not tell us in the response if the original was deleted, so we must check if the post is still there
		// however, I can't use getPost atm, because the XMLRPC code cannot deserialize dateTime
		//if( getPost(post) != null )
		
		// if post has no blogPostId, it is a new post
		if( post.getBlogPostId() == 0 )
		{
			parameters.add("0"); // appkey
			parameters.add(blogName); // blog name
			parameters.add(username); // username
			parameters.add(password); // password
			parameters.add(xml.toString()); // post content
			parameters.add(publish ? "1" : "0"); // publish
			//HashMap<String,String> categoryParameters = new HashMap<String,String>();
			//categoryParameters.put("mt_keywords", "test tags, out");
			//parameters.add(categoryParameters);

                       response = client.call("blogger.newPost", parameters.toArray());

			post.setSubmitted(true);
			post.setPublished(publish);
			
			// for a new post, the postId should be returned as an Integer
			if( response instanceof Integer )
				post.setBlogPostId(((Integer) response).intValue());
			else
				Log.w(TAG, "Post appeared successful, but response was not an Integer!");
		}
		// otherwise, we are editing an existing post
		else
		{
			parameters.add("0"); // appkey
			parameters.add(Integer.toString(post.getBlogPostId())); // post id
			parameters.add(username); // username
			parameters.add(password); // password
			parameters.add(xml.toString()); // post content
			parameters.add(publish ? "1" : "0"); // publish

                       response = client.call("blogger.editPost", parameters.toArray());

			if( response instanceof Boolean )
			{
				if( ! (Boolean) response )
					throw new XMLRPCException("Editing post failed.");
			}
			else
				Log.w(TAG, "Editing post appeared successful, but response was not Boolean!");
			
			post.setSubmitted(true);
			post.setPublished(publish);
		}
		
		return response;
	}

	// TODO this fails because dateTime cannot be deserialized
	public String getPost(Post post) throws XMLRPCException
	{
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<Object> parameters = new Vector<Object>();
		parameters.add("0"); // appkey
		parameters.add(Integer.toString(post.getBlogPostId())); // post id
		parameters.add(username); // username
		parameters.add(password); // password

               Object response = client.call("blogger.getPost", parameters.toArray());
		
		if( response instanceof String )
			return (String) response;
		
		return null;
	}
	
	public Object deletePost(Post post) throws XMLRPCException
	{
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<Object> parameters = new Vector<Object>();
		parameters.add("0"); // appkey
		parameters.add(Integer.toString(post.getBlogPostId())); // post id
		parameters.add(username); // username
		parameters.add(password); // password
		parameters.add(false); // publish
		
               Object response = client.call("blogger.deletePost", parameters.toArray());
		
		post.setSubmitted(false);
		post.setPublished(false);
		post.setBlogPostId(0);

		return response;
	}
	
	public Object addCategory(Category category) throws XMLRPCException
	{
		// attempt to add it to the blog
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<Object> parameters = new Vector<Object>();
		parameters.add(blogName); // blog name
		parameters.add(username); // username
		parameters.add(password); // password
		HashMap<String,String> categoryParameters = new HashMap<String,String>();
		categoryParameters.put(KEY_CATEGORY_NAME, category.getName());
		categoryParameters.put(KEY_CATEGORY_DESCRIPTION, category.getDescription());
		parameters.add(categoryParameters);

               Object response = client.call("wp.newCategory", parameters.toArray());
		
		int categoryId = 0;
		// my wordpress blogs both give back Integer
		if( response instanceof Integer )
			categoryId = (Integer) response;
		// wordpress.com seems to give back String
		else if( response instanceof String )
		{
			try
			{
				categoryId = Integer.parseInt(response.toString());
			}
			catch( NumberFormatException e )
			{
				Log.e(TAG, "Received non-integer categoryId from blog: " + response);
			}
		}
		
		if( categoryId == 0 )
			throw new XMLRPCException("Adding category failed.");
		
		category.setId(Integer.toString(categoryId));
		categories.add(category);
		category.setBlogId(blogId);
		
		return response;
	}

	@SuppressWarnings("unchecked")
	public String uploadImage(Image img) throws XMLRPCException
	{
		Log.d(TAG, "Uploading image " + img.getUri());
		
		XMLRPCClient client = new XMLRPCClient(URI.create(server + path));
		Vector<Object> parameters = new Vector<Object>();
		
		parameters.add(blogName); // blog name
		parameters.add(username); // username
		parameters.add(password); // password
		HashMap<String,Object> imageParameters = new HashMap<String,Object>();
		imageParameters.put(KEY_FILE_NAME, img.getName());
		imageParameters.put(KEY_FILE_TYPE, "image/jpeg");
		imageParameters.put(KEY_FILE_BITS, img.getContent());
		imageParameters.put(KEY_FILE_OVERWRITE, Boolean.toString(DEF_UPLOAD_FILE_OVERWRITE));
		parameters.add(imageParameters);
               Object response = client.call("wp.uploadFile", parameters.toArray());

		HashMap<String,String> map = (HashMap<String,String>) response;
		
		img.setUrl(map.get(KEY_FILE_URL));
		
		byte[] thumbnail = img.getThumbnailContent();
		if( thumbnail != null )
		{
			imageParameters.put(KEY_FILE_NAME, img.getThumbnailName());
			imageParameters.put(KEY_FILE_BITS, thumbnail);

                       response = client.call("wp.uploadFile", parameters.toArray());

			map = (HashMap<String,String>) response;
			
			img.setThumbnailUrl(map.get(KEY_FILE_URL));
		}
		
		return img.getUrl();
	}
	
	public int getCategoryIndex(String categoryId)
	{
		for( int i=0; i<categories.size(); i++ )
			if( categories.get(i).getId().equals(categoryId) )
				return i;
		return -1;
	}

	private void setCategoryBlogIds()
	{
		for( Category category: categories )
			category.setBlogId(blogId);
	}
	
	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getBlogName() {
		return blogName;
	}

	public void setBlogName(String blogName) {
		this.blogName = blogName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public Vector<Category> getCategories()
	{
		return categories;
	}
	
	public void setCategories(Vector<Category> categories)
	{
		this.categories = categories;
	}
	
	public long getBlogId()
	{
		return blogId;
	}

	public void setBlogId(long blogId)
	{
		this.blogId = blogId;
		setCategoryBlogIds();
	}

	@Override
	public String toString()
	{
		return blogName;
	}

	public Alignment getAlign() {
		return align;
	}

	public void setAlign(Alignment align) {
		this.align = align;
	}

	public String getDefaultTags() {
		return defaultTags;
	}

	public void setDefaultTags(String defaultTags) {
		this.defaultTags = defaultTags;
	}
}

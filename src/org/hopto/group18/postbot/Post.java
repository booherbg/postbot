/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

public class Post {

	private String mCategoryId;
	private String mBody;
	private String mTitle;
	private boolean mSubmitted;
	private boolean mPublished;
	private long mId; // this is the id given by our database locally
	private int mBlogPostId; // this is the id given to the post by the blog (used for editing posts)
	private long mBlogId;
	private String mTags;
	
	public Post(String title, String categoryId, String content)
	{
		setTitle(title);
		setCategoryId(categoryId);
		setBody(content);
		
		mId = 0;
		mBlogPostId = 0;
		mBlogId = 0;
		mSubmitted = false;
		mPublished = false;
		mTags = "";
	}

	public long getId()
	{
		return mId;
	}
	
	public void setId(long id)
	{
		mId = id;
	}
	
	public String getTitle()
	{
		return mTitle;
	}
	
	public void setTitle(String title)
	{
		mTitle = title;
	}
	
	public String getCategoryId()
	{
		return mCategoryId;
	}
	
	public void setCategoryId(String category)
	{
		mCategoryId = category;
	}
	
	public String getBody()
	{
		return mBody;
	}

	public String getHtmlContent()
	{
		return mBody;

	}
	public void setBody(String post)
	{
		mBody = post;
	}
	
	public String toString()
	{
		return mTitle + "\n" + mBody;
	}

	public boolean isSubmitted()
	{
		return mSubmitted;
	}

	public void setSubmitted(boolean submitted)
	{
		mSubmitted = submitted;
	}

	public long getBlogId()
	{
		return mBlogId;
	}

	public void setBlogId(long blogId)
	{
		mBlogId = blogId;
	}

	public int getBlogPostId()
	{
		return mBlogPostId;
	}

	public void setBlogPostId(int blogPostId)
	{
		mBlogPostId = blogPostId;
	}

	public boolean isPublished()
	{
		return mPublished;
	}

	public void setPublished(boolean published)
	{
		mPublished = published;
	}

	public String getTags() {
		return mTags;
	}

	public void setTags(String tags) {
		this.mTags = tags;
	}
}

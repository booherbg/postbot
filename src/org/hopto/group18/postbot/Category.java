/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.util.HashMap;

public class Category
{

	public static final String KEY_ID = "categoryId";
	public static final String KEY_NAME = "categoryName";
	public static final String KEY_DESC = "description";

	private String mId; // id the blog gives to this category
	private String mName;
	private String mDescription;
	
	private long categoryId; // id the db gives to the category
	private long blogId;
	
	public Category(String id, String name, String description)
	{
		this.mId = id;
		this.mName = name;
		this.mDescription = description;
		
		this.categoryId = -1;
		this.blogId = -1;
	}
	
	public Category(HashMap<String,Object> map)
	{
		this.mId = (String) map.get(KEY_ID);
		this.mName = (String) map.get(KEY_NAME);
		this.mDescription = (String) map.get(KEY_DESC);
	}

	public String getId()
	{
		return mId;
	}

	public void setId(String id)
	{
		mId = id;
	}

	public String getName()
	{
		return mName;
	}

	public void setName(String name)
	{
		mName = name;
	}

	public String getDescription()
	{
		return mDescription;
	}

	public void setDescription(String description)
	{
		mDescription = description;
	}
	
	@Override
	public String toString()
	{
		return mName;
	}

	public long getCategoryId()
	{
		return categoryId;
	}

	public void setCategoryId(long categoryId)
	{
		this.categoryId = categoryId;
	}

	public long getBlogId()
	{
		return blogId;
	}

	public void setBlogId(long blogId)
	{
		this.blogId = blogId;
	}
	
}

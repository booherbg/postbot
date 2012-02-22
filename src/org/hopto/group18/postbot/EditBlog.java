/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.security.InvalidParameterException;
import java.sql.SQLException;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditBlog extends Activity
{
	public static final String TAG = "EditBlog";
	
	public static final int ACTIVITY_CREATE = 0;
	public static final int ACTIVITY_EDIT = 1;

	public static final int DIALOG_DELETE_BLOG = 0;
	public static final int DIALOG_CONFIRM_CANCEL = 1;
	
	public static final String KEY_BLOG_ID = "blogId";
	
	private Button mCancel; 
	private Button mSave;
	
	private EditText mServer;
	private EditText mPath;
	private EditText mUsername;
	private EditText mPassword;
	//private EditText mDefaultTags;
	
	private TextView mOutput;

	private Spinner mBlogs;
	private Spinner mImageAlignment;
	
	private PostBotDbAdapter mDbAdapter;
	private Blog mBlog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) throws InvalidParameterException
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.editblog);
		
		mDbAdapter = new PostBotDbAdapter(this, this);
		try {
			mDbAdapter.open();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new InvalidParameterException(e.getMessage());
		}
		
		mCancel = (Button) findViewById(R.id.btn_cancel_edit_blog);
		mSave = (Button) findViewById(R.id.btn_next_edit_blog);

		mServer = (EditText) findViewById(R.id.edit_blog_server);
		mPath = (EditText) findViewById(R.id.edit_blog_path);
		mUsername = (EditText) findViewById(R.id.edit_blog_username);
		mPassword = (EditText) findViewById(R.id.edit_blog_password);
		//mDefaultTags = (EditText) findViewById(R.id.edit_default_tags);
		
		mOutput = (TextView) findViewById(R.id.edit_blog_output);
		
    	mBlogs = (Spinner) findViewById(R.id.spn_blogs);
    	mImageAlignment = (Spinner) findViewById(R.id.edit_blog_alignment);

    	ArrayAdapter<Blog.Alignment> alignAdapter = new ArrayAdapter<Blog.Alignment>(this, android.R.layout.simple_spinner_item, Blog.Alignment.values());
    	alignAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mImageAlignment.setAdapter(alignAdapter);
    	
    	mCancel.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View view)
			{
				showDialog(DIALOG_CONFIRM_CANCEL);
			}
		});
		
		mSave.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View view)
			{
				refreshOutput();

				Bundle bundle = new Bundle();
				Intent intent = new Intent();
				
				populateBlog();

				// new blog
				if( mBlog.getBlogId() == 0 )
				{
					try
					{
						mOutput.setText("Retrieving blog name...\n");
						mBlog.retrieveBlogName();
						
						// check for this name in the db already
						if( mDbAdapter.getBlog(mBlog.getBlogName()) == null )
						{
							mOutput.append("Retrieving blog categories...\n");
							mBlog.retrieveCategories();
							if( mDbAdapter.createBlog(mBlog) && mDbAdapter.setDefaultBlog(mBlog.getBlogName()) )
							{
								bundle.putString(PostBot.KEY_ACTIVITY_RESULT, "" + mBlog + " is now the active blog.");
								setResult(RESULT_OK, intent);
								intent.putExtras(bundle);
								finish();
							}
							else
								makeToast("An unknown database error occurred while creating this blog.", Toast.LENGTH_LONG);
						}
						else
							makeToast("A blog with the name '" + mBlog.getBlogName() + "' is already configured.", Toast.LENGTH_LONG);
					}
					catch( Exception e )
					{
						makeToast("An error occurred saving blog: " + e.getMessage(), Toast.LENGTH_LONG);
					}
				}
				// existing blog
				else
				{
					mDbAdapter.setDefaultBlog(mBlog.getBlogName());
					mDbAdapter.updateBlog(mBlog);
					bundle.putString(PostBot.KEY_ACTIVITY_RESULT, "" + mBlog + " is now the active blog.");
					setResult(RESULT_OK, intent);
					intent.putExtras(bundle);
					finish();
				}
			}
		});

		mBlogs.setOnItemSelectedListener(new Spinner.OnItemSelectedListener()
		{

			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				SQLiteCursor c = (SQLiteCursor) mBlogs.getSelectedItem();
				mBlog = PostBotDbAdapter.getBlogFromCursor(c);
				populateFields();
			}

			public void onNothingSelected(AdapterView<?> parent)
			{
			}
			
		});
		
		initializeBlog(savedInstanceState);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = new MenuInflater(this);
		inflater.inflate(R.menu.editblog, menu);
		return true;
	}

    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		super.onMenuItemSelected(featureId, item);
		
		switch( item.getItemId() )
		{
		case R.id.editblog_new_blog:
			createBlog();
			break;
		case R.id.editblog_delete_blog:
	    	showDialog(DIALOG_DELETE_BLOG);
			break;
		}
		
		return true;
	}

	public void refreshOutput()
	{
		mOutput.setText("");
	}
	
	public void initializeBlog(Bundle savedInstanceState)
	{
		Bundle extras = getIntent().getExtras();
		int action = extras != null ? extras.getInt(PostBot.KEY_ACTIVITY_ACTION) : -1;
		switch( action )
		{
		// use one if given, but if not, try the default blog
		case ACTIVITY_EDIT:
			long blogId = extras != null ? extras.getLong(KEY_BLOG_ID) : 0;
			if( blogId == 0 )
				mBlog = mDbAdapter.getDefaultBlog();
			else
				mBlog = mDbAdapter.getBlog(blogId);

			// if we have a blog, use it and break, otherwise just fall into CREATE
			if( mBlog != null )
			{
				refreshBlogs();
				populateFields();
				break;
			}
		case ACTIVITY_CREATE:
			mBlog = new Blog("New Blog", mServer.getText().toString(), mPath.getText().toString(), mUsername.getText().toString(), mPassword.getText().toString());
			mBlogs.setEnabled(false);
			break;
		default:
			throw new InvalidParameterException("Unknown Action code '" + action + "'.");
		}
	}
	
    public void refreshBlogs()
    {
    	Cursor c = mDbAdapter.getBlogsCursor();
    	startManagingCursor(c);
    	SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, c, new String[]{PostBotDbAdapter.KEY_BLOG_NAME}, new int[]{ android.R.id.text1});
    	adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mBlogs.setAdapter(adapter);
    }
    
	private void populateFields()
	{
		mServer.setText(mBlog.getServer());
		mPath.setText(mBlog.getPath());
		mUsername.setText(mBlog.getUsername());
		mPassword.setText(mBlog.getPassword());
    	int selection = mDbAdapter.getBlogIndex(mBlog.getBlogId());
    	mBlogs.setSelection(selection);
    	mBlogs.setSelected(true);
    	mImageAlignment.setSelection(mBlog.getAlign().ordinal());
    	mImageAlignment.setSelected(true);
    	//mDefaultTags.setText(mBlog.getDefaultTags());
	}
	
	private void populateBlog()
	{
		String server = mServer.getText().toString();
		if( server.charAt(server.length()-1) != '/' )
			server += "/";
		mBlog.setServer(server);
		String path = mPath.getText().toString();
		mBlog.setPath(path);
		if( path.charAt(path.length()-1) != '/' )
			path += "/";
		mBlog.setUsername(mUsername.getText().toString());
		mBlog.setPassword(mPassword.getText().toString());
		mBlog.setAlign(Blog.Alignment.values()[mImageAlignment.getSelectedItemPosition()]);
		//mBlog.setDefaultTags(mDefaultTags.getText().toString());
	}
	
	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch( id )
		{
		case DIALOG_DELETE_BLOG:
            return new AlertDialog.Builder(this)
            .setMessage("Delete blog " + mBlog.getBlogName() + "?\n\nAll posts for this blog will be deleted from PostBot.")
            .setPositiveButton("OK", new OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					deleteBlog();
				}
            })
            .setNegativeButton(
    		"Cancel", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		case DIALOG_CONFIRM_CANCEL:
            return new AlertDialog.Builder(this)
            .setMessage("Are you sure you wish to cancel?\n\nAny changes will be lost.")
            .setPositiveButton("Yes", new OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					finish();
				}
            })
            .setNegativeButton(
    		"No", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mDbAdapter.close();
	}

    public void createBlog()
    {
    	Intent i = new Intent(this, EditBlog.class);
    	i.putExtra(PostBot.KEY_ACTIVITY_ACTION, EditBlog.ACTIVITY_CREATE);
    	startActivityForResult(i, PostBot.ACTIVITY_NEW_BLOG);
    }
    
    public void deleteBlog()
    {
		FileManager.setCacheDir(getCacheDir());
    	Vector<Post> posts = mDbAdapter.getPostsForBlog(mBlog.getBlogId());
    	for( Post post: posts )
    		FileManager.deleteThumbnailsForPost(post);
		mDbAdapter.deleteBlog(mBlog.getBlogId());
		Vector<Blog> blogs = mDbAdapter.getBlogs();
		if( blogs.size() > 0 )
		{
			mBlog = blogs.get(0);
			mDbAdapter.setDefaultBlog(mBlog.getBlogName());
			refreshBlogs();
		}
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
        super.onActivityResult(requestCode, resultCode, intent);
        
        String message = null;
        int duration = Toast.LENGTH_SHORT;
        Bundle extras = intent != null ? intent.getExtras() : null;
        if( extras != null )
        	message = extras.getString(PostBot.KEY_ACTIVITY_RESULT);
        
        switch( resultCode )
        {
        case RESULT_OK:
        	break;
        case RESULT_CANCELED:
        	break;
        default:
        	if( message == null )
        	{
        		Log.w(TAG, "Subactivity failed, but no message returned.");
        		message = PostBot.DEF_FAILURE_MESSAGE;
        	}
        }
        if( message != null )
        	makeToast(message, duration);
       	initializeBlog(null);
	}
    
	public void makeToast(String message, int duration)
	{
		Toast.makeText(this, message, duration).show();
	}
}

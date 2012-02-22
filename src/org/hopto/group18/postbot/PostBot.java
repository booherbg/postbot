/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class PostBot extends Activity {

	public static final String TAG = "PostBot";
	
	public static final String POSTBOT = "PostBot";
	
	public static final int ACTIVITY_EDIT_BLOG = 0;
	public static final int ACTIVITY_NEW_BLOG = 1;
	public static final int ACTIVITY_EDIT_POST = 2;
	public static final int ACTIVITY_NEW_POST = 3;
	
	public static final String POST_STATUS_DRAFT = "[D]";
	public static final String POST_STATUS_MODIFIED = "[*]";
	
	public static final String KEY_ACTIVITY_ACTION = "activity_action";
	public static final String KEY_ACTIVITY_RESULT = "activity_result";
	
	public static final int DIALOG_CONFIRM_FORGET_POSTS = 0;
	public static final int DIALOG_CREATE_BLOG = 1;
	
	public static final String DEF_OK_MESSAGE = "The operation was successful.";
	public static final String DEF_CANCELED_MESSAGE = "The operation was canceled.";
	public static final String DEF_FAILURE_MESSAGE = "The operation was not successful!";
	
	public static final String DEF_TITLE_STRING = "[no title]";
	
	public static final int HTTP_SOCKET_TIMEOUT = 5000;
	
	private PostBotDbAdapter mDbAdapter;
	
	private Button mNewPost;
	private ListView mPosts;

	private Blog mBlog;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try
        {
            setContentView(R.layout.main);
        	
        	mDbAdapter = new PostBotDbAdapter(this, this);
        	mDbAdapter.open();

        	mBlog = mDbAdapter.getDefaultBlog();
        	
    		FileManager.setCacheDir(getCacheDir());

    		mNewPost = (Button) findViewById(R.id.new_post);
        	mPosts = (ListView) findViewById(R.id.lst_posts);
        	
        	mNewPost.setOnClickListener(new OnClickListener()
        	{
        		public void onClick(View view)
        		{
        			createPost();
        		}
        	});
        	
        	mPosts.setOnItemClickListener(new ListView.OnItemClickListener()
        	{
				public void onItemClick(AdapterView<?> parent, View view, int position, long id)
				{
					Post post = mDbAdapter.getPost(id);
					editPost(post);					
				}
        	});
        	
        	// if there is no blog yet, take the user to creating a blog
        	if( mBlog == null )
        		showDialog(DIALOG_CREATE_BLOG);

    		refresh();
        	
        }
        catch( Exception e )
        {
        	e.printStackTrace();
        }
        
    }

    @Override
	protected void onDestroy()
	{
		super.onDestroy();
		mDbAdapter.close();
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        String message = null;
        int duration = Toast.LENGTH_SHORT;
        Bundle extras = intent != null ? intent.getExtras() : null;
        if( extras != null )
        	message = extras.getString(KEY_ACTIVITY_RESULT);
        
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
        		message = DEF_FAILURE_MESSAGE;
        	}
        	duration = Toast.LENGTH_LONG;
        }
        
        // since the default blog may have been changed byt the activity, read it again before refresh
        mBlog = mDbAdapter.getDefaultBlog();
        
        refresh();
        
        if( message != null )
        	makeToast(message, duration);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = new MenuInflater(this);
		inflater.inflate(R.menu.postbot, menu);
		return true;
	}

    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		super.onMenuItemSelected(featureId, item);
		
		switch( item.getItemId() )
		{
		case R.id.settings_menu_item:
			editBlog(mBlog);
			break;
		case R.id.postbot_forget_posts:
			showDialog(DIALOG_CONFIRM_FORGET_POSTS);
			break;
		}
		
		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch( id )
		{
		case DIALOG_CONFIRM_FORGET_POSTS:
            return new AlertDialog.Builder(this)
            .setMessage("Forget all saved posts?\n\nNo posts will be deleted from blogs, but all posts and drafts will be forgotten by PostBot.")
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					forgetPosts();
					makeToast("Posts forgotten.", Toast.LENGTH_SHORT);
					refreshPosts();
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		case DIALOG_CREATE_BLOG:
            return new AlertDialog.Builder(this)
            .setTitle(POSTBOT)
            .setMessage("Welcome to PostBot!\n\nYou will need to set up a blog before you can post. Press 'OK' to set up a blog, or press 'Cancel' to exit PostBot.")
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					createBlog();
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
					finish();
				}
    		}).setIcon(R.drawable.postbot).create();
            default:
            	return super.onCreateDialog(id);
		}
	}

	public void refresh()
    {
    	refreshPosts();
    }
    
    public void refreshPosts()
    {
    	// TODO if there are no posts, just display an item that says so
    	Cursor c = mDbAdapter.getPostsCursor();
    	startManagingCursor(c);
    	SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.post_row, c, new String[]{ PostBotDbAdapter.KEY_TITLE, PostBotDbAdapter.KEY_BODY }, new int[]{ R.id.post_name, R.id.post_body })
    	{
    		// override the method that populates the views in the list
    		public View getView(int position, View convertView, ViewGroup parent)
    		{
    			try
    			{
    				if( convertView == null )
    					convertView = super.getView(position, convertView, parent);
    				
    				TextView title = (TextView) convertView.findViewById(R.id.post_name);
    				TextView blog = (TextView) convertView.findViewById(R.id.post_blog);
    				TextView body = (TextView) convertView.findViewById(R.id.post_body);
    				Cursor cursor = getCursor();
    				cursor.moveToPosition(position);

    				Post p = PostBotDbAdapter.getPostFromCursor(cursor);
    				String t = p.getTitle();
    				String bl = Long.toString(p.getBlogId());
    				String b = p.getBody();

    				if( t == null || t.equals("") )
    					t = DEF_TITLE_STRING;
    					
    				if( ! p.isSubmitted() )
    				{
    					// not submitted, not published: draft
    					if( ! p.isPublished() )
    						t = POST_STATUS_DRAFT + " " + t;
    					// not submitted, published: modified
    					else
    						t = POST_STATUS_MODIFIED + " " + t;
    				}
    				
					// if there are multiple blogs defined, show the blog indicator
    				cursor = mDbAdapter.getBlogsCursor();
    				startManagingCursor(cursor);
					if( cursor.getCount() > 1 )
					{
	    				// get name of blog
	    				try
	    				{
	    					bl = "(" + mDbAdapter.getBlog(Long.parseLong(bl)).getBlogName() + ")";
	    				}
	    				catch( Exception e )
	    				{
	    					Log.e(TAG, "Unable to find blog with ID '" + bl + "'!");
	    					bl = "(Unknown blog)";
	    				}
					}
					else
						blog.setHeight(0);
					
					// populate the view
    				title.setText(t);
    				blog.setText(bl);
    				body.setText(b);

    				return convertView;
    			}
    			catch( Exception e )
    			{
    				Log.e(TAG, "An error occurred while attempting to show list of posts: " + e.getMessage());
    				e.printStackTrace();
    				return super.getView(position, convertView, parent);
    			}
    		}
    	};
    	mPosts.setAdapter(adapter);
    }
    
    public void forgetPosts()
    {
    	Vector<Post> posts = mDbAdapter.getPosts();
    	for( Post post: posts )
			FileManager.deleteThumbnailsForPost(post);
    	mDbAdapter.deletePosts();
    }
    
    public void createPost()
    {
    	// clear out any leftover images in the db for posts that were never submitted so they don't show up in the new post
    	mDbAdapter.deleteImagesForPost(0);
    	
        Intent i = new Intent(this, EditPost.class);
        startActivityForResult(i, ACTIVITY_NEW_POST);
    }
    
    public void editPost(Post post)
    {
    	Intent i = new Intent(this, EditPost.class);
    	i.putExtra(EditPost.KEY_POST_ID, post.getId());
    	startActivityForResult(i, ACTIVITY_EDIT_POST);
    }
    
    public void createBlog()
    {
    	Intent i = new Intent(this, EditBlog.class);
    	i.putExtra(KEY_ACTIVITY_ACTION, EditBlog.ACTIVITY_CREATE);
    	startActivityForResult(i, ACTIVITY_NEW_BLOG);
    }
    
    public void editBlog(Blog blog)
    {
    	Intent i = new Intent(this, EditBlog.class);
    	i.putExtra(KEY_ACTIVITY_ACTION, EditBlog.ACTIVITY_EDIT);
    	startActivityForResult(i, ACTIVITY_EDIT_BLOG);
    }

    public void makeToast(String message, int duration)
    {
    	Toast.makeText(this, message, duration).show();
    }
    
}
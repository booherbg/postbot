/* Copyright 2009 Nicholas Newberry
 * This software is licensed under the GNU GPLv3.
 * See license.txt for details.
 */

package org.hopto.group18.postbot;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.sql.SQLException;
import java.util.Random;
import java.util.Vector;

import org.xmlrpc.android.XMLRPCException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditPost extends Activity
{
	public static final String TAG = "EditPost";
	
	public static final int ERR_DELAY = 250;
	public static final int MAX_UPLOAD_ATTEMPTS = 2;
	public static final int UPLOAD_FAILURE_SLEEP = 2000;
	
	public static final int ACTIVITY_CREATE = 0;
	public static final int ACTIVITY_EDIT = 1;
	public static final int ACTIVITY_GET_IMAGE = 2;	

	public static final int MSG_REFRESH_IMAGES = 0;
	public static final int MSG_REFRESH_CATEGORIES = 0;
	public static final int MSG_REFRESH_ALL = 0;
	
	public static final int MSG_THREAD_STARTING = 0;
	public static final int MSG_THREAD_PROGRESS = 1;
	public static final int MSG_THREAD_DONE = 2;
	public static final int MSG_THREAD_EXITING = 3;
	public static final int MSG_THREAD_FAILURE = 4;
	
	public static final int MSG_PROGRESS = 0;
	
	public static final int MSG_OK = 0;
	public static final int MSG_ERR = 1;

	public static final int DIALOG_NEW_CATEGORY = 0;
	public static final int DIALOG_CONFIRM_FORGET_POST = 1;
	public static final int DIALOG_REMOVE_IMAGE = 2;
	public static final int DIALOG_CONFIRM_REMOVE_IMAGES = 3;
	public static final int DIALOG_OOME = 4;
	public static final int DIALOG_SUBMITTING = 5;
	public static final int DIALOG_ERROR_SUBMITTING = 6;
	
	public static final String ACTION_SEND = "android.intent.action.SEND";
	
	public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";

	public static final String KEY_BLOG_ID = "blogId";
	public static final String KEY_POST_ID = "postId";
	public static final String KEY_LOCK_OBJECT = "lockObject";
	public static final String KEY_BITMAPS = "bitmaps";
	public static final String KEY_SHOW_DIALOG_PROGRESS = "showSubmitting";
	public static final String KEY_DIALOG_MESSAGE = "dialogMessage";
	
	private String mBitmapLock;
	
	private EditText mTitle;
	private Spinner mCategories;
	private EditText mBody;
	private Button mSubmit;
	private Button mSaveDraft;
	private TextView mBlogName;
	
	private TextView mImagesLabel;
	private Gallery mImageGallery;
	
	private Blog mBlog;
	private Post mPost;
	private PostBotDbAdapter mDbAdapter;
	
	//private EditText mTags;
	
	private Image mSelectedImage;

	private Vector<Bitmap> mBitmaps;
	private Bitmap mSelectedBitmap;
	
	private Vector<ImageView> mImageViews;
	private ImageView mSelectedImageView;
	
	private ProgressDialog mProgress;
	private String mProgressMessage;
	
	private Handler mHandlerImageUpdate;
	private Handler mHandlerProgress;
	private Handler mHandlerThreadEvent;
	private Handler mHandlerToast;
	
	private Thread mImageLoader;
	private boolean mRequestImageLoaderStop;
	
	private boolean mForceUploadImages;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "==========================[ onCreate ]==========================");
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.editpost);
        
    	try
        {
	        initUi();
	
	        initThreads();
	
	    	initData(savedInstanceState);

	    	initPost(savedInstanceState);
	    	
	    	initImages(savedInstanceState);
	    	
        	refresh();
        	
	        refreshStatusMsg();
        }
        catch( Exception e )
        {
        	e.printStackTrace();
        	makeToast(e.getMessage(), Toast.LENGTH_LONG);
        	setInputElementsEnabled(false);
        }
    }

    private void initUi()
    {
        mBody = (EditText) findViewById(R.id.post);
        mTitle = (EditText) findViewById(R.id.title);
        mSubmit = (Button) findViewById(R.id.submit);
        mSaveDraft = (Button) findViewById(R.id.save_draft);
        mBlogName = (TextView) findViewById(R.id.status);
        mCategories = (Spinner) findViewById(R.id.category);
        mImageGallery = (Gallery) findViewById(R.id.gal_images);
        mImagesLabel = (TextView) findViewById(R.id.txt_images);
        //mTags = (EditText) findViewById(R.id.tags);
        
        mSelectedImage = null;        
        mSelectedImageView = null;

        mProgress = null;
        
		mCategories.setOnLongClickListener(new OnLongClickListener() {
        	public boolean onLongClick(View view)
        	{
        		showDialog(DIALOG_NEW_CATEGORY);
        		return true;
        	}
        });
        
        mSubmit.setOnClickListener(new OnClickListener() {
        	public void onClick(View view)
        	{
				mRequestImageLoaderStop = true;
    			submit(true);
        	}
        });
        if( mPost != null && mPost.isSubmitted() )
        	mSubmit.setText(getResources().getText(R.string.update));
        
        mSaveDraft.setOnClickListener(new OnClickListener()
        {
        	public void onClick(View view)
        	{
				mRequestImageLoaderStop = true;

        		try
        		{
        			if( saveDraft() )
        				makeToast("Draft saved.", Toast.LENGTH_SHORT);
        			else
        				makeToast("Unable to save draft!", Toast.LENGTH_LONG);
        		}
        		catch( Exception e )
        		{
        			makeToast("Error while saving draft: " + e.getMessage(), Toast.LENGTH_LONG);
        		}
        		finish();
        	}
        });

        mImageGallery.setOnItemClickListener(new Gallery.OnItemClickListener()
        {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id)
			{
				Cursor c = mDbAdapter.getImagesCursorForPost(mPost.getId());
				startManagingCursor(c);
				c.moveToPosition(position);
				mSelectedImage = PostBotDbAdapter.getImageFromCursor(c);
				mSelectedBitmap = position < mBitmaps.size() ? mBitmaps.get(position) : null;
				mSelectedImageView = mImageViews.get(position);
				showDialog(DIALOG_REMOVE_IMAGE);
			}
        });
    }
    
    private void setProgressMessage(Message msg)
    {
		if( mProgress != null )
			if( msg.obj != null )
			{
				mProgress.setMessage(msg.obj.toString());
				mProgressMessage = msg.obj.toString();
			}
			else
			{
				mProgress.setProgress(msg.arg1);
			}
    }
    
    private void initThreads()
    {
        mHandlerImageUpdate = new Handler()
        {
        	@Override
            public void handleMessage(Message msg)
            {
        		refreshImage(msg.what);
            }
        };
        
        mHandlerProgress = new Handler()
        {
        	@Override
            public void handleMessage(Message msg)
            {
        		switch( msg.what )
        		{
        		case MSG_PROGRESS:
        			setProgressMessage(msg);
        			break;
        		}
            }
        };
        
        mHandlerThreadEvent = new Handler()
        {
        	@Override
            public void handleMessage(Message msg)
            {
        		switch( msg.what )
        		{
        		case MSG_THREAD_STARTING:
        	        Log.d(TAG, "==========================[ THREAD_STARTING (" + msg.obj + ") ]==========================");
        			break;
        		case MSG_THREAD_PROGRESS:
        	        Log.d(TAG, "==========================[ THREAD_PROGRESS (" + msg.obj + ") ]==========================");
        			break;
        		case MSG_THREAD_DONE:
        	        Log.d(TAG, "==========================[ THREAD_DONE (" + msg.obj + ") ]==========================");
        			break;
        		case MSG_THREAD_EXITING:
        	        Log.d(TAG, "==========================[ THREAD_EXITING (" + msg.obj + ") ]==========================");
        			break;
        		case MSG_THREAD_FAILURE:
        			Log.d(TAG, "==========================[ THREAD_FAILURE (" + msg.obj + ") ]==========================");
        			if( msg.obj != null )
        				makeToast(msg.obj.toString(), Toast.LENGTH_LONG);
        			else
        				makeToast("An error occurred while attempting to publish this post.", Toast.LENGTH_LONG);
        		}
            }
        };

        mHandlerToast = new Handler()
        {
        	@Override
        	public void handleMessage(Message msg)
        	{
        		switch( msg.what )
        		{
        		case MSG_OK:
        			makeToast(msg.obj.toString(), Toast.LENGTH_SHORT);
        			break;
        		case MSG_ERR:
        			makeToast(msg.obj.toString(), Toast.LENGTH_LONG);
        			break;
        		}
        	}
        };
        
		mImageLoader = new Thread(new Runnable() {
			public void run()
			{
				long id = new Random().nextLong();
				sendMessage(mHandlerThreadEvent, MSG_THREAD_STARTING, id);
				Vector<Image> images = mDbAdapter.getImagesForPost(mPost.getId());
				for( int j=mBitmaps.size(); j<images.size(); j++ )
				{
					// check for requested stop
					if( mRequestImageLoaderStop )
					{
						sendMessage(mHandlerThreadEvent, MSG_THREAD_EXITING, id);
						return;
					}
					
					// if the image is still within the limits of the vector, update it
					// (elements could have been removed by the UI thread)
					if( j < mImageViews.size() )
					{
						try
						{
							Bitmap bm = getImageGalleryThumbnail(images.get(j));
							if( bm == null )
							{
								sendMessage(mHandlerThreadEvent, MSG_THREAD_FAILURE, id);
								Log.d(TAG, "An error occurred while loadig the gallery thumbnail for " + images.get(j) + ".");
								mBitmaps.add(null);
							}
							if( j < mBitmaps.size() )
								mBitmaps.set(j, bm);
							else
								mBitmaps.add(bm);
							System.gc();
							sendMessage(mHandlerImageUpdate, j);
						}
						catch( IOException e )
						{
							sendMessage(mHandlerThreadEvent, MSG_THREAD_FAILURE, id);
							e.printStackTrace();
							mBitmaps.add(null);
						}
						catch( ArrayIndexOutOfBoundsException e )
						{
							Log.w(TAG, "Skipping item " + j + " because it has been removed from the list.");
							e.printStackTrace();
						}
						sendMessage(mHandlerThreadEvent, MSG_THREAD_PROGRESS, id);
					}
				}
				sendMessage(mHandlerThreadEvent, MSG_THREAD_DONE, id);
			}
		});
    }

	private void initData(Bundle bundle) throws InvalidParameterException, SQLException
    {
        if( bundle != null )
        {
        	mBitmapLock = bundle.getString(KEY_LOCK_OBJECT);
        	/*if( bundle.getBoolean(KEY_SHOW_DIALOG_PROGRESS) )
        	{
        		// TODO this isn't working: it seems onPause() dismissing the dialog does not take effect, so we never see this dialog
        		showDialog(DIALOG_SUBMITTING);
        		String msg = bundle.getString(KEY_DIALOG_MESSAGE);
        		if( msg != null )
        			mProgress.setMessage(msg);
        	}*/
        }
        
        mForceUploadImages = false;
        
        if( mBitmapLock == null )
        	mBitmapLock = Integer.toString(new Random().nextInt());
        
        mDbAdapter = new PostBotDbAdapter(this, this);
        mDbAdapter.open();
        
        FileManager.setCacheDir(getCacheDir());
    }

    private void initPost(Bundle bundle)
    {
        // check for post to edit in saved state
        long postId = bundle != null ? bundle.getLong(KEY_POST_ID) : 0;
    
        // check for post to edit in Intent
        if( postId == 0 )
        {
        	Bundle extras = getIntent().getExtras();
       		postId = extras != null ? extras.getLong(KEY_POST_ID) : 0;
        }
        
        // if there is a post to edit, retrieve it from the Db
        if( postId != 0 )
        {
        	mPost = mDbAdapter.getPost(postId);
        	// if we got a post, we need to get the associated Blog
        	if( mPost != null )
        	{
        		mBlog = mDbAdapter.getBlog(mPost.getBlogId());
        		if( mBlog == null )
        			// received a postId in the Bundle, but the Blog associated with the Post does not exist
        			throw new InvalidParameterException("Unable to load Blog with id '" + mPost.getBlogId() + "'.");
        	}
        	else
        		// received a postId in the Bundle, but no matching post in the db - error
        		throw new InvalidParameterException("Unable to load Post with id '" + postId + "' from the database.");
        }
        // if there is no post to edit, get a blog and create a new post for it 
        else
        {
        	mBlog = mDbAdapter.getDefaultBlog();
        	if( mBlog != null )
        	{
            	// if no post was given, it's a new post
            	mPost = new Post("", "", "");
            	mPost.setBlogId(mBlog.getBlogId());
            	mPost.setTags(mBlog.getDefaultTags());
        	}
        	else
        	{
        		// if there is no default blog, start the activity to create a blog
        		createBlog();
        		// can't proceed in this thread until subactivity completes, so throw exception to indicate initialization was unsuccessful
        		throw new InvalidParameterException("No blog configured; unable to proceed.");
        	}
        }
    }

	@SuppressWarnings("unchecked")
	private void initImages(Bundle bundle)
	{
		if( bundle != null )
		{
			// if bitmaps were passed in, use them
			mBitmaps = (Vector<Bitmap>) bundle.get(KEY_BITMAPS);
		}

		// start loading remaining images for this post
		loadImages();

    	// check for parcel we get when invoked because we implement ACTION_SEND
        Intent intent = getIntent();
        if( intent != null )
        {
	        String action = intent.getAction();
	        if( action != null && action.equals(Intent.ACTION_SEND) )
	        {
	        	String type = intent.getType();
		        if( type != null && type.equals(MIME_TYPE_IMAGE_JPEG) )
		        {
		        	Object o = intent.getExtras().getParcelable(Intent.EXTRA_STREAM);
		        	if( o != null && o instanceof Uri )
		        	{
		        		// clear out existing images (usually done by PostBot, but he got skipped this time)
		            	mDbAdapter.deleteImagesForPost(0);
		            	
		            	// load the image from the given Uri
		            	newImage((Uri) o); 
		        	}
		        }
	        }
        }
	}
	
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "==========================[ onSaveInstanceState ]==========================");
        
        // we need thready stuff, like the thread ref and the locking object
        outState.putString(KEY_LOCK_OBJECT, mBitmapLock);
        
        // also pass the images along
        outState.putSerializable(KEY_BITMAPS, mBitmaps);
        
        // pass dialog status so it can be recreated
        outState.putBoolean(KEY_SHOW_DIALOG_PROGRESS, new Boolean(mProgress != null ));
        outState.putString(KEY_DIALOG_MESSAGE, mProgressMessage);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "==========================[ onPause ]==========================");
		if( mProgress != null )
			dismissDialog(DIALOG_SUBMITTING);
        mRequestImageLoaderStop = true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "==========================[ onResume ]==========================");
    }
    
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
        Log.d(TAG, "==========================[ onDestroy ]==========================");
		mDbAdapter.close();
		
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch( id )
		{
		case DIALOG_NEW_CATEGORY:
            LayoutInflater factory = LayoutInflater.from(this);
            final View newCategoryView = factory.inflate(R.layout.new_category, null);
            return new AlertDialog.Builder(this)
            	.setTitle(R.string.new_category_label)
            	.setView(newCategoryView)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	EditText name = (EditText) newCategoryView.findViewById(R.id.new_category_name);
                    	EditText desc = (EditText) newCategoryView.findViewById(R.id.new_category_desc);
                    	
                    	Category category = new Category(null, name.getText().toString(), desc.getText().toString());
                    	category.setBlogId(mBlog.getBlogId());
                    	try
                    	{
                    		mBlog.addCategory(category);
                    		mDbAdapter.updateBlog(mBlog);
                    		refreshCategories();
                    		mCategories.setSelection(mCategories.getCount()-1);
                    		mCategories.setSelected(true);
                    		makeToast("Category added.", Toast.LENGTH_SHORT);
                    	}
                    	catch( XMLRPCException e )
                    	{
                    		makeToast("Unable to add category: " + e.getMessage(), Toast.LENGTH_LONG);
                    	}
                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
            	.create();
		case DIALOG_CONFIRM_FORGET_POST:
            return new AlertDialog.Builder(this)
            .setMessage("Forget this post?\n\nChanges here will be lost, but the post will not be deleted from the blog.")
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					FileManager.deleteThumbnailsForPost(mPost);
					// if there is a post, delete it
					if( mPost != null )
						mDbAdapter.deletePost(mPost.getId());
					// whether there is a post or not, go back to the main screen
       				makeToast("Post forgotten.", Toast.LENGTH_SHORT);
        			finish();
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		case DIALOG_REMOVE_IMAGE:
            return new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_remove_image)
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					if( mSelectedImage != null )
					{
						removeSelectedImage();
						refreshImages();
					}
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).setIcon(new BitmapDrawable(mSelectedBitmap)).create();
		case DIALOG_CONFIRM_REMOVE_IMAGES:
            return new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_remove_images)
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					Vector<Image> images = mDbAdapter.getImagesForPost(mPost.getId());
					for( Image img: images )
					{
						mSelectedImage = img;
						removeSelectedImage();
					}
					refreshImages();
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		case DIALOG_OOME:
	        // this is not currently called, because when OOME occurred, the Android crash notice didn't show, but showDialog() didn't get called either
            return new AlertDialog.Builder(this)
            .setMessage(R.string.err_oome)
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
				public void onClick(DialogInterface dialog, int which)
				{
					mDbAdapter.deleteImages();
					mSelectedImage = null;
					refreshImages();
				}
            })
            .setNegativeButton(
    		"Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which)
				{
				}
    		}).create();
		case DIALOG_SUBMITTING:
			mProgress = new ProgressDialog(this);
			mProgress.setIcon(R.drawable.postbot);
			mProgress.setTitle("Submitting your post...");
			mProgress.setMessage("Submitting...");
			mProgress.setIndeterminate(false);
			return mProgress;
		}
		return super.onCreateDialog(id);
	}

	private void removeSelectedImage()
	{
		FileManager.deleteThumbnail(mSelectedImage);
		mDbAdapter.deleteImage(mSelectedImage.getId());
		mSelectedImage = null;
		if( mSelectedBitmap != null )
		{
			mBitmaps.remove(mSelectedBitmap);
			mSelectedBitmap = null;
		}
		mImageViews.remove(mSelectedImageView);
		mSelectedImageView = null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = new MenuInflater(this);
		inflater.inflate(R.menu.editpost, menu);
		return true;
	}

    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		super.onMenuItemSelected(featureId, item);
		
		switch( item.getItemId() )
		{
		case R.id.editpost_add_category:
			showDialog(DIALOG_NEW_CATEGORY);
			break;
		case R.id.editpost_reload_categories:
			try
			{
				mBlog.retrieveCategories();
				mDbAdapter.updateBlog(mBlog);
				refreshCategories();
        		makeToast("Categories reloaded.", Toast.LENGTH_SHORT);
			}
			catch( XMLRPCException e )
			{
				Log.e(TAG, "An error occurred reloading categories: " + e.getMessage());
			}
			break;
		case R.id.editpost_forget_post:
			showDialog(DIALOG_CONFIRM_FORGET_POST);
			break;
		case R.id.editpost_remove_images:
			showDialog(DIALOG_CONFIRM_REMOVE_IMAGES);
			break;
		case R.id.editpost_add_image:
			getImageFromUser();
			break;
		}
		
		return true;
	}

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch( requestCode )
		{
		case ACTIVITY_GET_IMAGE:
			if( data != null )
			{
				newImage(data.getData());
				refreshImages();
			}
			break;
		}
		
    	setInputElementsEnabled(true);
    	refreshStatusMsg();
	}

    private void sendMessage(Handler handler, int what)
    {
    	Message msg = Message.obtain();
    	msg.what = what;
    	handler.sendMessage(msg);
    }
    
    private void sendMessage(Handler handler, int what, long l)
    {
    	Message msg = Message.obtain();
    	msg.what = what;
    	msg.obj = new Long(l);
    	handler.sendMessage(msg);
    }
    
    private void sendMessage(Handler handler, int what, Object obj)
    {
    	Message msg = Message.obtain();
    	msg.what = what;
    	msg.obj = obj;
    	handler.sendMessage(msg);
    }
    
    private boolean saveDraft()
    {
    	long postId = mPost.getId();
    	
    	// copy values from fields into the post and create or update the post in the db
		populatePost();
		mPost.setSubmitted(false);
		if( ! mDbAdapter.updatePost(mPost) && ! mDbAdapter.createPost(mPost) )
			return false;
		else
		{
			// get images using id of post before it was updated, because that's what the images will refer to in the db
			Vector<Image> images = mDbAdapter.getImagesForPost(postId);
			for( Image img: images )
			{
				img.setPostId(mPost.getId());
				mDbAdapter.updateImage(img);
			}
		}
		return true;
    }
    
    private void refreshStatusMsg()
    {
    	if( mBlog != null )
    		mBlogName.setText("Posting to " + mBlog.getBlogName());
    	else
    		mBlogName.setText("No blog configured; cannot post to blog.");
    }
    
	private void submit(final boolean publish)
	{
		saveDraft();

		showDialog(DIALOG_SUBMITTING);
		
		new Thread(new Runnable() {
			public void run()
			{
				Looper.prepare();
				boolean openedDb = false;
				try
				{
					// load image contents
					Vector<Image> images = mDbAdapter.getImagesForPost(mPost.getId());
					int progress = 0;
					for( Image img: images )
					{
						synchronized( mBitmapLock )
						{
							// upload the image if it hasn't been uploaded, or if user has asked for it
							if( mForceUploadImages || img.getUrl() == null )
							{
								// load the image and thumbnail contents from the drive
								try
								{
									img.loadFileContents(getContentResolver());
									// upload the image to the server
									sendMessage(mHandlerProgress, MSG_PROGRESS, "Uploading image " + (progress + 1));
									mBlog.uploadImage(img);

									// free up all possible memory and do a GC
									img.dropFileContents();

									// if orientation has changed, the db may have been closed
									if( ! mDbAdapter.isOpen() )
									{
										mDbAdapter.open();
										openedDb = true;
									}

									// the URL should be set for the image and its thumbnail, store it
									mDbAdapter.updateImage(img);
								}
								catch( OutOfMemoryError e )
								{
									e.printStackTrace();
									sendMessage(mHandlerThreadEvent, MSG_THREAD_FAILURE, "Unable to upload image " + (progress + 1) + ": It is too large.");
									Thread.sleep(ERR_DELAY);
								}
								catch( IOException e )
								{
									e.printStackTrace();
									sendMessage(mHandlerThreadEvent, MSG_THREAD_FAILURE, "Unable to upload image " + (progress + 1) + ": It is too large.");
									Thread.sleep(ERR_DELAY);
								}
							}
						}
						progress++;
					}
					
					// submit the post body itself
					sendMessage(mHandlerProgress, MSG_PROGRESS, "Uploading post...");
					mBlog.post(mPost, publish, images);

					// update the database (mPost should now be submitted and published)
					if( ! mDbAdapter.isOpen() )
					{
						mDbAdapter.open();
						openedDb = true;
					}
					mDbAdapter.updatePost(mPost);
					
					// spread the good news
					//bundle.putString(PostBot.KEY_ACTIVITY_RESULT, "Post published.");
					//setResult(RESULT_OK, intent);
					dismissDialog(DIALOG_SUBMITTING);
					sendMessage(mHandlerToast, MSG_OK, "Post published.");
				}
				catch( Exception e )
				{
					e.printStackTrace();
					dismissDialog(DIALOG_SUBMITTING);
					sendMessage(mHandlerToast, MSG_ERR, "Error while submitting: " + e.getMessage());
				}
				finally
				{
					if( openedDb )
						mDbAdapter.close();
				}
				
				mProgress = null;
				mProgressMessage = null;
				finish();

			}
		}).start();
	}

	private void refresh()
	{
		refreshCategories();
		refreshImages();
    	populateFields();
	}
	
	private void refreshCategories()
	{
		if( mBlog != null )
		{
			ArrayAdapter<Category> adapter = new ArrayAdapter<Category>(this, android.R.layout.simple_spinner_item, mBlog.getCategories());
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mCategories.setAdapter(adapter);
		}
	}
	
	private ImageView getThemedImageView()
	{
		ImageView iv = new ImageView(this);
		iv.setBackgroundResource(android.R.drawable.gallery_thumb);
		return iv;
	}
	
	private void loadImages()
	{
		// TODO select count from db instead of the cursor
		Cursor c = mDbAdapter.getImagesCursorForPost(mPost.getId());
		int count = c.getCount();
		c.close();

		// create all required ImageViews
    	mImageViews = new Vector<ImageView>(count);
    	for( int i=0; i<count; i++ )
    		mImageViews.add(getThemedImageView());
    	
		// ensure that we have a bitmaps collection as well as an images collection
		if( mBitmaps == null )
			mBitmaps = new Vector<Bitmap>(count);
		
		for( int i=0; i<count; i++ )
		{
			// if this bitmap is loaded, use it
			if( i < mBitmaps.size() )
				mImageViews.get(i).setImageBitmap(mBitmaps.get(i));
			// otherwise, use a filler
			else
				mImageViews.get(i).setImageBitmap(BitmapFactory.decodeResource(getResources(), android.R.drawable.gallery_thumb));
		}
		
		mImageLoader.start();
	}

	private void refreshImage(int position)
	{
		if( position < mImageViews.size() && position < mBitmaps.size() );
			mImageViews.get(position).setImageBitmap(mBitmaps.get(position));
	}
	
	private void refreshImages()
	{
		Cursor c = mDbAdapter.getImagesCursorForPost(mPost.getId());
		startManagingCursor(c);
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, 0, c, new String[]{}, new int[]{})
		{
			public View getView(int position, View convertView, ViewGroup parent)
			{
				// if there is an imageView for this position, use it
				if( position < mImageViews.size() )
					return mImageViews.get(position);
				return getThemedImageView();
			}
		};
		mImageGallery.setAdapter(adapter);
	}
	
    private void populateFields()
    {
    	mTitle.setText(mPost.getTitle());
    	mBody.setText(mPost.getBody());
    	int selection = mBlog.getCategoryIndex(mPost.getCategoryId());
    	mCategories.setSelection(selection);
    	mCategories.setSelected(true);
        //mTags.setText(mPost.getTags());
    }
    
    private void populatePost()
    {
		Category category = (Category) mCategories.getSelectedItem();
		if( category != null )
			mPost.setCategoryId(category.getId());
		mPost.setTitle(mTitle.getText().toString());
		mPost.setBody(mBody.getText().toString());
		//mPost.setTags(mTags.getText().toString());
    }
    
    private void setInputElementsEnabled(boolean enabled)
    {
    	mTitle.setEnabled(enabled);
    	mCategories.setEnabled(enabled);
    	mBody.setEnabled(enabled);
    }
    
    public void getImageFromUser()
    {
    	Intent getImage = new Intent(Intent.ACTION_GET_CONTENT);
		getImage.addCategory(Intent.CATEGORY_OPENABLE);
		getImage.setType(MIME_TYPE_IMAGE_JPEG);
		startActivityForResult(getImage, ACTIVITY_GET_IMAGE);
    }
    
    public void createBlog()
    {
    	Intent i = new Intent(this, EditBlog.class);
    	startActivityForResult(i, EditBlog.ACTIVITY_EDIT);
    }

    private boolean newImage(Uri data)
    {
		Image newImage = new Image(data.toString(), null);
		if( mPost != null )
			newImage.setPostId(mPost.getId());
    	if( mDbAdapter.createImage(newImage) )
    	{
    		try
    		{
    	    	synchronized( mBitmapLock )
    	    	{
	    			Bitmap bm = getImageGalleryThumbnail(newImage);
	    			ImageView iv = getThemedImageView();
	    			iv.setImageBitmap(bm);
	    			mBitmaps.add(bm);
	    			mImageViews.add(iv);
	    			return true;
    	    	}
    		}
    		catch( IOException e )
    		{
    			Log.e(TAG, "Unable to load image contents for image " + newImage + "!");
    			makeToast("Unable to load image: it is too large.", Toast.LENGTH_LONG);
    			mDbAdapter.deleteImage(newImage.getId());
    			return false;
    		}
    	}
    	Log.e(TAG, "Unable to create image " + newImage + " in the database!");
    	return false;		
    }
    
    private synchronized Bitmap getImageGalleryThumbnail(Image img) throws IOException
    {
    	synchronized(mBitmapLock)
    	{
    		Bitmap bm = null;
    		// try to get a cached image
    		bm = FileManager.getImageThumbnail(img);
    		// if not, generate one
    		if( bm == null )
    		{
	    		if( img.getContent() == null )
	    			img.loadFileContents(getContentResolver());
	    		bm = img.getGalleryThumbnail();
	    		img.dropFileContents();
	    		System.gc();
	    		FileManager.putImageThumbnail(img, bm);
    		}
    		return bm;
    	}
    }
    
    public void makeToast(String message, int duration)
    {
    	Toast.makeText(this, message, duration).show();
    }
}

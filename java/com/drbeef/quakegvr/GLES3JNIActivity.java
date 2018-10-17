
package com.drbeef.quakegvr;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;

import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;


@SuppressLint("SdCardPath") public class GLES3JNIActivity extends Activity implements SurfaceHolder.Callback, JNICallback
{
	// Load the gles3jni library right away to make sure JNI_OnLoad() gets called as the very first thing.
	static
	{
		System.loadLibrary( "quakegvr" );
	}

	PositionalTracking posTracking;
	
	//Save the game pad type once known:
	// 1 - Generic BT gamepad
	// 2 - Samsung gamepad that uses different axes for right stick
	int gamepadType = 0;

	private static final String TAG = "QuakeGVR";

	private SurfaceView mView;
	private SurfaceHolder mSurfaceHolder;
	private long mNativeHandle;

	private final boolean m_asynchronousTracking = false;
	
	public static QGVRAudioCallback mAudio;

	@Override protected void onCreate( Bundle icicle )
	{
		Log.v( TAG, "----------------------------------------------------------------" );
		Log.v( TAG, "GLES3JNIActivity::onCreate()" );
		super.onCreate( icicle );

		posTracking = new PositionalTracking(this, m_asynchronousTracking);
		posTracking.initialise(this);

		mView = new SurfaceView( this );
		setContentView( mView );
		mView.getHolder().addCallback( this );

		// Force the screen to stay on, rather than letting it dim and shut off
		// while the user is watching a movie.
		getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

		// Force screen brightness to stay at maximum
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.screenBrightness = 1.0f;
		getWindow().setAttributes( params );
		
		//This will copy the shareware version of quake if user doesn't have anything installed
		copy_asset("pak0.pak");
		copy_asset("config.cfg");
		
		if (mAudio==null)
		{
			mAudio = new QGVRAudioCallback();
		}
		
		GLES3JNILib.setCallbackObjects(mAudio, this);

		mNativeHandle = GLES3JNILib.onCreate( this );
	}
	
	public void copy_asset(String name) {
		File f = new File("/sdcard/QGVR/id1/" + name); 
		if (!f.exists() ||
			//If file was somehow corrupted, copy the back-up
			f.length() < 500) {
			
			//Ensure we have an appropriate folder
			new File("/sdcard/QGVR/id1").mkdirs();
			copy_asset(name, "/sdcard/QGVR/id1/" + name);
		}
	}

	public void copy_asset(String name_in, String name_out) {
		AssetManager assets = this.getAssets();

		try {
			InputStream in = assets.open(name_in);
			OutputStream out = new FileOutputStream(name_out);

			copy_stream(in, out);

			out.close();
			in.close();

		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	public static void copy_stream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			int count = in.read(buf);
			if (count <= 0)
				break;
			out.write(buf, 0, count);
		}
	}


	@Override protected void onStart()
	{
		Log.v( TAG, "GLES3JNIActivity::onStart()" );
		super.onStart();

		//Read these from a file and pass through
		String commandLineParams = new String("quake");

		//See if user is trying to use command line params
		if(new File("/sdcard/QGVR/commandline.txt").exists())
		{
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader("/sdcard/QGVR/commandline.txt"));
				String s;
				StringBuilder sb=new StringBuilder(0);
				while ((s=br.readLine())!=null)
					sb.append(s + " ");
				br.close();

				commandLineParams = new String(sb.toString());
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		GLES3JNILib.onStart( mNativeHandle, commandLineParams );
	}

	@Override protected void onResume()
	{
		Log.v( TAG, "GLES3JNIActivity::onResume()" );
		super.onResume();
		if (posTracking.getStatus() == PositionalTracking.Status.PAUSED) {
			posTracking.setStatus(PositionalTracking.Status.RESUME);
		}

		GLES3JNILib.onResume( mNativeHandle );
	}

	@Override protected void onPause()
	{
		Log.v( TAG, "GLES3JNIActivity::onPause()" );
		GLES3JNILib.onPause( mNativeHandle );
		posTracking.setStatus(PositionalTracking.Status.PAUSE);
		super.onPause();
	}

	@Override protected void onStop()
	{
		Log.v( TAG, "GLES3JNIActivity::onStop()" );
		GLES3JNILib.onStop( mNativeHandle );
		super.onStop();
	}

	@Override protected void onDestroy()
	{
		Log.v( TAG, "GLES3JNIActivity::onDestroy()" );

		if ( mSurfaceHolder != null )
		{
			GLES3JNILib.onSurfaceDestroyed( mNativeHandle );
		}

		GLES3JNILib.onDestroy( mNativeHandle );

		super.onDestroy();
		mNativeHandle = 0;
		posTracking = null;
	}

	@Override public void surfaceCreated( SurfaceHolder holder )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceCreated()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceCreated( mNativeHandle, holder.getSurface() );
			mSurfaceHolder = holder;
		}
	}

	@Override public void surfaceChanged( SurfaceHolder holder, int format, int width, int height )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceChanged()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceChanged( mNativeHandle, holder.getSurface() );
			mSurfaceHolder = holder;
		}
	}
	
	@Override public void surfaceDestroyed( SurfaceHolder holder )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceDestroyed()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceDestroyed( mNativeHandle );
			mSurfaceHolder = null;
		}
	}

	@Override public void updatePosition()
	{
		float[] positionInWorld = new float[3];
		posTracking.getPosition(positionInWorld);

		if ( mNativeHandle != 0 ) {
			GLES3JNILib.setWorldPosition(mNativeHandle, positionInWorld[0], positionInWorld[1], positionInWorld[2]);
		}
	}


	@Override public void resetPosition() {
		posTracking.setStatus(PositionalTracking.Status.RESET);
	}

	@Override
	public boolean isPositionTrackingSupported()
	{
		return posTracking.isPositionTrackingSupported();
	}

	@Override
	public void shutdown()
	{
		posTracking.setStatus(PositionalTracking.Status.DESTROY);
		while (posTracking.getStatus() != PositionalTracking.Status.STOPPED) {
			try {
				if (!m_asynchronousTracking)
				{
					//Have to execute run here in sync mode
					posTracking.run();
				}
				Thread.sleep(500);
			} catch (Exception e) {
				//Whatevs
			}
		}
	}

	@Override
	public int getCameraTexture()
	{
		return posTracking.getCameraTexture();
	}

	public int getCharacter(int keyCode, KeyEvent event)
	{
		if (keyCode==KeyEvent.KEYCODE_DEL) return '\b';
		return event.getUnicodeChar();
	}

	public static final int K_ESCAPE = 27;

	@Override public boolean dispatchKeyEvent( KeyEvent event )
	{
		if ( mNativeHandle != 0 )
		{
			int keyCode = event.getKeyCode();
			int action = event.getAction();
			int character = 0;

			if ( action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP )
			{
				return super.dispatchKeyEvent( event );
			}

			if ( action == KeyEvent.ACTION_UP )
			{
				Log.v( TAG, "GLES3JNIActivity::dispatchKeyEvent( " + keyCode + ", " + action + " )" );
			}

			if (keyCode == KeyEvent.KEYCODE_BACK ||
					keyCode == KeyEvent.KEYCODE_BUTTON_B)
			{
				return true;
			}

			//Following buttons must not be handled here
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
					keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
					)
				return false;

			if (keyCode == KeyEvent.KEYCODE_BUTTON_START)
			{
				//Pass through
				GLES3JNILib.onKeyEvent( mNativeHandle, K_ESCAPE, action, character );
			}

			//Convert to Quake keys
			character = getCharacter(keyCode, event);
			int qKeyCode = convertKeyCode(keyCode, event);

			//Don't hijack all keys (volume etc)
			if (qKeyCode != -1)
				keyCode = qKeyCode;

			GLES3JNILib.onKeyEvent( mNativeHandle, keyCode, action, character );
		}
		return true;
	}

	public static int convertKeyCode(int keyCode, KeyEvent event)
	{
		int uchar = event.getUnicodeChar(0);
		if((uchar < 127)&&(uchar!=0))
			return uchar;
		return keyCode%95+32;//Magic
	}

}

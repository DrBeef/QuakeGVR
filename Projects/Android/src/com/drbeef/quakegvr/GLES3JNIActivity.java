
package com.drbeef.quakegvr;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;


@SuppressLint("SdCardPath") public class GLES3JNIActivity extends Activity implements SurfaceHolder.Callback
{
	// Load the gles3jni library right away to make sure JNI_OnLoad() gets called as the very first thing.
	static
	{
		System.loadLibrary( "quakegvr" );
	}
	
	//Save the game pad type once known:
	// 1 - Generic BT gamepad
	// 2 - Samsung gamepad that uses different axes for right stick
	int gamepadType = 0;

	private static final String TAG = "QuakeGVR";

	private SurfaceView mView;
	private SurfaceHolder mSurfaceHolder;
	private long mNativeHandle;
	
	public static QGVRAudioCallback mAudio;

	@Override protected void onCreate( Bundle icicle )
	{
		Log.v( TAG, "----------------------------------------------------------------" );
		Log.v( TAG, "GLES3JNIActivity::onCreate()" );
		super.onCreate( icicle );

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
		
		GLES3JNILib.setCallbackObject(mAudio);

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
		GLES3JNILib.onResume( mNativeHandle );
	}

	@Override protected void onPause()
	{
		Log.v( TAG, "GLES3JNIActivity::onPause()" );
		GLES3JNILib.onPause( mNativeHandle );
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

	public int getCharacter(int keyCode, KeyEvent event)
	{		
		if (keyCode==KeyEvent.KEYCODE_DEL) return '\b';		
		return event.getUnicodeChar();		
	}
	
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
			
			//Following buttons must not be handled here
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
				keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
				keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL
				)
				return false;
			
			if (keyCode == KeyEvent.KEYCODE_BACK)
			{
				//Pass through
				GLES3JNILib.onKeyEvent( mNativeHandle, keyCode, action, character );
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

	private static float getCenteredAxis(MotionEvent event,
            int axis) {
        final InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
        if (range != null) {
            final float flat = range.getFlat();
            final float value = event.getAxisValue(axis);
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }	
	
	int lTrigAction = KeyEvent.ACTION_UP;
	int rTrigAction = KeyEvent.ACTION_UP;
	
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		int source = event.getSource();
		int action = event.getAction();			
		if ((source==InputDevice.SOURCE_JOYSTICK)||(event.getSource()==InputDevice.SOURCE_GAMEPAD))
		{
			if (event.getAction() == MotionEvent.ACTION_MOVE)
			{
				float x = getCenteredAxis(event, MotionEvent.AXIS_X);
				float y = -getCenteredAxis(event, MotionEvent.AXIS_Y);			
				GLES3JNILib.onTouchEvent( mNativeHandle, source, action, x, y );
	
		        float z = getCenteredAxis(event, MotionEvent.AXIS_Z);
		        float rz = getCenteredAxis(event, MotionEvent.AXIS_RZ);	
				//For the samsung game pad (uses different axes for the second stick)
				float rx = getCenteredAxis(event, MotionEvent.AXIS_RX);
				float ry = getCenteredAxis(event, MotionEvent.AXIS_RY);	      	        
		        	
				//let's figure it out
				if (gamepadType == 0)
				{
			        if (z != 0.0f || rz != 0.0f)
			        	gamepadType = 1;
			        else if (rx != 0.0f || ry != 0.0f)
			        	gamepadType = 2;
				}
	
				switch (gamepadType)
				{
				case 0:
					break;
				case 1:
		        	GLES3JNILib.onMotionEvent( mNativeHandle, source, action, z, rz );
		        	break;
				case 2:
					GLES3JNILib.onMotionEvent( mNativeHandle, source, action, rx, ry );
					break;
				}

				//Fire weapon using shoulder trigger
				float axisRTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
						event.getAxisValue(MotionEvent.AXIS_GAS));
				int newRTrig = axisRTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
				if (rTrigAction != newRTrig)
				{
					GLES3JNILib.onKeyEvent( mNativeHandle, K_MOUSE1, newRTrig, 0);
					rTrigAction = newRTrig;
				}
				
				//Run using L shoulder
				float axisLTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), 
						event.getAxisValue(MotionEvent.AXIS_BRAKE));
				int newLTrig = axisLTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
				if (lTrigAction != newLTrig)
				{
					GLES3JNILib.onKeyEvent( mNativeHandle, K_SHIFT, newLTrig, 0);
					lTrigAction = newLTrig;
				}
			}
		}
	    return false;
	}
	
	private float max(float axisValue, float axisValue2) {
		return (axisValue > axisValue2) ? axisValue : axisValue2;
	}

	@Override public boolean dispatchTouchEvent( MotionEvent event )
	{
		if ( mNativeHandle != 0 )
		{
			int source = event.getSource();
			int action = event.getAction();			
			float x = event.getRawX();
			float y = event.getRawY();
			if ( action == MotionEvent.ACTION_UP )
			{
				Log.v( TAG, "GLES3JNIActivity::dispatchTouchEvent( " + action + ", " + x + ", " + y + " )" );
			}
			GLES3JNILib.onTouchEvent( mNativeHandle, source, action, x, y );
		}
		return true;
	}
	
	public static final int K_TAB = 9;
	public static final int K_ENTER = 13;
	public static final int K_ESCAPE = 27;
	public static final int K_SPACE	= 32;
	public static final int K_BACKSPACE	= 127;
	public static final int K_UPARROW = 128;
	public static final int K_DOWNARROW = 129;
	public static final int K_LEFTARROW = 130;
	public static final int K_RIGHTARROW = 131;	
	public static final int K_ALT = 132;
	public static final int K_CTRL = 133;
	public static final int K_SHIFT = 134;
	public static final int K_F1 = 135;
	public static final int K_F2 = 136;
	public static final int K_F3 = 137;
	public static final int K_F4 = 138;
	public static final int K_F5 = 139;
	public static final int K_F6 = 140;
	public static final int K_F7 = 141;
	public static final int K_F8 = 142;
	public static final int K_F9 = 143;
	public static final int K_F10 = 144;
	public static final int K_F11 = 145;
	public static final int K_F12 = 146;
	public static final int K_INS = 147;
	public static final int K_DEL = 148;
	public static final int K_PGDN = 149;
	public static final int K_PGUP = 150;
	public static final int K_HOME = 151;
	public static final int K_END = 152;
	public static final int K_PAUSE = 153;
	public static final int K_NUMLOCK = 154;
	public static final int K_CAPSLOCK = 155;
	public static final int K_SCROLLOCK = 156;
	public static final int K_MOUSE1 = 512;
	public static final int K_MOUSE2 = 513;
	public static final int K_MOUSE3 = 514;
	public static final int K_MWHEELUP = 515;
	public static final int K_MWHEELDOWN = 516;
	public static final int K_MOUSE4 = 517;
	public static final int K_MOUSE5 = 518;		
	
	public static int convertKeyCode(int keyCode, KeyEvent event)
	{				
		switch(keyCode)
		{			
			case KeyEvent.KEYCODE_FOCUS:
				return K_F1;
			case KeyEvent.KEYCODE_DPAD_UP:
				return K_UPARROW;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				return K_DOWNARROW;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				return 'a';
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				return 'd';
			case KeyEvent.KEYCODE_DPAD_CENTER:
				return K_CTRL;
			case KeyEvent.KEYCODE_ENTER:
				return K_ENTER;		
//			case KeyEvent.KEYCODE_BACK:
//				return K_ESCAPE;
			case KeyEvent.KEYCODE_DEL:
				return K_BACKSPACE;
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				return K_ALT;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
				return K_SHIFT;
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
				return K_CTRL;
			case KeyEvent.KEYCODE_INSERT:
				return K_INS;
			case 122:
				return K_HOME;
			case KeyEvent.KEYCODE_FORWARD_DEL:
				return K_DEL;
			case 123:
				return K_END;
			case KeyEvent.KEYCODE_ESCAPE:
				return K_ESCAPE;
			case KeyEvent.KEYCODE_TAB:
				return K_TAB;
			case KeyEvent.KEYCODE_F1:
				return K_F1;
			case KeyEvent.KEYCODE_F2:
				return K_F2;
			case KeyEvent.KEYCODE_F3:
				return K_F3;
			case KeyEvent.KEYCODE_F4:
				return K_F4;
			case KeyEvent.KEYCODE_F5:
				return K_F5;
			case KeyEvent.KEYCODE_F6:
				return K_F6;
			case KeyEvent.KEYCODE_F7:
				return K_F7;
			case KeyEvent.KEYCODE_F8:
				return K_F8;
			case KeyEvent.KEYCODE_F9:
				return K_F9;
			case KeyEvent.KEYCODE_F10:
				return K_F10;
			case KeyEvent.KEYCODE_F11:
				return K_F11;
			case KeyEvent.KEYCODE_F12:
				return K_F12;
			case KeyEvent.KEYCODE_CAPS_LOCK:
				return K_CAPSLOCK;
			case KeyEvent.KEYCODE_PAGE_DOWN:
				return K_PGDN;
			case KeyEvent.KEYCODE_PAGE_UP:
				return K_PGUP;		
			case KeyEvent.KEYCODE_BUTTON_A:
				return K_ENTER;
			case KeyEvent.KEYCODE_BUTTON_B:
				return 'r';
			case KeyEvent.KEYCODE_BUTTON_X:
				return '#'; //prev weapon, set in the config.txt as impulse 12
			case KeyEvent.KEYCODE_BUTTON_Y:
				return '/';//Next weapon, set in the config.txt as impulse 10
			//These buttons are not so popular
			case KeyEvent.KEYCODE_BUTTON_C:
				return 'a';//That's why here is a, nobody cares.											
			case KeyEvent.KEYCODE_BUTTON_Z:
				return 'z';
			//--------------------------------
			case KeyEvent.KEYCODE_BUTTON_START:
				return K_ESCAPE;
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				return K_ENTER;
			case KeyEvent.KEYCODE_MENU:
				return K_ESCAPE;
				
			//Both shoulder buttons will "fire"
			case KeyEvent.KEYCODE_BUTTON_R1:
			case KeyEvent.KEYCODE_BUTTON_R2:
				return K_MOUSE1;
				
			//enables "run"
			case KeyEvent.KEYCODE_BUTTON_L1:
			case KeyEvent.KEYCODE_BUTTON_L2:
				return K_SHIFT;
			case KeyEvent.KEYCODE_BUTTON_THUMBL:
				return -1;
		}	
		int uchar = event.getUnicodeChar(0);
		if((uchar < 127)&&(uchar!=0))
			return uchar;
		return keyCode%95+32;//Magic
	}

}

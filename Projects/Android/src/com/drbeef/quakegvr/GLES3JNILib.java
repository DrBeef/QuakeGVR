
package com.drbeef.quakegvr;

import android.app.Activity;
import android.view.Surface;

// Wrapper for native library

public class GLES3JNILib
{
	// Activity lifecycle
	public static native long onCreate( Activity obj );
	public static native void onStart( long handle, String commandLineParams );
	public static native void onResume( long handle );
	public static native void onPause( long handle );
	public static native void onStop( long handle );
	public static native void onDestroy( long handle );

	// Surface lifecycle
	public static native void onSurfaceCreated( long handle, Surface s );
	public static native void onSurfaceChanged( long handle, Surface s );
	public static native void onSurfaceDestroyed( long handle );

	// Input
	public static native void onKeyEvent( long handle, int keyCode, int action, int character );
	public static native void onTouchEvent( long handle, int source, int action, float x, float y );
	public static native void onMotionEvent( long handle, int source, int action, float x, float y );
	
	//Q3E stuff (renamed to make things a bit easier)
	public static native void requestAudioData();
	public static native void setCallbackObject(Object obj);	
}

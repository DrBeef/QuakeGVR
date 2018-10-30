
package com.drbeef.quakegvr;

import android.app.Activity;
import android.view.Surface;

// Wrapper for native library

public class GLES3JNILib
{
	// Activity lifecycle
	public static native long onCreate( Activity obj, String commandLineParams );
	public static native void onStart( long handle );
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

	//Positional Tracking from ArCore
	public static native void setWorldPosition( long handle, float x, float y, float z );

	public static native void requestAudioData();
	public static native void setCallbackObjects(Object obj1, Object obj2);
}

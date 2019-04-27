package com.drbeef.quakegvr;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Pose;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import android.app.Activity;
import android.opengl.GLES11Ext;

import java.util.Deque;
import java.util.LinkedList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_WIDTH;
import static com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED;

class Point3D {

	public Point3D() {
		x = 0.0f;
		y = 0.0f;
		z = 0.0f;
	}

	public Point3D(Point3D p) {
		x = p.x;
		y = p.y;
		z = p.z;
	}

	//Position
	public float x;
	public float y;
	public float z;
}


/**
 * This class wraps the Google ArCore functionality into a utility class that literally just provides the position in world space
 *
 * It creates its own EGL Context so that it can handle the GPU processing for ArCore on a separate thread to the renderer
 * otherwise the max FPS would be 30 frames a second (which is the limit of the S8 camera when in AR mode)
 */

public class PositionalTracking  implements Runnable {
	private static final String TAG = PositionalTracking.class.getSimpleName();

	public PositionalTracking(Context context, boolean async) {
		m_async = async;
		this.context = context;
	}

	Context context;
	private boolean m_async;

	Thread myThread = null;


	Activity activity;

	private boolean installRequested = false;

	private Session session;

	private Deque<Point3D> pointDeque = new LinkedList<Point3D>();

	private float[] worldOrigin = {0.0f, 0.0f, 0.0f};; // This can change, if it the camera position deviates suddenly from the previous position, reset the origin to where we are now
	private Point3D lastPoint = null;

	public synchronized void getPosition(float[] position)
	{
		if (!m_async)
		{
			if (pointDeque.isEmpty()) {
				updatePosition(false);
			}
		}

		if (pointDeque.isEmpty())
		{
			Point3D point = new Point3D();
			position[0] = point.x;
			position[1] = point.y;
			position[2] = point.z;
		}
		else
		{
			Point3D point = pointDeque.removeLast();
			position[0] = point.x;
			position[1] = point.y;
			position[2] = point.z;
		}
	}

	private synchronized void setPosition(float[] translation)
	{
		Point3D position = new Point3D();
		position.x = (translation[0] - worldOrigin[0]) * 1.0f;
		position.y = (translation[1] - worldOrigin[1]) * 1.0f;
		position.z = (translation[2] - worldOrigin[2]) * 1.0f;

		if (lastPoint == null)
		{
			pointDeque.addLast(position);
			pointDeque.addLast(position);
		}
		else {

			pointDeque.addLast(position);

			Point3D midPosition = new Point3D();
			midPosition.x = (position.x + lastPoint.x) / 2.0f;
			midPosition.y = (position.y + lastPoint.y) / 2.0f;
			midPosition.z = (position.z + lastPoint.z) / 2.0f;

			pointDeque.addLast(midPosition);

			while (pointDeque.size() > 2) {
				pointDeque.removeFirst();
			}
		}

		lastPoint = position;
	}

	private int cameraTextureId = -1;

	SurfaceTexture surfaceTexture = null;

	private boolean isArCoreSupported = false;

	public synchronized void setArCoreSupported() {
		isArCoreSupported = true;
	}

	public synchronized boolean getArCoreSupported() {
		return isArCoreSupported;
	}

	private EGL10 mEgl;
	private EGLConfig[] maEGLconfigs;
	private EGLDisplay mEglDisplay = null;
	private EGLContext mEglContext = null;
	private EGLSurface mEglSurface = null;

	public enum Status {
		CHECKARCORESUPPORT,
		INITIALISE,
		RESET,
		RUNNING,
		PAUSE,
		PAUSED,
		RESUME,
		DESTROY,
		STOP, // Only used if ArCore not supported and we need to drop out
		STOPPED
	}

	private Status status = Status.CHECKARCORESUPPORT;

	public synchronized void setStatus(Status status) {
		//Can't change from initialise state externally
		if (this.status.ordinal() > Status.INITIALISE.ordinal() &&
				this.status != Status.STOPPED)
		{
			this.status = status;

			if (!m_async)
			{
				//If we aren't asynchronous we need to execute a run to perform the status change
				run();
			}
		}
	}
	public synchronized Status getStatus() {
		return this.status;
	}

	public  boolean isPositionTrackingSupported()
	{
		return getArCoreSupported();
	}

	public int getCameraTexture()
	{
		return cameraTextureId;
	}

	public void run() {

		if (m_async) {
			//Set up EGL Context for this thread
			InitializeEGL();
		}

		do
		{
			Status status = getStatus();

			if (status == Status.CHECKARCORESUPPORT)
			{
				ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(context);
				if (availability.isTransient()) {
					try {
						Thread.sleep(400);
					}
					catch (Exception e)	{
					}
				} else if (availability == SUPPORTED_INSTALLED) {
					setArCoreSupported();
					this.status = Status.INITIALISE;
				} else { // Unsupported or unknown.
					this.status = Status.STOP;
				}
			}
			else if (status == Status.INITIALISE)
			{
				if (internalInit()) {
					//Make an exception, we can change this state from initialise
					this.status = Status.RUNNING;
					continue;
				}

				//no point continuing, can't use AR
				this.status = Status.STOPPED;
				break;
			}
			else if (status == Status.RUNNING) {
				updatePosition(false);
			}
			else if (status == Status.RESET) {
				updatePosition(true);

				//Restore us back to the running state
				this.status = Status.RUNNING;
			}
			else if (status == Status.DESTROY)
			{
				if (session != null) {
					session.pause();
					session = null;
					this.status = Status.STOP;
				}
			}
			else if (status == Status.PAUSE)
			{
				if (session != null) {
					session.pause();
				}
				setStatus(Status.PAUSED);
			}
			else if (status == Status.RESUME)
			{
				try {
					if (session != null) {
						session.resume();
					}
					setStatus(Status.RUNNING);
				} catch (CameraNotAvailableException e) {
					Log.e(TAG, "Camera not available. Please restart the app");
					session = null;
					return;
				}
			}
			else if (status == Status.STOP)
			{
				if (session != null) {
					if (m_async) {
						DeleteSurfaceEGL(mEglSurface);
					}
				}
				this.status = Status.STOPPED;
				break;
			}
		}
		while (m_async);

		return;
	}

	private void InitializeEGL()
	{
		mEgl = (EGL10)EGLContext.getEGL();

		mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

		if (mEglDisplay == EGL10.EGL_NO_DISPLAY)
			throw new RuntimeException("Error: eglGetDisplay() Failed " + GLUtils.getEGLErrorString(mEgl.eglGetError()));

		int[] version = new int[2];

		if (!mEgl.eglInitialize(mEglDisplay, version))
			throw new RuntimeException("Error: eglInitialize() Failed " + GLUtils.getEGLErrorString(mEgl.eglGetError()));

		maEGLconfigs = new EGLConfig[1];

		int[] configsCount = new int[1];
		int[] configSpec = new int[]
		{
				EGL10.EGL_RENDERABLE_TYPE,
				EGL_OPENGL_ES2_BIT,
				EGL10.EGL_RED_SIZE, 8,
				EGL10.EGL_GREEN_SIZE, 8,
				EGL10.EGL_BLUE_SIZE, 8,
				EGL10.EGL_ALPHA_SIZE, 8,
				EGL10.EGL_DEPTH_SIZE, 0,
				EGL10.EGL_STENCIL_SIZE, 0,
				EGL10.EGL_NONE
		};
		if ((!mEgl.eglChooseConfig(mEglDisplay, configSpec, maEGLconfigs, 1, configsCount)) || (configsCount[0] == 0))
			throw new IllegalArgumentException("Error: eglChooseConfig() Failed " + GLUtils.getEGLErrorString(mEgl.eglGetError()));

		if (maEGLconfigs[0] == null)
			throw new RuntimeException("Error: eglConfig() not Initialized");

		int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE };

		mEglContext = mEgl.eglCreateContext(mEglDisplay, maEGLconfigs[0], EGL10.EGL_NO_CONTEXT, attrib_list);

		int[] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		surfaceTexture = new SurfaceTexture(textures[0]);

		CreateSurfaceEGL(surfaceTexture, EGL_WIDTH, EGL_HEIGHT);
	}

	private void CreateSurfaceEGL(SurfaceTexture surfaceTexture, int width, int height)
	{
		surfaceTexture.setDefaultBufferSize(width, height);

		mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, maEGLconfigs[0], surfaceTexture, null);

		if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE)
		{
			int error = mEgl.eglGetError();

			if (error == EGL10.EGL_BAD_NATIVE_WINDOW)
			{
				Log.e(TAG, "Error: createWindowSurface() Returned EGL_BAD_NATIVE_WINDOW.");
				return;
			}
			throw new RuntimeException("Error: createWindowSurface() Failed " + GLUtils.getEGLErrorString(error));
		}
		if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext))
			throw new RuntimeException("Error: eglMakeCurrent() Failed " + GLUtils.getEGLErrorString(mEgl.eglGetError()));

		int[] widthResult = new int[1];
		int[] heightResult = new int[1];

		mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_WIDTH, widthResult);
		mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_HEIGHT, heightResult);
		Log.i(TAG, "EGL Surface Dimensions:" + widthResult[0] + " " + heightResult[0]);
	}

	private void DeleteSurfaceEGL(EGLSurface eglSurface)
	{
		if (eglSurface != EGL10.EGL_NO_SURFACE)
			mEgl.eglDestroySurface(mEglDisplay, eglSurface);
	}

	public void initialise(Activity activity) {
		this.activity = activity;

		if (m_async) {
			myThread = new Thread(this);
			myThread.start();
		}
		else
		{
			run();

			if (isPositionTrackingSupported())
			{
				//Run again to initialise
				run();
			}
		}
	}

	private boolean internalInit()
	{
		if (session == null) {
			Exception exception = null;
			String message = null;
			try {
				// Create the session.
				session = new Session(activity);

			} catch (UnavailableArcoreNotInstalledException e) {
				message = "Please install ARCore";
				exception = e;
			} catch (UnavailableApkTooOldException e) {
				message = "Please update ARCore";
				exception = e;
			} catch (UnavailableSdkTooOldException e) {
				message = "Please update this app";
				exception = e;
			} catch (Exception e) {
				message = "Failed to create AR session";
				exception = e;
			}

			if (message != null) {
				Log.e(TAG, "Exception creating ArCore session", exception);
				return false;
			}
		}

		try {

			if (!m_async) {
				Config config = new Config(session);
				config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
				session.configure(config);
			}

			session.resume();
		} catch (CameraNotAvailableException e) {
			Log.e(TAG, "Camera not available. Please restart the app");
			session = null;
			return false;
		}

		createSurface();

		return true;
	}

	public void createSurface() {
		if (session != null) {
			int[] textures = new int[1];
			GLES20.glGenTextures(1, textures, 0);
			cameraTextureId = textures[0];
			int textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
			GLES20.glBindTexture(textureTarget, cameraTextureId);
			GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
		}
	}

	public void updatePosition(boolean reset) {

		if (session == null || cameraTextureId == -1) {
			return;
		}

		try {
			session.setCameraTextureName(cameraTextureId);

			Frame frame = session.update();
			Camera camera = frame.getCamera();

			// If not tracking don't do anything else
			if (camera.getTrackingState() != TrackingState.TRACKING) {
				return;
			}

			Pose pose = camera.getPose();

			float[] translation = new float[3];
			pose.getTranslation(translation, 0);

			Log.d(TAG, "Pose: " + pose.toString());

			if (reset)
			{
				//Reset origin
				worldOrigin = translation.clone();

/*				yawAdjustment -= (resetYaw / 180.0f) * (float)Math.PI;

				if (yawAdjustment > (float)Math.PI)
				{
					yawAdjustment -= (float)(Math.PI * 2);
				}
				if (yawAdjustment < -(float)Math.PI)
				{
					yawAdjustment += (float)(Math.PI * 2);
				}*/
			}
			else
			{
				//Should be good to use these values
				setPosition(translation);
			}
		} catch (Throwable t) {
			// Avoid crashing the application due to unhandled exceptions.
			Log.e(TAG, "Exception on the OpenGL thread", t);
		}
	}

}



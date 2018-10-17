package com.drbeef.quakegvr;

public interface JNICallback {
	void updatePosition();
	void resetPosition();
	boolean isPositionTrackingSupported();
	void shutdown();
	int getCameraTexture();
}

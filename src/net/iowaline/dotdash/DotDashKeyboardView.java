package net.iowaline.dotdash;

import java.util.HashSet;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

@SuppressLint("ClickableViewAccessibility")
public class DotDashKeyboardView extends KeyboardView {

	private String TAG = this.getClass().getSimpleName();
	private DotDashIMEService service;
	private Dialog cheatsheetDialog;
	private TableLayout cheatsheet1;
	private TableLayout cheatsheet2;
	private int mSwipeThreshold;
	private GestureDetector gestureDetector;
	
	private Set<Keyboard.Key> pressedKeys = new HashSet<Keyboard.Key>();

	public static final int KBD_NONE = 0;
	public static final int KBD_DOTDASH = 1;
	public static final int KBD_UTILITY = 2;

	public boolean mEnableUtilityKeyboard = false;
	
    private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int DEBOUNCE_TIMEOUT = 50; //70;
    
    private static final int MSG_KEY_REPEAT = 1;
    private static final int MSG_KEY_BOUNCE = 2;
    
    Handler handler = new Handler(
		new Handler.Callback() {
			
			@Override
			public boolean handleMessage(Message msg) {
				switch (msg.what) {
					case MSG_KEY_REPEAT:
						Keyboard.Key k = (Keyboard.Key) msg.obj;
						if (!k.pressed) {
							return true;
						}
						
						getOnKeyboardActionListener().onKey(k.codes[0], k.codes);
						handler.sendMessageDelayed(handler.obtainMessage(MSG_KEY_REPEAT, k), REPEAT_INTERVAL);
						break;
					case MSG_KEY_BOUNCE:
						// Do nothing! This message was just there to prevent people from accidentally
						// touching the same key twice in quick succession.
						break;
				}

				return true;
			}
		}
	);

	public void setService(DotDashIMEService service) {
		this.service = service;
	}

	public DotDashKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setEverythingUp();
	}

	public DotDashKeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setEverythingUp();
	}

	@SuppressWarnings("deprecation")
	private void setEverythingUp() {
		mSwipeThreshold = (int) (300 * getResources().getDisplayMetrics().density);
		setPreviewEnabled(false);
		gestureDetector = new GestureDetector(
				new GestureDetector.SimpleOnGestureListener() {

					/**
					 * This function mostly copied from LatinKeyboardBaseView in
					 * the Hacker's Keyboard project: http://code.google.com/p/hackerskeyboard/
					 * 
				 	 * Copyright (C) 2010, authors of the Hacker's Keyboard project: http://code.google.com/p/hackerskeyboard/ 
				 	 * Copyright (c) 2011, Aaron Wells
					 * 
					 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
					 * use this file except in compliance with the License. You may obtain a copy of
					 * the License at
					 *
					 * http://www.apache.org/licenses/LICENSE-2.0
					 */
					@Override
					public boolean onFling(MotionEvent e1, MotionEvent e2,
							float velocityX, float velocityY) {

						// If they swipe up off the keyboard, launch the cheat
						// sheet. This was originally a check for e2.getY() < 0,
						// but that didn't work in ICS. Possibly ICS stops
						// sending you events after you go past the edge of the
						// window. So I changed it to 10 instead.
						if (e2.getY() <= 10) {
							// If they swipe up off the keyboard, launch the
							// cheat sheet
							showCheatSheet();
							return true;
						} else if (mEnableUtilityKeyboard) {
							final float absX = Math.abs(velocityX);
							final float absY = Math.abs(velocityY);
							float deltaX = e2.getX() - e1.getX();
							int travelMin = Math.min((getWidth() / 3),
									(getHeight() / 3));

							if (velocityX > mSwipeThreshold && absY < absX
									&& deltaX > travelMin) {
								toggleKeyboard();
								return true;
							} else if (velocityX < -mSwipeThreshold
									&& absY < absX && deltaX < -travelMin) {
								toggleKeyboard();
								return true;
							}
						}
						return false;
					}
				});
		
//		View.OnTouchListener gestureListener = new View.OnTouchListener() {
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
//				if (gestureDetector.onTouchEvent(event)) {
//					// Tell the underlying keyboardview to cancel its
//					// touch event if we've initiated a gesture.
//					MotionEvent cancel = MotionEvent.obtain(event);
//					cancel.setAction(MotionEvent.ACTION_CANCEL);
//					DotDashKeyboardView.this.onTouchEvent(cancel);
//					cancel.recycle();
//					return true;
//				} else {
//					return false;
//				}
//			}
//		};
//		setOnTouchListener(gestureListener);
	}

	private void toggleKeyboard() {
		if (getKeyboard() == service.dotDashKeyboard) {
			setKeyboard(service.utilityKeyboard);
			// TODO: Make this work. I think it's a layout issue...
//			setPreviewEnabled(true);
		} else {
			setKeyboard(service.dotDashKeyboard);
//			setPreviewEnabled(false);
		}
	}

	@SuppressLint("InflateParams")
	public void createCheatSheet() {
		boolean updateTouchListeners = false;
		if (this.cheatsheetDialog == null) {
			this.cheatsheetDialog = new Dialog(this.service);

			cheatsheetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

			cheatsheetDialog.setCancelable(true);
			cheatsheetDialog.setCanceledOnTouchOutside(true);
			updateTouchListeners = true;
		}
		if (this.cheatsheet1 == null) {
			this.cheatsheet1 = (TableLayout) this.service.getLayoutInflater().inflate(
					R.layout.cheatsheet1, null);
			this.prettifyCheatSheet(this.cheatsheet1);
			updateTouchListeners = true;
		}
		if (this.cheatsheet2 == null) {
			this.cheatsheet2 = (TableLayout) this.service.getLayoutInflater().inflate(
					R.layout.cheatsheet2, null);
			updateNewlineCode();
			this.prettifyCheatSheet(this.cheatsheet2);
			updateTouchListeners = true;
		}
		
		if (updateTouchListeners) {
			cheatsheetDialog.setContentView(cheatsheet1);
			cheatsheet1.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					cheatsheetDialog.setContentView(cheatsheet2);
					return true;
				}
			});
			cheatsheet2.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					cheatsheetDialog.setContentView(cheatsheet1);
					return true;
				}
			});
			Window window = this.cheatsheetDialog.getWindow();
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.token = this.getWindowToken();
			lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
			window.setAttributes(lp);
			window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
		}
	}
	
	/**
	 * Update the characters in the cheat sheet dialogue to match the user's preference
	 * @TODO: Probably better performance if I replaced this with two hard-coded versions
	 * of the sheet...
	 * 
	 * @param cheatsheet
	 */
	public void prettifyCheatSheet(TableLayout cheatsheet) {
		// No action necessary.
		if (service.ditdahcharsPref == DotDashIMEService.DITDAHCHARS_UNICODE) {
			return;
		}
		
		for (int i = 0; i < cheatsheet.getChildCount(); i++) {
			TableRow row = (TableRow) cheatsheet.getChildAt(i);
			
			// On my cheat sheets, only the even-number columns
			// contain code groups
			for (int j = 1; j < row.getChildCount(); j += 2) {
				TextView cell = (TextView) row.getChildAt(j);
				cell.setText(service.convertDitDahUnicodeToAscii(cell.getText().toString(), true));
			}
		}
	}

	public void showCheatSheet() {
		createCheatSheet();
		cheatsheetDialog.show();
	}
	
	public void closeCheatSheet() {
		if (cheatsheetDialog != null) {
			cheatsheetDialog.dismiss();
		}
	}
	
	public void clearCheatSheet() {
		closeCheatSheet();
		this.cheatsheet1 = null;
		this.cheatsheet2 = null;
	}

	/**
	 * Updates the newline code printed in the cheat sheet, based on the user's
	 * current preference.
	 */
	public void updateNewlineCode() {
		if (cheatsheet2 == null) {
			return;
		}

		String newCode = service.getText(R.string.newline_disabled).toString();
		if (service.newlineGroups != null && service.newlineGroups.length > 0) {
			newCode = service.newlineGroups[0].replace(".", DotDashIMEService.UNICODE_DOT).replace("-",  DotDashIMEService.UNICODE_DASH);
		}
		((TextView) cheatsheet2.findViewById(R.id.newline_code))
				.setText(newCode);
	}

	public int whichKeyboard() {
		Keyboard kbd = getKeyboard();
		if (kbd == service.dotDashKeyboard) {
			return KBD_DOTDASH;
		} else if (kbd == service.utilityKeyboard) {
			return KBD_UTILITY;
		} else
			return KBD_NONE;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent me) {
		Log.d(TAG, "onTouchEvent");

		// TODO: Unfortunately, since I send the character when you first press
		// the key, all the keys you press while swiping still count as getting
		// pressed.
		//
		// Not sure what I could do about that... maybe a more sensitive 
		// swipe detector?
		if (gestureDetector.onTouchEvent(me)) {
			for(Keyboard.Key k : pressedKeys) {
				k.onReleased(false);
			}
			invalidateAllKeys();
			pressedKeys.clear();
			return true;
		}

		// Let KeyboardView handle the utility keyboard. 
		if (whichKeyboard() == DotDashKeyboardView.KBD_UTILITY) {
			return super.onTouchEvent(me);
		}

		int actionmasked = me.getActionMasked();
		int actionindex = me.getActionIndex();
		Set<Keyboard.Key> curPressedKeys = new HashSet<Keyboard.Key>();
		
		for (int i=0; i < me.getPointerCount(); i++) {
			
			// Find out which key the pointer is on
			int x = (int) me.getX(i);
			int y = (int) me.getY(i);
			int[] keys = service.dotDashKeyboard.getNearestKeys(x, y);
			Keyboard.Key touchedKey = null;
			for (int k : keys) {
				Keyboard.Key key = service.dotDashKeyboard.getKeys().get(k);
				// TODO: This continues to detect it even after you've moved off the keyboard. :-P
				if (key.isInside(x, y)) {
					touchedKey = key;
				}
			}
			
			if (touchedKey != null) {
				if (i == actionindex) {
					switch (actionmasked) {
						case MotionEvent.ACTION_DOWN:
						case MotionEvent.ACTION_MOVE:
							curPressedKeys.add(touchedKey);
							break;
						case MotionEvent.ACTION_UP:
						case MotionEvent.ACTION_CANCEL:
							curPressedKeys.remove(touchedKey);
							break;
					}
				}
			}
		}
		
		// Now that we know which keys have fingers on 'em this time,
		// let's check to see how that has changed from last time.
		
		// Keys that are in curPressedKeys but not in pressedKeys
		// are newly pressed.
		Set<Keyboard.Key> newlyPressed = new HashSet<Keyboard.Key>(curPressedKeys);
		newlyPressed.removeAll(pressedKeys);
		for (Keyboard.Key k : newlyPressed) {
			if (handler.hasMessages(MSG_KEY_BOUNCE, k) || k.pressed) {
				continue;
			}

//			k.onPressed();
			k.pressed = true;
			getOnKeyboardActionListener().onPress(k.codes[0]);
			getOnKeyboardActionListener().onKey(k.codes[0], k.codes);
			if (k.repeatable && !handler.hasMessages(MSG_KEY_REPEAT, k)) {
				handler.sendMessageDelayed(
						handler.obtainMessage(MSG_KEY_REPEAT, k),
						REPEAT_START_DELAY
				);
			}
			invalidateKey(service.dotDashKeyboard.getKeys().indexOf(k));
		}
		
		// Keys that are in pressedKeys but not curPressedKeys
		// are newly released.
		Set<Keyboard.Key> newlyReleased = new HashSet<Keyboard.Key>(pressedKeys);
		newlyReleased.removeAll(curPressedKeys);
		for (Keyboard.Key k : newlyReleased) {
			if (!k.pressed) {
				continue;
			}

			k.pressed = false;
			getOnKeyboardActionListener().onRelease(k.codes[0]);
			if (k.repeatable) {
				handler.removeMessages(MSG_KEY_REPEAT, k);
			}
			handler.sendMessageDelayed(
					handler.obtainMessage(DotDashKeyboardView.MSG_KEY_BOUNCE, k), 
					DotDashKeyboardView.DEBOUNCE_TIMEOUT
			); 
			invalidateKey(service.dotDashKeyboard.getKeys().indexOf(k));
		}
		
		pressedKeys = curPressedKeys;

//		for (Keyboard.Key k : service.dotDashKeyboard.getKeys()) {
//			Log.d(TAG, "Key " + String.valueOf(k.codes[0]) + " " + (k.pressed ? "down" : "up"));
//		}

		return true;
	}
	
	@Override
	public void invalidateKey(int keyIndex) {
		// TODO Auto-generated method stub
		super.invalidateKey(keyIndex);
	}
	
	@Override
	public void invalidateAllKeys() {
		// TODO Auto-generated method stub
		super.invalidateAllKeys();
	}
}

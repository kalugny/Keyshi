package com.kalgon.gamepadkeyboard;

import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class GamepadKeyboardService extends InputMethodService implements View.OnTouchListener {

    private final boolean DEBUG = true;

    //    private InputMethodManager mInputMethodManager;
    private View mView = null;
    private WindowManager mWindowManager;

    private boolean mCapsLock;
    private long mLastShiftTime;

    // Position of the stick (which represents the letters available). 0-8 where
    // 0 is centered, 1 is 12 o'clock and advancing clockwise every eighth
    private int mStickPosition = 0;
    private int mRightStickPosition = 0;

    private KeyMap mCurrentKeyboard = null;
    private ArrayList<KeyMap> mKeyboards = new ArrayList<KeyMap>() {
    };
    private int mKeyboardIndex = 0;
    private final String[] AVAILABLE_KEYBOARDS = new String[]{"english", "hebrew"};
    private KeyMap mSymbols = null;
    private boolean mSymbolsShown = false;

    private boolean mShift = false;
    private boolean mEnterPressed = false;

    private boolean mViewAddedToWindowManager = false;
    private int mViewDeltaX = 0;
    private int mViewDeltaY = 0;

    private boolean mUsingGamepad = false;
    private final long KEY_REPEAT_MS = 200;

    final Handler handler = new Handler();
    private int mKeyRepeat = 0;

    // DEBUG
    private int debugDpadUpDown = 0;
    private int debugDpadLeftRight = 0;
    private float l2Value = 0, r2Value = 0;
    private long lastToastTime = 0;
    private float debugHatX, debugHatY;


    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override
    public void onCreate() {
        Log.i("GamepadKeyboard", "onCreate");
        super.onCreate();

        //        Debug.waitForDebugger();

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.i("GamepadKeyboard", "onDestroy");
        if (mView != null) mWindowManager.removeView(mView);
        super.onDestroy();
    }


    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {
        Log.d("GamepadKeyboard", "onInitializeInterface");
        // TODO: How to do this only if something really changed?
        try {
            if (mKeyboards.size() == 0) {
                mSymbols = new KeyMap(getApplicationContext(), R.xml.symbols);
                for (String keyboardName : AVAILABLE_KEYBOARDS) {
                    int resourceId = getResources().getIdentifier(keyboardName, "xml", getPackageName());
                    mKeyboards.add(new KeyMap(getApplicationContext(), resourceId));
                }
            }
        } catch (Exception e) {
            Log.e("onInitializeInterface", e.toString());
        }
    }

    private boolean usingFloatingKeyboard() {
        return Settings.canDrawOverlays(getApplicationContext());
    }

    private void addViewToWindowManager() {
        Log.d("addViewToWindowManager", "mViewAddedToWindowManager = " + mViewAddedToWindowManager);
        if (!mViewAddedToWindowManager && mView != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;

            Log.d("addViewToWindowManager", "mView parent = " + mView.getParent());
            mWindowManager.addView(mView, params);

            mViewAddedToWindowManager = true;
        }
    }

    private void removeViewFromWindowManager() {
        Log.d("removeViewFromWindowManager", "mViewAddedToWindowManager = " + mViewAddedToWindowManager);
        if (mViewAddedToWindowManager) {
            mWindowManager.removeView(mView);
            Log.d("removeViewFromWindowManager", "mView parent = " + mView.getParent());
            mViewAddedToWindowManager = false;
        }
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override
    public View onCreateInputView() {
        Log.i("GamepadKeyboard", "onCreateInputView");
        if (mView == null) {
            mView = getLayoutInflater().inflate(R.layout.diamond_ui, null);
            setupView();

            if (usingFloatingKeyboard()) {
                mView.setOnTouchListener(this);
            }
        }

        if (usingFloatingKeyboard()) {
            return null;
        }

        return mView;
    }

    private void setupView() {
        Log.d("GamepadKeyboard", "setupView");
        final char[] buttons = {'A', 'B', 'X', 'Y'};
        for (int i = 0; i < 9; i++) {
            View circle = mView.findViewById(getResources().getIdentifier("diamond_" + i, "id", getPackageName()));
            for (int b = 0; b < buttons.length; b++) {
                TextView buttonView = circle.findViewById(getResources().getIdentifier(String.valueOf(buttons[b]), "id", getPackageName()));
                String letter = mCurrentKeyboard.getKey(i, b, mShift);
                buttonView.setText(letter);
            }
        }
        highlightStickPosition();
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        Log.i("GamepadKeyboard", "onStartInput. restarting = " + restarting + ", Input type = " + attribute.inputType);
        super.onStartInput(attribute, restarting);

        if (attribute.inputType == InputType.TYPE_NULL) {
            // This is not an input editor, so it's better that the gamepad will work as a gamepad
            // and not as a keyboard
            mUsingGamepad = false;
            return;
        }
        mUsingGamepad = true;

        if (!restarting) {
            // We are now going to initialize our state based on the type of
            // text being edited.
            switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
                case InputType.TYPE_CLASS_NUMBER:
                case InputType.TYPE_CLASS_DATETIME:
                    // Numbers and dates default to the symbols keyboard, with
                    // no extra features.
                    toggleSymbols(true);
                    break;

                case InputType.TYPE_CLASS_PHONE:
                    // Phones will also default to the symbols keyboard, though
                    // often you will want to have a dedicated phone keyboard.
                    toggleSymbols(true);
                    break;

                default:
                    // For all unknown input types, default to the alphabetic
                    // keyboard with no special features.
                    mCurrentKeyboard = mKeyboards.get(mKeyboardIndex);
            }
        }
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override
    public void onFinishInput() {
        Log.i("GamepadKeyboard", "onFinishInput");

        super.onFinishInput();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        Log.i("GamepadKeyboard", "onFinishInputView");
        if (usingFloatingKeyboard()) {
            removeViewFromWindowManager();
        }
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        Log.i("GamepadKeyboard", "onStartInputView. restarting = " + restarting);
        super.onStartInputView(attribute, restarting);
        if (usingFloatingKeyboard()) {
            addViewToWindowManager();
        }
        setupView();
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("GamepadKeyboard", "onKeyDown. Key = " + keyCode);

        if (!mUsingGamepad) return false;

//        if (System.currentTimeMillis() - lastToastTime > 1000){
//            Toast.makeText(getApplicationContext(), "key = " + keyCode, Toast.LENGTH_SHORT).show();
//            lastToastTime = System.currentTimeMillis();
//        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_A: // DEBUG
            case KeyEvent.KEYCODE_B: // DEBUG
            case KeyEvent.KEYCODE_X: // DEBUG
            case KeyEvent.KEYCODE_Y: // DEBUG
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {

                    if (DEBUG) {
                        if (keyCode == KeyEvent.KEYCODE_A) keyCode = KeyEvent.KEYCODE_BUTTON_A;
                        if (keyCode == KeyEvent.KEYCODE_B) keyCode = KeyEvent.KEYCODE_BUTTON_B;
                        if (keyCode == KeyEvent.KEYCODE_X) keyCode = KeyEvent.KEYCODE_BUTTON_X;
                        if (keyCode == KeyEvent.KEYCODE_Y) keyCode = KeyEvent.KEYCODE_BUTTON_Y;
                    }

                    String key = mCurrentKeyboard.getKey(mStickPosition, mCurrentKeyboard.keyCodeToButtonIndex(keyCode), mShift);
                    ic.commitText(key, 1);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_R:  // DEBUG
                keyDownUp(KeyEvent.KEYCODE_SPACE);
                return true;

            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_L:  // DEBUG
                keyDownUp(KeyEvent.KEYCODE_DEL);
                return true;

            case KeyEvent.KEYCODE_DPAD_UP: // DEBUG
                debugDpadUpDown = -1;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN: // DEBUG
                debugDpadUpDown = 1;
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT: // DEBUG
                debugDpadLeftRight = -1;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT: // DEBUG
                debugDpadLeftRight = 1;
                break;

            case KeyEvent.KEYCODE_7: // DEBUG
                debugHatX = 1f;
                break;

            case KeyEvent.KEYCODE_8: // DEBUG
                debugHatX = -1f;
                break;

            case KeyEvent.KEYCODE_9: // DEBUG
                debugHatY = 1f;
                break;

            case KeyEvent.KEYCODE_0: // DEBUG
                debugHatY = -1f;
                break;

            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_1: // DEBUG
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                return true;

            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_2: // DEBUG
                mShift = true;
                setupView();
                return true;
        }

        if (DEBUG) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                updateDpadStickPosition();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_7
                    || keyCode == KeyEvent.KEYCODE_8
                    || keyCode == KeyEvent.KEYCODE_9
                    || keyCode == KeyEvent.KEYCODE_0) {
                updateHat(debugHatX, debugHatY);
                return true;
            }
        }


        return super.onKeyDown(keyCode, event);
    }

    // DEBUG
    private void updateDpadStickPosition() {
        float x = debugDpadLeftRight;
        float y = debugDpadUpDown;

        if (debugDpadLeftRight != 0 && debugDpadUpDown != 0) {
            x *= 0.75;
            y *= 0.75;
        }

        updateStickPosition(x, y);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i("GamepadKeyboard", "onKeyUp");
        super.onKeyUp(keyCode, event);

        if (!mUsingGamepad) return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:   // DEBUG
            case KeyEvent.KEYCODE_DPAD_DOWN: // DEBUG
                debugDpadUpDown = 0;
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:  // DEBUG
            case KeyEvent.KEYCODE_DPAD_RIGHT: // DEBUG
                debugDpadLeftRight = 0;
                break;

            case KeyEvent.KEYCODE_7: // DEBUG
            case KeyEvent.KEYCODE_8: // DEBUG
                debugHatX = 0;
                break;

            case KeyEvent.KEYCODE_9: // DEBUG
            case KeyEvent.KEYCODE_0: // DEBUG
                debugHatY = 0;
                break;

            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_2:   // DEBUG
                mShift = false;
                setupView();
                return true;
        }

        if (DEBUG && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            updateDpadStickPosition();
            return true;
        }

        return false;
    }


    private void highlightStickPosition() {
        for (int i = 0; i < 9; i++) {
            View circle = mView.findViewById(getResources().getIdentifier("diamond_" + i, "id", getPackageName()));
            circle.setBackgroundResource(mStickPosition == i ? R.drawable.circle_selected : R.drawable.circle);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a game controller

        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {

//            // Process all historical movement samples in the batch
//            final int historySize = event.getHistorySize();
//
//            // Process the movements starting from the
//            // earliest historical position in the batch
//            for (int i = 0; i < historySize; i++) {
//                // Process the event at historical position i
//                processJoystickInput(event, i);
//            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private float getCenteredAxis(MotionEvent event,
                                  InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis) :
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {

        InputDevice inputDevice = event.getDevice();

        float x = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_X, historyPos);
        float y = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Y, historyPos);
        updateStickPosition(x, y);

        // Check the DPAD (hat)
        float hatX = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_HAT_X, historyPos);
        float hatY = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_HAT_Y, historyPos);
        updateHat(hatX, hatY);


        float rx = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Z, historyPos);
        float ry = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_RZ, historyPos);
        updateRightStickPosition(rx, ry);
    }

    private void updateHat(float hatX, float hatY) {
        if (hatY == -1.0f) {
            toggleSymbols(false);
        } else if (hatX == 1.0f) {
            nextKeyboard();
        } else if (hatX == -1.0f) {
            prevKeyboard();
        }
    }

    private void nextKeyboard() {
        mKeyboardIndex++;
        if (mKeyboardIndex >= mKeyboards.size()) mKeyboardIndex = 0;
        if (!mSymbolsShown) {
            mCurrentKeyboard = mKeyboards.get(mKeyboardIndex);
            setupView();
        }
    }

    private void prevKeyboard() {
        mKeyboardIndex--;
        if (mKeyboardIndex < 0) mKeyboardIndex = mKeyboards.size() - 1;
        if (!mSymbolsShown) {
            mCurrentKeyboard = mKeyboards.get(mKeyboardIndex);
            setupView();
        }
    }

    private void toggleSymbols(boolean force) {
        if (mSymbolsShown && !force) {
            mCurrentKeyboard = mKeyboards.get(mKeyboardIndex);
        } else {
            mCurrentKeyboard = mSymbols;
        }
        mSymbolsShown = mCurrentKeyboard == mSymbols;
        setupView();
    }

    private void updateStickPosition(float x, float y) {

        double omega = Math.atan2(x, y) / Math.PI * 180;

        // We're looking for a 45 degree slice for each of the main 8 directions
        int newStickPos = -1;
        if (x == 0 && y == 0) newStickPos = 0;
        else if (omega > 158 || omega <= -158) newStickPos = 1;
        else if (omega <= 158 && omega > 113) newStickPos = 2;
        else if (omega <= 113 && omega > 68) newStickPos = 3;
        else if (omega <= 68 && omega > 23) newStickPos = 4;
        else if (omega <= 23 && omega > -22) newStickPos = 5;
        else if (omega <= -22 && omega > -67) newStickPos = 6;
        else if (omega <= -67 && omega > -112) newStickPos = 7;
        else if (omega <= -112 && omega > -158) newStickPos = 8;

//        Log.d("processJoystickInput", String.format("x = %f, y = %f, omega = %f, newStickPos = %d", x, y, omega, newStickPos));

        if (newStickPos != mStickPosition) {
            mStickPosition = newStickPos;
            highlightStickPosition();
        }
    }

    private void updateRightStickPosition(float x, float y) {
        double omega = Math.atan2(x, y) / Math.PI * 180;

        // We're looking for specific up-down-left-right, so we're ignoring diagonals
        // Keeping the numbering 0, 1, 3, 5, 7 for consistency with the left stick directions
        int newDirection = -1;
        if (x == 0 && y == 0) newDirection = 0;
        else if (omega > 135 || omega <= -135) newDirection = 1;
        else if (omega > 45 && omega <= 135) newDirection = 3;
        else if (omega > -45 && omega <= 45) newDirection = 5;
        else if (omega > -135 && omega <= -45) newDirection = 7;

        if (newDirection != mRightStickPosition) {
            keyDownUpStop();
            mRightStickPosition = newDirection;

            switch (mRightStickPosition) {
                case 1:
                    keyDownUpRepeat(KeyEvent.KEYCODE_DPAD_UP);
                    break;
                case 3:
                    keyDownUpRepeat(KeyEvent.KEYCODE_DPAD_RIGHT);
                    break;
                case 5:
                    keyDownUpRepeat(KeyEvent.KEYCODE_DPAD_DOWN);
                    break;
                case 7:
                    keyDownUpRepeat(KeyEvent.KEYCODE_DPAD_LEFT);
                    break;
            }
        }


    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDown(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
    }

    private void keyDownUpRepeat(int keyEventCode) {
        mKeyRepeat = keyEventCode;
        keyDownUp(keyEventCode);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mKeyRepeat == keyEventCode) {
                    keyDownUp(keyEventCode);
                    handler.postDelayed(this, KEY_REPEAT_MS);
                }
            }
        }, KEY_REPEAT_MS);

    }

    private void keyDownUpStop() {
        mKeyRepeat = 0;
    }

    private void keyUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private void keyDownUp(int keyEventCode) {
        keyDown(keyEventCode);
        keyUp(keyEventCode);
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        if (keyCode == '\n') {
            keyDownUp(KeyEvent.KEYCODE_ENTER);
        } else {
            if (keyCode >= '0' && keyCode <= '9') {
                keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
            } else {
                getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
            }
        }
    }


//
//    private IBinder getToken() {
//        final Dialog dialog = getWindow();
//        if (dialog == null) {
//            return null;
//        }
//        final Window window = dialog.getWindow();
//        if (window == null) {
//            return null;
//        }
//        return window.getAttributes().token;
//    }
//
//    private void checkToggleCapsLock() {
//        long now = System.currentTimeMillis();
//        if (mLastShiftTime + 800 > now) {
//            mCapsLock = !mCapsLock;
//            mLastShiftTime = 0;
//        } else {
//            mLastShiftTime = now;
//        }
//    }
//

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        final int X = (int) event.getRawX();
        final int Y = (int) event.getRawY();
        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                WindowManager.LayoutParams lParams = (WindowManager.LayoutParams) view.getLayoutParams();
                mViewDeltaX = X - lParams.x;
                mViewDeltaY = Y - lParams.y;
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) view.getLayoutParams();
                layoutParams.x = X - mViewDeltaX;
                layoutParams.y = Y - mViewDeltaY;
                mWindowManager.updateViewLayout(view, layoutParams);
                break;
        }
        mView.invalidate();
        return true;

    }
}

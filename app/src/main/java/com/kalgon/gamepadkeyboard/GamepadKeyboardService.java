package com.kalgon.gamepadkeyboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class GamepadKeyboardService extends InputMethodService implements View.OnTouchListener {

    private final boolean DEBUG = true;

    private InputMethodManager mInputMethodManager;
    private View mView = null;
    private WindowManager mWindowManager;

    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

//    private LatinKeyboard mSymbolsKeyboard;
//    private LatinKeyboard mSymbolsShiftedKeyboard;
//    private LatinKeyboard mQwertyKeyboard;
//
//    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;

    // Position of the stick (which represents the letters available). 0-8 where
    // 0 is centered, 1 is 12 o'clock and advancing clockwise every eighth
    private int mStickPosition = 0;
    // This is the mapping between the stick position and the 4 face buttons and 2 triggers
    // TODO: Move this to a config file
    private final int BACKSPACE = -1;
    private final int SPACE = -2;
    private int[][] mKeyMap = {
            // A, B, X, Y, R, L
            {'E', 'T', 'A', 'O', SPACE, BACKSPACE},
            {'I', 'N', 'S', 'R', SPACE, BACKSPACE},
            {'V', 'K', 'X', 'Q', SPACE, BACKSPACE},
            {'H', 'D', 'L', 'U', SPACE, BACKSPACE},
            {'J', 'Z', ',', '.', SPACE, BACKSPACE},
            {'C', 'M', 'F', 'Y', SPACE, BACKSPACE},
            {'\'', ':', ';', '-', SPACE, BACKSPACE},
            {'W', 'G', 'P', 'B', SPACE, BACKSPACE},
            {'+', '"', '!', '?', SPACE, BACKSPACE}
    };

    private final int[] KEY_ORDER = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L1};

    private boolean mShift = false;
    private boolean mEnterPressed = false;

    private boolean mViewAddedToWindowManager = false;
    private int mViewDeltaX = 0;
    private int mViewDeltaY = 0;

    // DEBUG
    private int dpadUpDown = 0;
    private int dpadLeftRight = 0;
    private float l2Value = 0, r2Value = 0;
    private long lastToastTime = 0;


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

        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    @Override
    public void onDestroy() {
        if (mView != null) mWindowManager.removeView(mView);
        super.onDestroy();
    }

    /**
     * Create new context object whose resources are adjusted to match the metrics of the display
     * which is managed by WindowManager.
     */
    @NonNull
    Context getDisplayContext() {
        // TODO (b/133825283): Non-activity components Resources / DisplayMetrics update when
        //  moving to external display.
        // An issue in Q that non-activity components Resources / DisplayMetrics in
        // Context doesn't well updated when the IME window moving to external display.
        // Currently we do a workaround is to create new display context directly and re-init
        // keyboard layout with this context.
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return createDisplayContext(wm.getDefaultDisplay());
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
//    @Override public void onInitializeInterface() {
//        final Context displayContext = getDisplayContext();
//        if (mQwertyKeyboard != null) {
//            // Configuration changes can happen after the keyboard gets recreated,
//            // so we need to be able to re-build the keyboards if the available
//            // SPACE has changed.
//            int displayWidth = getMaxWidth();
//            if (displayWidth == mLastDisplayWidth) return;
//            mLastDisplayWidth = displayWidth;
//        }
//        mQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
//        mSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
//        mSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);
//    }
    private boolean usingFloatingKeyboard() {
        return Settings.canDrawOverlays(getApplicationContext());
    }

    private void addViewToWindowManager() {
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

            mWindowManager.addView(mView, params);

            mViewAddedToWindowManager = true;
        }
    }

    private void removeViewFromWindowManager() {
        if (mViewAddedToWindowManager) {
            mWindowManager.removeView(mView);
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
            setupView(mView, mKeyMap);

            if (usingFloatingKeyboard()) {
                mView.setOnTouchListener(this);
                return null;
            }
        }

        return mView;
    }

    private void setupView(View view, int[][] keyMap) {
        final char[] buttons = {'A', 'B', 'X', 'Y'};
        for (int i = 0; i < 9; i++) {
            View circle = view.findViewById(getResources().getIdentifier("diamond_" + String.valueOf(i), "id", getPackageName()));
            Log.d("Test", String.format("%s %d", circle, i));
            for (int b = 0; b < buttons.length; b++) {
                TextView buttonView = circle.findViewById(getResources().getIdentifier(String.valueOf(buttons[b]), "id", getPackageName()));
                Log.d("Test", String.format("%s, %d, %c, %s", buttonView, b, keyMap[i][b], String.valueOf(keyMap[i][b])));
                String letter = String.valueOf((char) keyMap[i][b]);
                if (mShift) {
                    letter = letter.toUpperCase();
                } else {
                    letter = letter.toLowerCase();
                }
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
        Log.i("GamepadKeyboard", "onStartInput");
        super.onStartInput(attribute, restarting);

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
//                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
//                mCurKeyboard = mSymbolsKeyboard;
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
//                mCurKeyboard = mQwertyKeyboard;
//                updateShiftKeyState(attribute);
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
        if (usingFloatingKeyboard()) {
            removeViewFromWindowManager();
        }
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        Log.i("GamepadKeyboard", "onStartInputView");
        super.onStartInputView(attribute, restarting);
        if (usingFloatingKeyboard()) {
            addViewToWindowManager();
        }
    }

//    @Override
//    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
//        mInputView.setSubtypeOnSPACEKey(subtype);
//    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
//    private boolean translateKeyDown(int keyCode, KeyEvent event) {
//        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
//                keyCode, event);
//        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
//        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
//        InputConnection ic = getCurrentInputConnection();
//        if (c == 0 || ic == null) {
//            return false;
//        }
//
//        boolean dead = false;
//        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
//            dead = true;
//            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
//        }
//
//        onKey(c, null);
//
//        return true;
//    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i("GamepadKeyboard", "onKeyDown");
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
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L1:
                // DEBUG
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_X:
            case KeyEvent.KEYCODE_Y:
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_L:
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {

                    if (DEBUG) {
                        if (keyCode == KeyEvent.KEYCODE_A) keyCode = KeyEvent.KEYCODE_BUTTON_A;
                        if (keyCode == KeyEvent.KEYCODE_B) keyCode = KeyEvent.KEYCODE_BUTTON_B;
                        if (keyCode == KeyEvent.KEYCODE_X) keyCode = KeyEvent.KEYCODE_BUTTON_X;
                        if (keyCode == KeyEvent.KEYCODE_Y) keyCode = KeyEvent.KEYCODE_BUTTON_Y;
                        if (keyCode == KeyEvent.KEYCODE_R) keyCode = KeyEvent.KEYCODE_BUTTON_R1;
                        if (keyCode == KeyEvent.KEYCODE_L) keyCode = KeyEvent.KEYCODE_BUTTON_L1;
                    }

                    int i = 0;
                    for (; i < KEY_ORDER.length; i++) {
                        if (KEY_ORDER[i] == keyCode) break;
                    }

                    int key = mKeyMap[mStickPosition][i];

                    if (key == BACKSPACE) {
                        keyDownUp(KeyEvent.KEYCODE_DEL);
                        return true;
                    }
                    if (key == SPACE) {
                        keyDownUp(KeyEvent.KEYCODE_SPACE);
                        return true;
                    }

                    String keyS = String.valueOf((char) key);
                    ic.commitText(mShift ? keyS.toUpperCase() : keyS.toLowerCase(), 1);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                dpadUpDown = -1;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                dpadUpDown = 1;
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                dpadLeftRight = -1;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dpadLeftRight = 1;
                break;

            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_1:
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                return true;

            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_2:
                mShift = true;
                setupView(mView, mKeyMap);
                return true;
        }

        if (DEBUG && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            updateDpadStickPosition();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void updateDpadStickPosition() {
        float x = dpadLeftRight;
        float y = dpadUpDown;

        if (dpadLeftRight != 0 && dpadUpDown != 0) {
            x *= 0.75;
            y *= 0.75;
        }

        updateStickPosition(x, y);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i("GamepadKeyboard", "onKeyUp");
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dpadUpDown = 0;
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dpadLeftRight = 0;
                break;

            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_2:
                mShift = false;
                setupView(mView, mKeyMap);
                return true;
        }

        if (DEBUG && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            updateDpadStickPosition();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }


    private void highlightStickPosition() {
        char[] buttons = {'A', 'B', 'X', 'Y'};
        for (int i = 0; i < 9; i++) {
            View circle = mView.findViewById(getResources().getIdentifier("diamond_" + String.valueOf(i), "id", getPackageName()));
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

//            Log.d("getCenteredAxis", String.format("flat = %f, value = %f", flat, value));
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

//        // Check the R2 and L2 triggers
//        float l2 = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_LTRIGGER, historyPos);
//        float r2 = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_RTRIGGER, historyPos);
//
//        updateL2R2(l2, r2);
    }

//    private void updateL2R2(float l2, float r2){
//
//        // L2 is shift. Double click for caps lock.
//
//        if (l2 > 0 && !mShift){
//            mShift = true;
//            setupView(mView, mKeyMap);
//        }
//        else if (l2 == 0 && mShift){
//            mShift = false;
//            setupView(mView, mKeyMap);
//        }
//
//        // R2 is Enter
//        if (r2 > 0 && !mEnterPressed){
//            mEnterPressed = true;
//            keyDownUp(KeyEvent.KEYCODE_ENTER);
//        }
//        else if (r2 == 0 && mEnterPressed){
//            mEnterPressed = false;
//        }
//
//        Log.d("updateL2R2", String.format("mEnterPressed = %b, mShift = %b", mEnterPressed, mShift));
//    }

    //    private long lastToastTime = 0;
    private void updateStickPosition(float x, float y) {

        double omega = Math.atan2(x, y) / Math.PI * 180;

        // We're looking for a 45 degree slice for each of the main 8 directions

//        long now = System.currentTimeMillis();
//        if (now - lastToastTime > 1000) {
//            lastToastTime = now;
//            Toast.makeText(getApplicationContext(), String.format("x = %f, y = %f, omega = %f", x, y, omega), Toast.LENGTH_SHORT).show();
//        }

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

        Log.d("processJoystickInput", String.format("x = %f, y = %f, omega = %f, newStickPos = %d", x, y, omega, newStickPos));

        if (newStickPos != mStickPosition) {
            mStickPosition = newStickPos;
            highlightStickPosition();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
//    private void updateShiftKeyState(EditorInfo attr) {
//        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
//            int caps = 0;
//            EditorInfo ei = getCurrentInputEditorInfo();
//            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
//                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
//            }
//            mInputView.setShifted(mCapsLock || caps != 0);
//        }
//    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        return Character.isLetter(code);
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
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

    // Implementation of KeyboardViewListener
//    public void onKey(int primaryCode, int[] keyCodes) {
//        if (isWordSeparator(primaryCode)) {
//            // Handle separator
//            if (mComposing.length() > 0) {
//                commitTyped(getCurrentInputConnection());
//            }
//            sendKey(primaryCode);
//            updateShiftKeyState(getCurrentInputEditorInfo());
//        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
//            handleBackSPACE();
//        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
//            handleShift();
//        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
//            handleClose();
//            return;
//        } else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
//            handleLanguageSwitch();
//            return;
//        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
//            // Show a menu or somethin'
//        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
//                && mInputView != null) {
//            Keyboard current = mInputView.getKeyboard();
//            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
//                setLatinKeyboard(mQwertyKeyboard);
//            } else {
//                setLatinKeyboard(mSymbolsKeyboard);
//                mSymbolsKeyboard.setShifted(false);
//            }
//        } else {
//            handleCharacter(primaryCode, keyCodes);
//        }
//    }

//    public void onText(CharSequence text) {
//        InputConnection ic = getCurrentInputConnection();
//        if (ic == null) return;
//        ic.beginBatchEdit();
//        if (mComposing.length() > 0) {
//            commitTyped(ic);
//        }
//        ic.commitText(text, 0);
//        ic.endBatchEdit();
//        updateShiftKeyState(getCurrentInputEditorInfo());
//    }

//    private void handleBackSPACE() {
//        final int length = mComposing.length();
//        if (length > 1) {
//            mComposing.delete(length - 1, length);
//            getCurrentInputConnection().setComposingText(mComposing, 1);
//        } else if (length > 0) {
//            mComposing.setLength(0);
//            getCurrentInputConnection().commitText("", 0);
//        } else {
//            keyDownUp(KeyEvent.KEYCODE_DEL);
//        }
//        updateShiftKeyState(getCurrentInputEditorInfo());
//    }
//
//    private void handleShift() {
//        if (mInputView == null) {
//            return;
//        }
//
//        Keyboard currentKeyboard = mInputView.getKeyboard();
//        if (mQwertyKeyboard == currentKeyboard) {
//            // Alphabet keyboard
//            checkToggleCapsLock();
//            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
//        } else if (currentKeyboard == mSymbolsKeyboard) {
//            mSymbolsKeyboard.setShifted(true);
//            setLatinKeyboard(mSymbolsShiftedKeyboard);
//            mSymbolsShiftedKeyboard.setShifted(true);
//        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
//            mSymbolsShiftedKeyboard.setShifted(false);
//            setLatinKeyboard(mSymbolsKeyboard);
//            mSymbolsKeyboard.setShifted(false);
//        }
//    }
//
//    private void handleCharacter(int primaryCode, int[] keyCodes) {
//        if (isInputViewShown()) {
//            if (mInputView.isShifted()) {
//                primaryCode = Character.toUpperCase(primaryCode);
//            }
//        }
//        getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
//    }
//
//    private void handleClose() {
//        commitTyped(getCurrentInputConnection());
//        requestHideSelf(0);
//        mInputView.closing();
//    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    private void handleLanguageSwitch() {
        mInputMethodManager.switchToNextInputMethod(getToken(), false);
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char) code));
    }

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

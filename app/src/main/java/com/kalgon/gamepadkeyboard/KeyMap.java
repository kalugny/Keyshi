package com.kalgon.gamepadkeyboard;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.view.KeyEvent;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Loads a keymap from an XML file
 */
public class KeyMap {

    private static final List<String> BUTTON_LETTER_ORDER = Arrays.asList("A", "B", "X", "Y");
    public static final String STICK_DIRECTION = "StickDirection";
    public static final String GAMEPAD_KEYBOARD = "GamepadKeyboard";
    private static int NO_ALT = 0;
    private static final int MAIN_KEY = 0;
    private static final int ALT_KEY = 1;

    private int[][][] mKeyMap;

    public KeyMap(Context context, int resourceId) throws IOException {
        try {
            mKeyMap = loadXml(context, resourceId);
        }
        catch (XmlPullParserException e){
            throw new IOException(e);
        }

    }

    public int keyCodeToButtonIndex(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return 0;
            case KeyEvent.KEYCODE_BUTTON_B:
                return 1;
            case KeyEvent.KEYCODE_BUTTON_X:
                return 2;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return 3;
        }
        throw new IllegalArgumentException("Keycode not supported");
    }

    public String getKey(int stickPosition, int buttonIndex) {
        return getKey(stickPosition, buttonIndex, false);
    }

    public String getKey(int stickPosition, int buttonIndex, boolean alt) {
        int[] pos = mKeyMap[stickPosition][buttonIndex];

        String retVal = String.valueOf((char) pos[MAIN_KEY]);
        if (pos[ALT_KEY] == NO_ALT) {
            return alt ? retVal.toUpperCase() : retVal.toLowerCase();
        }

        return alt ? String.valueOf((char) pos[ALT_KEY]) : retVal;
    }

    private int[][][] loadXml(Context context, int resourceId) throws XmlPullParserException, IOException {
        XmlResourceParser parser = context.getResources().getXml(resourceId);

        // The keymap to return
        int[][][] keyMap = new int[9][4][2];

        int currentStickPos = -1;
        int currentButtonIndex = -1;
        String alt = "";
        int eventType = parser.getEventType();
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlResourceParser.START_TAG:
                    if (parser.getName().equals(STICK_DIRECTION)) {
                        String stickPosStr = parser.getAttributeValue(null, "position");
                        currentStickPos = Integer.parseInt(stickPosStr);
                        currentButtonIndex = -1;
                    }
                    else if (BUTTON_LETTER_ORDER.contains(parser.getName())) {
                        currentButtonIndex = BUTTON_LETTER_ORDER.indexOf(parser.getName());
                        alt = parser.getAttributeValue(null, "alt");
                    }
                    else if (!parser.getName().equals(GAMEPAD_KEYBOARD)){
                        Log.w("loadXml", "Unexpected tag found: " + parser.getName());
                    }
                    break;

                case XmlResourceParser.TEXT:
                    if (currentStickPos > -1 && currentButtonIndex > -1){
                        keyMap[currentStickPos][currentButtonIndex][MAIN_KEY] = parser.getText().charAt(0);
                        keyMap[currentStickPos][currentButtonIndex][ALT_KEY] = alt == null ? NO_ALT : alt.charAt(0);
                    }
                    else {
                        Log.w("loadXml", String.format("Unexpected text found: %s", parser.getText()));
                    }
                    break;

                case XmlResourceParser.END_TAG:
                    if (parser.getName().equals(STICK_DIRECTION)) {
                        currentStickPos = -1;
                    }
                    else if (BUTTON_LETTER_ORDER.contains(parser.getName())) {
                        currentButtonIndex = -1;
                    }
                    else if (!parser.getName().equals(GAMEPAD_KEYBOARD)){
                        Log.w("loadXml", "Unexpected tag found: " + parser.getName());
                    }
                    break;
            }

            eventType = parser.next();
        }

        return keyMap;
    }

}

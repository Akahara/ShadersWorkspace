package wonder.shaderdisplay.serial;

import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

interface InputImageStream {

    // Does not create gl resources
    int[] getImageResolution() throws BadInitException;

    void startReading() throws BadInitException;

    Texture getTexture();

    void close();

}

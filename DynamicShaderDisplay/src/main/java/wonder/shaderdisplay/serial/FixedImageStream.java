package wonder.shaderdisplay.serial;

import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.entry.BadInitException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

class FixedImageStream implements InputImageStream {

    private final File file;
    private Texture texture;

    public FixedImageStream(File textureFile) {
        this.file = textureFile;
    }

    @Override
    public int[] getImageResolution() throws BadInitException {
        try {
            BufferedImage img = ImageIO.read(file);
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException e) {
            throw new BadInitException(e);
        }
    }

    @Override
    public void startReading() throws BadInitException {
        this.texture = Texture.loadTexture(file);
        if (texture == Texture.getMissingTexture())
            throw new BadInitException("Could not load texture from '" + file.getAbsolutePath() + "'");
    }

    @Override
    public void close() {
        texture.dispose();
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

}

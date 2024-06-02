package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.files.FilesUtils;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.display.TexturesSwapChain;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.uniforms.InputTextureUniform;
import wonder.shaderdisplay.uniforms.ResolutionUniform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class EntryImage extends SetupUtils {

    public static void run(Main.ImagePassOptions options, File fragment, File[] inputFiles) {
        Main.logger.info("-- Running image shader pass --");

        Display display;
        Scene scene;

        try {
            loadCommonOptions(options);

            if(inputFiles.length > 1 && !options.outputPath.contains("{}") && !new File(options.outputPath).isDirectory())
                throw new BadInitException("Output path is not a directory and multiple input files given, use -o <directory>");

            display = createDisplay(options.displayOptions, false, false);
            scene = createScene(options.displayOptions, display.renderer, fragment);
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        for(File inputFile : inputFiles) {
            String outputPath = options.outputPath.replaceAll("\\{}", inputFile.getName());
            File outputFile = new File(outputPath);
            if(outputFile.isDirectory())
                outputFile = new File(outputFile, inputFile.getName());
            String imageFormat = FilesUtils.getFileExtension(outputFile).toUpperCase();
            if(!options.overwriteExistingFiles && outputFile.exists()) {
                Main.logger.warn("File '" + outputFile.getPath() + "' already exists, add -r to overwrite it");
                continue;
            }

            Texture inputTexture;
            try {
                inputTexture = new Texture(ImageIO.read(inputFile));
            } catch (IOException e) {
                Main.logger.err("Could not read file '" + inputFile.getPath() + "': " + e.getMessage());
                continue;
            }
            inputTexture.bind(InputTextureUniform.INPUT_TEXTURE_SLOT);

            int outputWidth = options.sizeToImage ? inputTexture.getWidth() : options.displayOptions.winWidth;
            int outputHeight = options.sizeToImage ? inputTexture.getHeight() : options.displayOptions.winHeight;
            TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(outputWidth, outputHeight);
            BufferedImage frameImage = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_3BYTE_BGR);
            int[] frameCpuBuffer = new int[outputWidth*outputHeight];
            ResolutionUniform.updateViewportSize(outputWidth, outputHeight);

            if(options.runFromFrame != Main.ImagePassOptions.NO_RUN_FROM_FRAME) {
                for(int i = options.runFromFrame; i < options.screenshotFrame; i++) {
                    Time.setFrame(i);
                    renderTargetsSwapChain.swap();
                    renderTargetsSwapChain.bind();
                    display.renderer.render(scene);
                }
            }
            Time.setFrame(options.screenshotFrame);

            renderTargetsSwapChain.swap();
            renderTargetsSwapChain.bind();
            display.renderer.render(scene);

            renderTargetsSwapChain.readColorAttachment(0, frameCpuBuffer, options.displayOptions.background);
            frameImage.setRGB(0, 0, outputWidth, outputHeight, frameCpuBuffer, outputWidth*(outputHeight-1), -outputWidth);

            try {
                ImageIO.write(frameImage, imageFormat, outputFile);
                Main.logger.info("Wrote '" + outputFile.getPath() + "'");
            } catch (IOException e) {
                Main.logger.err("Could not write file '" + outputFile.getPath() + "': " + e.getMessage());
            }

            inputTexture.dispose();
        }
    }
}

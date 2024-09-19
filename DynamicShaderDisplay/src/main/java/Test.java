import com.zakgof.velvetvideo.*;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class Test {

    private static final int FRAMERATE = 25;

    public static void main(String[] args) throws AWTException {
        File dest = new File("out.mp4");
        screenCapture(dest, 250);
    }

    private static void screenCapture(File dest, int frames) throws AWTException {

        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        IVelvetVideoLib lib = VelvetVideoLib.getInstance();

        IVideoEncoderBuilder encoderBuilder = lib.videoEncoder("libx264")
                .framerate(FRAMERATE)
                .dimensions(screenRect.width, screenRect.height)
                .bitrate(1000000);
        try (IMuxer muxer = lib.muxer("mp4").videoEncoder(encoderBuilder).build(dest)) {
            IVideoEncoderStream videoEncoder = muxer.videoEncoder(0);

            try (IDemuxer demuxer = lib.demuxer(new File("C:\\Users\\albin\\Dev\\ShadersWorkspace\\DynamicShaderDisplay\\screenCapture.mp4"))) {
                IVideoDecoderStream videoStream = demuxer.videoStream(0);
                IVideoFrame videoFrame;
                while ((videoFrame = videoStream.nextFrame()) != null) {
                    BufferedImage img = videoFrame.image();
                    System.out.println(img);
                    videoEncoder.encode(img);
                }
            }
        }
        System.out.println(dest);
    }
}

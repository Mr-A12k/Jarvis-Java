package com.jarvis.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * High-performance Screen Capture Service.
 * Captures user desktop screens via Java AWT Robot or PowerShell GDI fallback,
 * normalizes resolution (up to 1920x1080), compresses to high-clarity JPEG,
 * and encodes to compact Base64 for multimodal vision processing.
 */
@Service
public class ScreenCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ScreenCaptureService.class);

    private static final int TARGET_MAX_WIDTH = 1920;
    private static final int TARGET_MAX_HEIGHT = 1080;
    private static final float JPEG_QUALITY = 0.85f;

    public ScreenCaptureResult captureScreen() {
        // 1. Try Java AWT Robot capture
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                Robot robot = new Robot();
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Dimension screenSize = toolkit.getScreenSize();
                Rectangle screenRect = new Rectangle(screenSize);
                BufferedImage rawImage = robot.createScreenCapture(screenRect);
                if (rawImage != null) {
                    return compressAndEncode(rawImage, "host_os_robot");
                }
            }
        } catch (Throwable t) {
            log.debug("Java AWT Robot capture not available in this process context: {}", t.getMessage());
        }

        // 2. Try PowerShell GDI capture fallback
        try {
            ScreenCaptureResult psResult = captureViaPowerShell();
            if (psResult != null && psResult.isAvailable()) {
                return psResult;
            }
        } catch (Throwable t) {
            log.debug("PowerShell GDI capture fallback failed: {}", t.getMessage());
        }

        // 3. Graceful fallback when host display handle is unavailable (e.g. headless/service)
        return new ScreenCaptureResult("", "image/jpeg", 0, 0, 0, "unavailable", System.currentTimeMillis());
    }

    public boolean isHostCaptureSupported() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            return screens != null && screens.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private ScreenCaptureResult compressAndEncode(BufferedImage source, String sourceTag) {
        try {
            int originalWidth = source.getWidth();
            int originalHeight = source.getHeight();

            // Downscale if higher than 1080p
            BufferedImage outputImage = source;
            if (originalWidth > TARGET_MAX_WIDTH || originalHeight > TARGET_MAX_HEIGHT) {
                double scale = Math.min((double) TARGET_MAX_WIDTH / originalWidth, (double) TARGET_MAX_HEIGHT / originalHeight);
                int scaledWidth = (int) Math.round(originalWidth * scale);
                int scaledHeight = (int) Math.round(originalHeight * scale);

                BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
                g2d.dispose();
                outputImage = resized;
            } else if (source.getType() != BufferedImage.TYPE_INT_RGB) {
                // Ensure RGB format for JPEG encoding
                BufferedImage rgbImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = rgbImage.createGraphics();
                g2d.drawImage(source, 0, 0, null);
                g2d.dispose();
                outputImage = rgbImage;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                ImageIO.write(outputImage, "png", baos);
                byte[] bytes = baos.toByteArray();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                return new ScreenCaptureResult(base64, "image/png", outputImage.getWidth(), outputImage.getHeight(), bytes.length, sourceTag, System.currentTimeMillis());
            }

            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(outputImage, null, null), param);
            } finally {
                writer.dispose();
            }

            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return new ScreenCaptureResult(
                    base64,
                    "image/jpeg",
                    outputImage.getWidth(),
                    outputImage.getHeight(),
                    bytes.length,
                    sourceTag,
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.error("Failed to compress and encode screen capture: {}", e.getMessage(), e);
            return new ScreenCaptureResult("", "image/jpeg", 0, 0, 0, "unavailable", System.currentTimeMillis());
        }
    }

    private ScreenCaptureResult captureViaPowerShell() {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("jarvis_screen_", ".png");
            String tempPath = tempFile.toAbsolutePath().toString().replace("\\", "/");
            String psCommand = String.format(
                    "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; " +
                    "$b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds; " +
                    "$bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height; " +
                    "$g = [System.Drawing.Graphics]::FromImage($bmp); " +
                    "$g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size); " +
                    "$bmp.Save('%s', [System.Drawing.Imaging.ImageFormat]::Png); " +
                    "$g.Dispose(); $bmp.Dispose();",
                    tempPath
            );

            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psCommand
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && Files.exists(tempFile) && Files.size(tempFile) > 0) {
                BufferedImage img = ImageIO.read(tempFile.toFile());
                if (img != null) {
                    return compressAndEncode(img, "host_os_powershell");
                }
            }
        } catch (Exception e) {
            log.debug("PowerShell capture fallback exception: {}", e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}

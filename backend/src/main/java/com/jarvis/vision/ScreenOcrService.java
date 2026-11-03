package com.jarvis.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Optical text and error extractor for screenshots.
 * Extracts visible compiler traces, exceptions, terminal logs, and code lines
 * from screen captures to enable high-accuracy code debugging even prior to
 * downloading dedicated multi-gigabyte neural vision models.
 */
@Service
public class ScreenOcrService {

    private static final Logger log = LoggerFactory.getLogger(ScreenOcrService.class);

    public String extractTextFromImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }

        Path tempImage = null;
        try {
            tempImage = Files.createTempFile("jarvis_ocr_", ".png");
            Files.write(tempImage, imageBytes);
            String imagePath = tempImage.toAbsolutePath().toString().replace("\\", "/");

            String psScript = String.format(
                    "$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]; " +
                    "$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType = WindowsRuntime]; " +
                    "$null = [Windows.Storage.StorageFile, Windows.Foundation, ContentType = WindowsRuntime]; " +
                    "$fileTask = [Windows.Storage.StorageFile]::GetFileFromPathAsync('%s'); " +
                    "$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.IsGenericMethod } | Select-Object -First 1; " +
                    "$f = $asTask.MakeGenericMethod([Windows.Storage.StorageFile]).Invoke($null, @($fileTask)).GetAwaiter().GetResult(); " +
                    "$stream = $asTask.MakeGenericMethod([Windows.Storage.Streams.IRandomAccessStream]).Invoke($null, @($f.OpenAsync([Windows.Storage.FileAccessMode]::Read))).GetAwaiter().GetResult(); " +
                    "$decoder = $asTask.MakeGenericMethod([Windows.Graphics.Imaging.BitmapDecoder]).Invoke($null, @([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream))).GetAwaiter().GetResult(); " +
                    "$bmp = $asTask.MakeGenericMethod([Windows.Graphics.Imaging.SoftwareBitmap]).Invoke($null, @($decoder.GetSoftwareBitmapAsync())).GetAwaiter().GetResult(); " +
                    "$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages(); " +
                    "if ($engine) { " +
                    "    $res = $asTask.MakeGenericMethod([Windows.Media.Ocr.OcrResult]).Invoke($null, @($engine.RecognizeAsync($bmp))).GetAwaiter().GetResult(); " +
                    "    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " +
                    "    Write-Output $res.Text; " +
                    "} " +
                    "$stream.Dispose();",
                    imagePath
            );

            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start();

            boolean finished = process.waitFor(6, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!output.isBlank()) {
                    log.info("ScreenOcrService: Extracted {} characters of visible text from screenshot", output.length());
                    return output;
                }
            }
        } catch (Exception e) {
            log.debug("ScreenOcrService: OCR extraction not available or timed out: {}", e.getMessage());
        } finally {
            if (tempImage != null) {
                try {
                    Files.deleteIfExists(tempImage);
                } catch (Exception ignored) {}
            }
        }

        return "";
    }
}

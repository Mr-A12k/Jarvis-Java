/**
 * Screen capture utilities for Web Display Media, Clipboard paste, and File conversion.
 */

export async function captureDisplayMedia(): Promise<string> {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
    throw new Error("Screen capture API (getDisplayMedia) is not supported in this browser.");
  }

  // Request display media stream (monitor, window, or tab)
  const stream = await navigator.mediaDevices.getDisplayMedia({
    video: {
      displaySurface: "monitor",
    },
    audio: false,
  });

  try {
    const videoTrack = stream.getVideoTracks()[0];
    if (!videoTrack) {
      throw new Error("No video track received from display capture.");
    }

    // Create hidden video element to capture snapshot frame
    const video = document.createElement("video");
    video.srcObject = stream;
    video.playsInline = true;
    video.muted = true;

    await new Promise<void>((resolve, reject) => {
      video.onloadedmetadata = () => {
        video.play().then(resolve).catch(reject);
      };
      video.onerror = () => reject(new Error("Failed to load video stream frame."));
      setTimeout(() => reject(new Error("Screen capture preview timed out.")), 5000);
    });

    // Wait a brief tick for the frame to render
    await new Promise((r) => setTimeout(r, 100));

    const width = video.videoWidth || 1920;
    const height = video.videoHeight || 1080;

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;

    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Could not initialize 2D canvas context.");

    ctx.drawImage(video, 0, 0, width, height);
    const dataUrl = canvas.toDataURL("image/jpeg", 0.88);
    return dataUrl;
  } finally {
    // Always stop and release all video tracks
    stream.getTracks().forEach((track) => track.stop());
  }
}

export function fileToBase64(file: File | Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        resolve(reader.result);
      } else {
        reject(new Error("Failed to read file as base64 string."));
      }
    };
    reader.onerror = () => reject(reader.error || new Error("Failed to read file."));
    reader.readAsDataURL(file);
  });
}

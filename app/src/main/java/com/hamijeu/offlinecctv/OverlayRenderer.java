package com.hamijeu.offlinecctv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

final class OverlayRenderer {
    private static final int JPEG_QUALITY = 88;

    private OverlayRenderer() {
    }

    static byte[] renderTimestamp(byte[] jpegBytes, Instant capturedAt) {
        Bitmap decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        if (decoded == null) {
            return jpegBytes;
        }

        Bitmap bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true);
        if (bitmap == null) {
            decoded.recycle();
            return jpegBytes;
        }

        drawTimestamp(bitmap, OverlayText.timestamp(capturedAt));

        ByteArrayOutputStream output = new ByteArrayOutputStream(jpegBytes.length);
        boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output);
        decoded.recycle();
        bitmap.recycle();
        return compressed ? output.toByteArray() : jpegBytes;
    }

    private static void drawTimestamp(Bitmap bitmap, String timestamp) {
        Canvas canvas = new Canvas(bitmap);
        float textSize = Math.max(22f, bitmap.getWidth() / 34f);
        float padding = textSize * 0.45f;
        float margin = textSize * 0.55f;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(timestamp);
        float textHeight = metrics.descent - metrics.ascent;
        float boxLeft = margin;
        float boxTop = margin;
        float boxRight = boxLeft + textWidth + padding * 2f;
        float boxBottom = boxTop + textHeight + padding * 2f;

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(0x99000000);
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, backgroundPaint);
        canvas.drawText(timestamp, boxLeft + padding, boxTop + padding - metrics.ascent, textPaint);
    }
}

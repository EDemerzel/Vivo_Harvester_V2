/*
 * ******************************************************************************
 *   Copyright (c) 2019 Symplectic. All rights reserved.
 *   This Source Code Form is subject to the terms of the Mozilla Public
 *   License, v. 2.0. If a copy of the MPL was not distributed with this
 *   file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ******************************************************************************
 *   Version :  ${git.branch}:${git.commit.id}
 * ******************************************************************************
 */
package uk.co.symplectic.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class of simply image manipulation utilities (read, write and resizing tools)
 */
public final class ImageUtils {

    public enum PhotoType{
        NONE,
        ORIGINAL,
        PROFILE,
        THUMBNAIL
    }

    private ImageUtils() {}

    public static BufferedImage readFile(InputStream inputStream) throws IOException {
        try {
            BufferedImage image = ImageIO.read(inputStream);
            // Strip any alpha channel from the image
            BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            newImage.createGraphics().drawImage(image, 0, 0, Color.BLACK, null);

            return newImage;
        } finally {
            inputStream.close();
        }
    }

    public static boolean writeFile(BufferedImage image, File outputFile, String format) {
        try {
            ImageIO.write(image, format, outputFile);
            return true;
        } catch (IOException ignored) {
        }

        return false;
    }

    @SuppressWarnings("unused")
    public static BufferedImage cropToSquare(BufferedImage inputImage){
        int height = inputImage.getHeight();
        int width = inputImage.getWidth();

        if(height == width) return inputImage;

        int size = Math.min(height, width);
        int offset = Math.abs(height-width)/2;

        int offsetX = height > width ? 0 : offset;
        int offsetY = height > width ? offset : 0;

        return inputImage.getSubimage(offsetX, offsetY, size, size);
    }


    /**
     * Convenience method that returns a scaled instance of the
     * provided {@code BufferedImage}.
     *
     * @param img the original image to be scaled
     * @param targetWidth the desired width of the scaled instance,
     *    in pixels
     * @param targetHeight the desired height of the scaled instance,
     *    in pixels
     * @param higherQuality if true, this method will use a multi-step
     *    scaling technique that provides higher quality than the usual
     *    one-step technique (only useful in downscaling cases, where
     *    {@code targetWidth} or {@code targetHeight} is
     *    smaller than the original dimensions, and generally only when
     *    the {@code BILINEAR} hint is specified)
     * @return a scaled version of the original {@code BufferedImage}
     */
    public static BufferedImage getScaledInstance(BufferedImage img,
                                           int targetWidth,
                                           int targetHeight,
                                           boolean higherQuality)
    {
        Object hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        int currentWidth, currentHeight;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            currentWidth = img.getWidth();
            currentHeight = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            currentWidth = targetWidth;
            currentHeight = targetHeight;
        }

        do {
            if (higherQuality && currentWidth > targetWidth) {
                currentWidth /= 2;
                if (currentWidth < targetWidth) {
                    currentWidth = targetWidth;
                }
            } else if (currentWidth < targetWidth) {
                currentWidth = targetWidth;
            }

            if (higherQuality && currentHeight > targetHeight) {
                currentHeight /= 2;
                if (currentHeight < targetHeight) {
                    currentHeight = targetHeight;
                }
            } else if (currentHeight < targetHeight) {
                currentHeight = targetHeight;
            }

            BufferedImage tmp = new BufferedImage(currentWidth, currentHeight, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(ret, 0, 0, currentWidth, currentHeight, null);
            g2.dispose();

            ret = tmp;
        } while (currentWidth != targetWidth || currentHeight != targetHeight);

        return ret;
    }

    public static int getTargetHeight(int currentWidth, int currentHeight, int targetWidth) {
        return calculateSecondaryDimension(currentWidth, currentHeight, targetWidth);
    }

    @SuppressWarnings("unused")
    public static int getTargetWidth(int currentWidth, int currentHeight, int targetHeight) {
        return calculateSecondaryDimension(currentHeight, currentWidth, targetHeight);
    }

    private static int calculateSecondaryDimension(int dimension1, int dimension2, int targetDimension1) {
        if (dimension1 != targetDimension1) {
            return (targetDimension1 * dimension2) / dimension1;
        }
        return dimension2;
    }
}

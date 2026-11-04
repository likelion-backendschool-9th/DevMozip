package com.devmozip.backend.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class ProfileImageTestFiles {
    private ProfileImageTestFiles() {}
    public static byte[] png() throws Exception { return image("png"); }
    public static byte[] jpeg() throws Exception { return image("jpeg"); }
    private static byte[] image(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
    public static byte[] webp() {
        return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    }
}

package fr.bamlab.rnimageresizer;

import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.media.ExifInterface;
import android.database.Cursor;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by almouro on 11/19/15.
 */
class ImageResizer {

    private static Bitmap rotateExif(String src, Bitmap bitmap) {
        try {
            ExifInterface ei = new ExifInterface(src);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            if (orientation == 1) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90);
                    break;
                default:
                    return bitmap;
            }

            try {
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return oriented;
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    private static String getRealPathFromURI(Uri contentURI, Context context) {
      String result;
      Cursor cursor = context.getContentResolver().query(contentURI, null, null, null, null);
      if (cursor == null) { // Source is Dropbox or other similar local file path
          result = contentURI.getPath();
      } else { 
          cursor.moveToFirst(); 
          int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA); 
          result = cursor.getString(idx);
          cursor.close();
      }
      return result;
    }

    private static Bitmap resizeImage(String imagePath, int maxWidth, int maxHeight, Context context) {
        try {
            Bitmap image;
            if (!imagePath.startsWith("content://") && !imagePath.startsWith("file://")) {
                image = BitmapFactory.decodeFile(imagePath);
            } else {
                ContentResolver cr = context.getContentResolver();
                Uri url = Uri.parse(imagePath);
                InputStream input = cr.openInputStream(url);
                image = BitmapFactory.decodeStream(input);
                image = rotateExif(RealPathUtil.getRealPathFromURI(context, url), image);
                input.close();
            }

            if (image == null) {
                return null; // Can't load the image from the given path.
            }

            if (maxHeight > 0 && maxWidth > 0) {
                float width = image.getWidth();
                float height = image.getHeight();

                float ratio = Math.min((float)maxWidth / width, (float)maxHeight / height);

                int finalWidth = (int) (width * ratio);
                int finalHeight = (int) (height * ratio);
                image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            }

            return image;
        }catch (IOException ex) {
             // No memory available for resizing.
        }

        return null;
    }

    public static Bitmap rotateImage(Bitmap b, float degrees)
    {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees,
                    (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // No memory available for rotating. Return the original bitmap.
            }
        }
        return b;
    }

    private static String saveImage(Bitmap bitmap, File saveDirectory, String fileName,
                                    Bitmap.CompressFormat compressFormat, int quality)
            throws IOException {
        if (bitmap == null) {
            throw new IOException("The bitmap couldn't be resized");
        }

        File newFile = new File(saveDirectory, fileName + "." + compressFormat.name());
        if(!newFile.createNewFile()) {
            throw new IOException("The file already exists");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, quality, outputStream);
        byte[] bitmapData = outputStream.toByteArray();

        outputStream.flush();
        outputStream.close();

        FileOutputStream fos = new FileOutputStream(newFile);
        fos.write(bitmapData);
        fos.flush();
        fos.close();

        return newFile.getAbsolutePath();
    }

    public static String createResizedImage(Context context, String imagePath, int newWidth,
                                            int newHeight, Bitmap.CompressFormat compressFormat,
                                            int quality, int rotation, String outputPath) throws IOException  {

        Bitmap resizedImage = ImageResizer.rotateImage(ImageResizer.resizeImage(imagePath, newWidth, newHeight, context), rotation);

        File path = context.getCacheDir();
        //if (outputPath != null || !outputPath.isEmpty()) {
          //path = new File(outputPath);
        //}

        return ImageResizer.saveImage(resizedImage, path,
                Long.toString(new Date().getTime()), compressFormat, quality);
    }
}

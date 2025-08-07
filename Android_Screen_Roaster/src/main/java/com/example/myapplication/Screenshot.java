package com.example.myapplication;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Screenshot {
    private String filePath;
    private String fileName;
    private long dateCreated;
    private long fileSize;
    private int width;
    private int height;

    public Screenshot(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        this.fileName = file.getName();
        this.dateCreated = file.lastModified();
        this.fileSize = file.length();
        
        // Extract dimensions from file if needed (for now, we'll use default values)
        this.width = 1080;
        this.height = 2400;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
        return sdf.format(new Date(dateCreated));
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", fileSize / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getDimensions() {
        return width + "x" + height;
    }

    public String getSizeInfo() {
        return getDimensions() + " • " + getFormattedSize();
    }
}
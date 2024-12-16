package com.nhnacademy.hexafileupload.tool;

public class ImageNameSeperator {


    private String filename;

    private String fileExtension;

    public ImageNameSeperator(String filename) {

        String [] fileNameInfo = filename.split("\\.");
        this.filename = fileNameInfo[0];
        this.fileExtension = "."+fileNameInfo[1];
    }

    public String FullName(){
        return filename + fileExtension ;
    }

    public String getFilename() {
        return filename;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }
}

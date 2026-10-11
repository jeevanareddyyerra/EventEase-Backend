package com.eventease.dto.response;

public class ImageUploadResponse {

    private String imageUrl;
    private String publicId;

    public ImageUploadResponse() {
    }

    public ImageUploadResponse(String imageUrl, String publicId) {
        this.imageUrl = imageUrl;
        this.publicId = publicId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }
}

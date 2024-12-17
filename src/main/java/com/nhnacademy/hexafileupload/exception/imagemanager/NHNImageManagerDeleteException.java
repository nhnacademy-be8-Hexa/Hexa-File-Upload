package com.nhnacademy.hexafileupload.exception.imagemanager;

// nhn image manager 에 이미지 삭제 요청을 했지만 실패
public class NHNImageManagerDeleteException extends RuntimeException {
    public NHNImageManagerDeleteException(String message) {
        super(message);
    }
}

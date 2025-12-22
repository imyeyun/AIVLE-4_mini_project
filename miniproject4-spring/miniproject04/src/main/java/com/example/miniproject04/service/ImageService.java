package com.example.miniproject04.service;

import com.example.miniproject04.Entity.Book;
import com.example.miniproject04.Entity.GeneratedImage;
import com.example.miniproject04.repository.BookRepository;
import com.example.miniproject04.repository.GeneratedImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final GeneratedImageRepository imageRepository;
    private final BookRepository bookRepository;

    // 실제 이미지 저장 경로
    private static final String IMAGE_SAVE_DIR = "C:/images";

    /**
     * =======================================================
     * 1. 이미지 등록 (이미지 저장 후 URL 반환)
     * =======================================================
     */
    @Transactional
    public String createImage(String tempUrl, Long bookId, String baseUrl) {

        // tempUrl 예: "/images/temp_xxx.png"
        String finalUrl;

        if (tempUrl.startsWith("http")) {
            finalUrl = tempUrl;
        } else {
            finalUrl = baseUrl + tempUrl; // ★ 여기서 IP 포함
        }

        GeneratedImage image = new GeneratedImage();
        image.setBook(bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("책 없음")));
        image.setImageUrl(finalUrl);

        imageRepository.save(image);

        return finalUrl;
    }


    /**
     * =======================================================
     * 2. 이미지 조회
     * =======================================================
     */
    @Transactional(readOnly = true)
    public GeneratedImage getImage(Long bookId) {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("삭제된 목록입니다."));

        GeneratedImage img = imageRepository.findByBook(book);

        if (img == null) {
            throw new IllegalArgumentException("삭제된 목록입니다.");
        }

        return img;
    }

    /**
     * =======================================================
     * 3. 이미지 수정 (새 이미지 덮어쓰기)
     * =======================================================
     */
    @Transactional
    public String updateImage(Long bookId, String tempUrl, Long userId) {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("삭제된 목록입니다."));

        // 권한 확인
        if (!book.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한 없음");
        }

        GeneratedImage img = imageRepository.findByBook(book);

        if (img == null) {
            throw new IllegalArgumentException("삭제된 목록입니다.");
        }

        // 새 이미지 저장
        String newImageUrl = downloadImageToLocal(tempUrl, bookId);

        img.setImageUrl(newImageUrl);
        imageRepository.save(img);

        return newImageUrl; // ⭐ 프론트로 반환할 상대URL
    }

    /**
     * =======================================================
     * tempUrl → 로컬 저장 후 접근 가능한 URL 반환
     * =======================================================
     */
    private String downloadImageToLocal(String tempUrl, Long bookId) {

        try {
            URL url = new URL(tempUrl);
            InputStream in = url.openStream();

            // 저장 폴더 생성
            Path saveDir = Paths.get(IMAGE_SAVE_DIR);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }

            // 파일명 생성
            String fileName = "book_" + bookId + "_" + UUID.randomUUID() + ".png";

            // 로컬 저장 경로
            Path destination = saveDir.resolve(fileName);

            // 이미지 저장
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);

            // ⭐ DB에는 상대URL만 저장
            return "/images/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("이미지 다운로드 실패: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteImageByBookId(Long bookId) {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("삭제된 목록입니다."));

        GeneratedImage img = imageRepository.findByBook(book);

        if (img == null) return; // 이미지 없으면 바로 종료

        // 1) 로컬 파일 삭제
        deleteLocalFile(img.getImageUrl());

        // 2) DB 삭제
        imageRepository.delete(img);
    }

    private void deleteLocalFile(String imageUrl) {
        try {
            // "/images/파일명.png" → 실제 저장 경로로 변환
            String fileName = imageUrl.replace("/images/", "");
            Path filePath = Paths.get(IMAGE_SAVE_DIR, fileName);

            Files.deleteIfExists(filePath);

        } catch (Exception e) {
            System.out.println("이미지 파일 삭제 실패: " + e.getMessage());
        }
    }
}

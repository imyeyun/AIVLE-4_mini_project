package com.example.miniproject04.controller;

import com.example.miniproject04.Entity.GeneratedImage;
import com.example.miniproject04.service.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/image")
@CrossOrigin(origins = "*")
public class ImageController {

    private final ImageService imageService;


    // ✅ 요청이 들어온 주소(배포 IP / ALB DNS)를 그대로 baseUrl로 만든다
    private String resolveBaseUrl(HttpServletRequest request) {
        // ALB 같은 프록시 뒤에서 https 처리 시 필요
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = request.getScheme();

        // ALB가 Host를 바꿀 수 있어서 우선순위로 읽음
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");

        return proto + "://" + host; // 예: http://3.34.253.177:8080  또는 http://xxx.elb.amazonaws.com
    }

    @PostMapping
    public ResponseEntity<?> createImage(
            @RequestBody Map<String, Object> req,
            HttpServletRequest request) {

        String tempUrl = (String) req.get("image_url");
        Long bookId = Long.valueOf(req.get("book_id").toString());

        String baseUrl = resolveBaseUrl(request); // ★ 여기 중요
        String fullImageUrl = imageService.createImage(tempUrl, bookId, baseUrl);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "image_url", fullImageUrl
        ));
    }


    @PostMapping("/check")
    public ResponseEntity<?> getImage(@RequestBody Map<String, Object> req,
                                      HttpServletRequest request) {
        try {
            Long bookId = Long.valueOf(req.get("book_id").toString());
            GeneratedImage img = imageService.getImage(bookId);

            String imageUrl = img.getImageUrl(); // DB 값

            // DB 값이 이미 절대 URL이면 그대로 반환 (중복 방지)
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                return ResponseEntity.ok(Map.of("power", "이용자", "image_url", imageUrl));
            }

            // 상대경로면 baseUrl 붙여서 반환
            String fullUrl = resolveBaseUrl(request) + imageUrl;
            return ResponseEntity.ok(Map.of("power", "이용자", "image_url", fullUrl));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(
                    Map.of("status", "error", "message", e.getMessage())
            );
        }
    }


    @PutMapping("/put")
    public ResponseEntity<?> updateImage(@RequestBody Map<String, Object> req,
                                         HttpServletRequest request) {
        try {
            Long bookId = Long.valueOf(req.get("book_id").toString());
            Long userId = Long.valueOf(req.get("user_id").toString());
            String tempUrl = (String) req.get("image_url");

            String updatedUrl = imageService.updateImage(bookId, tempUrl, userId);
            String fullUrl = resolveBaseUrl(request) + updatedUrl;

            return ResponseEntity.ok(Map.of("status", "success", "image_url", fullUrl));
        } catch (IllegalArgumentException e) {
            if ("권한 없음".equals(e.getMessage())) {
                return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
            }
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}

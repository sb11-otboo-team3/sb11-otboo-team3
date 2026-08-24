package com.otboo.global.infrastructure.storage;

import com.otboo.global.infrastructure.storage.exception.ThumbnailGenerationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailGenerator {

  private static final int THUMBNAIL_SIZE = 200;

  // 목록 화면 등에서 사용할 정사각형 썸네일을 생성한다. 원본 비율과
  // 무관하게 200x200으로 크롭+리사이징한다. (#186)
  public byte[] generate(byte[] originalBytes, ImageContentType contentType) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      Thumbnails.of(new ByteArrayInputStream(originalBytes))
          .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
          .crop(net.coobird.thumbnailator.geometry.Positions.CENTER)
          .outputFormat(contentType.getExtension())
          .toOutputStream(outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new ThumbnailGenerationException(e);
    }
  }
}
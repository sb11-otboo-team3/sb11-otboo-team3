package com.otboo.global.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThumbnailGeneratorTest {

  private final ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator();

  @Test
  @DisplayName("원본 이미지를 200x200 썸네일로 리사이징한다")
  void generateResizesImageTo200x200() throws Exception {
    // given
    byte[] originalBytes = createTestImage(800, 600);

    // when
    byte[] thumbnailBytes = thumbnailGenerator.generate(originalBytes, ImageContentType.JPEG);

    // then
    BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));
    assertThat(thumbnail.getWidth()).isEqualTo(200);
    assertThat(thumbnail.getHeight()).isEqualTo(200);
  }

  @Test
  @DisplayName("가로세로 비율이 다른 이미지도 정사각형으로 크롭된다")
  void generateCropsNonSquareImageToSquare() throws Exception {
    // given: 세로로 긴 이미지
    byte[] originalBytes = createTestImage(300, 900);

    // when
    byte[] thumbnailBytes = thumbnailGenerator.generate(originalBytes, ImageContentType.PNG);

    // then
    BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));
    assertThat(thumbnail.getWidth()).isEqualTo(thumbnail.getHeight());
  }

  private byte[] createTestImage(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ImageIO.write(image, "png", outputStream);
    return outputStream.toByteArray();
  }
}
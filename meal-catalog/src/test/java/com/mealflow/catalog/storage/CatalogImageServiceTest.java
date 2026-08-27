package com.mealflow.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mealflow.catalog.api.ImageUploadView;
import com.mealflow.common.exception.BizException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class CatalogImageServiceTest {
  @TempDir
  private Path tempDir;

  @Test
  void uploadsImageToLocalStorageAndLoadsResource() throws Exception {
    ObjectStorageProperties properties = localProperties();
    CatalogImageService service = new CatalogImageService(properties, new LocalObjectStorageService(properties));
    MockMultipartFile file = new MockMultipartFile("file", "meal.png", "image/png",
        new byte[] {(byte) 137, 80, 78, 71, 0x0D, 0x0A, 0x1A, 0x0A});

    ImageUploadView uploaded = service.upload(10L, file);
    Resource resource = service.load(uploaded.objectKey());

    assertThat(uploaded.provider()).isEqualTo("local");
    assertThat(uploaded.objectKey()).startsWith("m10-").endsWith(".png");
    assertThat(uploaded.url()).isEqualTo("/catalog/images/" + uploaded.objectKey());
    assertThat(uploaded.size()).isEqualTo(file.getSize());
    assertThat(uploaded.contentType()).isEqualTo("image/png");
    assertThat(resource.exists()).isTrue();
    assertThat(resource.contentLength()).isEqualTo(file.getSize());
  }

  @Test
  void rejectsUnsupportedContentType() {
    ObjectStorageProperties properties = localProperties();
    CatalogImageService service = new CatalogImageService(properties, new LocalObjectStorageService(properties));
    MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

    assertThatThrownBy(() -> service.upload(10L, file))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("Only jpeg, png, webp and gif images are allowed");
  }

  @Test
  void rejectsSpoofedContentTypeWithNonImageMagic() {
    ObjectStorageProperties properties = localProperties();
    CatalogImageService service = new CatalogImageService(properties, new LocalObjectStorageService(properties));
    // Declares image/png but the bytes are a shell script signature; must be rejected by magic sniffing.
    MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png",
        "#! /bin/bash\ncurl evil".getBytes());

    assertThatThrownBy(() -> service.upload(10L, file))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("Image content does not match an allowed format");
  }

  @Test
  void rejectsOversizedImage() {
    ObjectStorageProperties properties = localProperties();
    properties.setMaxSizeBytes(2L);
    CatalogImageService service = new CatalogImageService(properties, new LocalObjectStorageService(properties));
    MockMultipartFile file = new MockMultipartFile("file", "meal.jpg", "image/jpeg", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.upload(10L, file))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("Image file is too large");
  }

  private ObjectStorageProperties localProperties() {
    ObjectStorageProperties properties = new ObjectStorageProperties();
    properties.setProvider("local");
    properties.setLocalDir(tempDir.toString());
    return properties;
  }
}

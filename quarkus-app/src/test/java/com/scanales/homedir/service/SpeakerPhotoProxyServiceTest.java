package com.scanales.homedir.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies Google Drive view URLs are converted to direct download URLs (issue #1341). */
public class SpeakerPhotoProxyServiceTest {

  @Test
  public void driveViewUrlIsConvertedToDirectImageUrl() {
    String input =
        "https://drive.google.com/file/d/1tN8a7-FsS04nhrJUBDNudDwEuejRmlDu/view?usp=sharing";
    String expected =
        "https://drive.google.com/uc?export=view&id=1tN8a7-FsS04nhrJUBDNudDwEuejRmlDu";
    assertEquals(expected, SpeakerPhotoProxyService.toDirectImageUrl(input));
  }

  @Test
  public void driveViewUrlWithoutQueryIsConverted() {
    String input = "https://drive.google.com/file/d/abc123DEF/view";
    String expected = "https://drive.google.com/uc?export=view&id=abc123DEF";
    assertEquals(expected, SpeakerPhotoProxyService.toDirectImageUrl(input));
  }

  @Test
  public void driveOpenUrlIsConverted() {
    String input = "https://drive.google.com/file/d/xYz456_QWE/edit";
    String expected = "https://drive.google.com/uc?export=view&id=xYz456_QWE";
    assertEquals(expected, SpeakerPhotoProxyService.toDirectImageUrl(input));
  }

  @Test
  public void nonDriveUrlIsUnchanged() {
    String input = "https://avatars.githubusercontent.com/u/12345?v=4";
    assertEquals(input, SpeakerPhotoProxyService.toDirectImageUrl(input));
  }

  @Test
  public void nullUrlIsNull() {
    assertNull(SpeakerPhotoProxyService.toDirectImageUrl(null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://drive.google.com/uc?export=view&id=abc",
        "https://gravatar.com/avatar/123",
        "https://i.imgur.com/xyz.png",
      })
  public void allowedUrlsRemainUnchanged(String url) {
    assertEquals(url, SpeakerPhotoProxyService.toDirectImageUrl(url));
  }
}

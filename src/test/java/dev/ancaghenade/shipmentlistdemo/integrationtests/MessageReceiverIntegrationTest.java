package dev.ancaghenade.shipmentlistdemo.integrationtests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageReceiverIntegrationTest extends LocalStackSetupConfigurations {

  @BeforeAll
  public static void setup() {
    LocalStackSetupConfigurations.setupConfig();

    localStack.followOutput(logConsumer);

  }

  @Test
  void testSNSSQSMessageReceiver() {
    var imageData = new byte[0];
    try {
      imageData = Files.readAllBytes(Path.of("src/test/java/resources/cat.jpg"));
    } catch (IOException e) {
      e.printStackTrace();
    }
    var resource = new ByteArrayResource(imageData) {
      @Override
      public String getFilename() {
        return "cat.jpg";
      }
    };

    var shipmentId = "dc3b6668-45ba-4c10-9860-95bbffaebfc1";
    // build the URL with the id as a path variable
    var url = "/api/shipment/" + shipmentId + "/image/upload";
    // set the request headers
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    // request body with the file resource and headers
    MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
    requestBody.add("file", resource);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody,
        headers);

    ResponseEntity<String> responseEntity = restTemplate.exchange(BASE_URL + url,
        HttpMethod.POST, requestEntity, String.class);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());

    // give the Lambda time to start up and process the image + send the message to SQS
    try {
      Thread.sleep(5000);

    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    var sseUrl = "/push-endpoint";

    ResponseEntity<String> sseEndpointResponse = restTemplate.getForEntity(BASE_URL + sseUrl,
        String.class);
    assertEquals(HttpStatus.OK, sseEndpointResponse.getStatusCode());
    assertNotNull(sseEndpointResponse.getBody());
    assertTrue(sseEndpointResponse.getBody().contains(shipmentId));

  }

}

package dev.ancaghenade.shipmentlistdemo.integrationtests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaIntegrationTest extends LocalStackSetupConfigurations {

  @BeforeAll
  public static void setup()  {
    LocalStackSetupConfigurations.setupConfig();
    localStack.followOutput(logConsumer);

  }

  @Test
  @Order(1)
  void testFileAddWatermarkInLambda() {

    // prepare the file to upload
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

    var originalHash = applyHash(imageData);

    var shipmentId = "f7fc2d00-5cd9-4749-b5ac-10a6f7ac0310";
    // build the URL with the id as a path variable
    var postUrl = "/api/shipment/" + shipmentId + "/image/upload";
    var getUrl = "/api/shipment/" + shipmentId + "/image/download";

    // set the request headers
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    // request body with the file resource and headers
    MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
    requestBody.add("file", resource);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody,
        headers);

    ResponseEntity<String> postResponse = restTemplate.exchange(BASE_URL + postUrl,
        HttpMethod.POST, requestEntity, String.class);

    assertEquals(HttpStatus.OK, postResponse.getStatusCode());

    // give the Lambda time to start up and process the image
    try {
      Thread.sleep(15000);

    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    ResponseEntity<byte[]> responseEntity = restTemplate.exchange(BASE_URL + getUrl,
        HttpMethod.GET, null, byte[].class);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());

    var watermarkHash = applyHash(responseEntity.getBody());

    assertNotEquals(originalHash, watermarkHash);

  }

  @Test
  @Order(2)
  void testFileProcessedInLambdaHasMetadata() {
    var getItemRequest = GetItemRequest.builder()
        .tableName("shipment")
        .key(Map.of(
            "shipmentId",
            AttributeValue.builder().s("f7fc2d00-5cd9-4749-b5ac-10a6f7ac0310").build())).build();

    var getItemResponse = dynamoDbClient.getItem(getItemRequest);

    dynamoDbClient.getItem(getItemRequest);
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(BUCKET_NAME)
        .key(getItemResponse.item().get("imageLink").s())
        .build();
    try {
      // already processed objects have a metadata field added, not be processed again
      var s3ObjectResponse = s3Client.getObject(getObjectRequest);
      assertTrue(s3ObjectResponse.response().metadata().entrySet().stream().anyMatch(
          entry -> entry.getKey().equals("skip-processing") && entry.getValue().equals("true")));
    } catch (NoSuchKeyException noSuchKeyException) {
      noSuchKeyException.printStackTrace();
    }
    dynamoDbClient.close();
    s3Client.close();


  }

  private String applyHash(byte[] data) {
    String hashValue = null;
    try {
      var digest = MessageDigest.getInstance("SHA-256");

      // get the hash of the byte array
      var hash = digest.digest(data);

      // convert the hash bytes to a hexadecimal representation
      var hexString = new StringBuilder();
      for (byte b : hash) {
        var hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      hashValue = hexString.toString();
      System.out.println("Hash value: " + hashValue);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return hashValue;
  }

}

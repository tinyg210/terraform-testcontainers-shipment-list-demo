package dev.ancaghenade.shipmentlistdemo.integrationtests;

import dev.ancaghenade.shipmentlistdemo.entity.Shipment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentServiceIntegrationTest extends LocalStackSetupConfigurations {

    @BeforeAll
    public static void setup() {
        LocalStackSetupConfigurations.setupConfig();

        localStack.followOutput(logConsumer);

    }

    @Test
    @Order(1)
    void testFileUploadToS3() throws Exception {

        // Prepare the file to upload
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
        var execResult = executeInContainer(
                "awslocal s3api list-objects --bucket ancas-demo-bucket --query length(Contents[])");
        assertEquals(String.valueOf(1), execResult.getStdout().trim());
    }

    @Test
    @Order(2)
    void testFileDownloadFromS3() {

        var shipmentId = "dc3b6668-45ba-4c10-9860-95bbffaebfc1";
        // build the URL with the id as a path variable
        var url = "/api/shipment/" + shipmentId + "/image/download";

        ResponseEntity<byte[]> responseEntity = restTemplate.exchange(BASE_URL + url,
                HttpMethod.GET, null, byte[].class);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        // object is not empty
        assertNotNull(responseEntity.getBody());
    }

    @Test
    @Order(3)
    void testFileDownloadFromS3FailsOnWrongId() {

        var shipmentId = "dc3b6668-45ba-4c10-9860-95bbffawrong";
        // build the URL with the id as a path variable
        var url = "/api/shipment/" + shipmentId + "/image/download";
        ResponseEntity<byte[]> responseEntity = restTemplate.exchange(BASE_URL + url,
                HttpMethod.GET, null, byte[].class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
    }

    @Test
    @Order(4)
    void testGetShipmentFromDynamoDB() throws IOException {

        var url = "/api/shipment";
        // set the request headers
        ResponseEntity<List<Shipment>> responseEntity = restTemplate.exchange(BASE_URL + url,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            var json = new File("src/test/java/resources/shipment.json");
            var shipment = objectMapper.readValue(json, Shipment.class);
            List<Shipment> shipmentList = responseEntity.getBody();
            var shipmentWithoutLink = shipmentList.get(0);
            shipmentWithoutLink.setImageLink(null);
            assertEquals(shipment, shipmentWithoutLink);
        }
    }

    @Test
    @Order(5)
    void testAddShipmentToDynamoDB() throws IOException {

        var url = "/api/shipment";
        // set the request headers

        var json = new File("src/test/java/resources/shipmentToUpload.json");
        var shipment = objectMapper.readValue(json, Shipment.class);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE));

        HttpEntity<Shipment> requestEntity = new HttpEntity<>(shipment,
                headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(BASE_URL + url,
                HttpMethod.POST, requestEntity, String.class);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());

    }

    @Test
    @Order(6)
    void testGetTwoShipmentsFromDynamoDB() {

        var url = "/api/shipment";
        // set the request headers
        ResponseEntity<List<Shipment>> responseEntity = restTemplate.exchange(BASE_URL + url,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            List<Shipment> shipmentList = responseEntity.getBody();
            assertEquals(5, shipmentList.size());
        }
    }

    @Test
    @Order(7)
    void testDeleteShipmentFromDynamoDB() {

        var url = "/api/shipment/";
        var shipmentId = "dc3b6668-45ba-4c10-9860-95bbffaebfc1";

        // set the request headers
        ResponseEntity<String> deleteResponseEntity = restTemplate.exchange(BASE_URL + url + shipmentId,
                HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.OK, deleteResponseEntity.getStatusCode());
        assertEquals("Shipment has been deleted", deleteResponseEntity.getBody());

    }

}

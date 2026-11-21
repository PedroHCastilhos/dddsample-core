package se.citerus.dddsample.journey.changedestination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import se.citerus.dddsample.Application;

import java.io.IOException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {"spring.datasource.url=jdbc:hsqldb:mem:dddsample_changedestination_system_test"})
class ChangeDestinationSystemTest {

  private static final String CARGO = "ABC123";
  private static final String ORIGINAL_ETA = "2009-03-12T00:00:00Z";

  @LocalServerPort
  private int port;

  private final ObjectMapper mapper = new ObjectMapper();
  private RestTemplate http;

  @BeforeAll
  static void locale() {
    Locale.setDefault(Locale.US);
  }

  @BeforeEach
  void setUp() {
    http = new RestTemplate();
    // Never throw on 4xx/5xx: the tests assert on the status code themselves.
    http.setErrorHandler(new ResponseErrorHandler() {
      @Override
      public boolean hasError(ClientHttpResponse response) {
        return false;
      }

      @Override
      public void handleError(ClientHttpResponse response) throws IOException {
        // ignored on purpose
      }
    });
  }

  @AfterEach
  void restoreOriginalDestination() {
    postChangeDestination(CARGO, "FIHEL");
  }

  private String base() {
    return "http://localhost:" + port + "/dddsample";
  }

  private ResponseEntity<String> postChangeDestination(String trackingId, String unLocode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("trackingId", trackingId);
    form.add("unlocode", unLocode);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    return http.postForEntity(base() + "/admin/changeDestination", new HttpEntity<>(form, headers), String.class);
  }

  private JsonNode track(String trackingId) throws Exception {
    ResponseEntity<String> response = http.getForEntity(base() + "/api/track/" + trackingId, String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    return mapper.readTree(response.getBody());
  }

  @Test
  @DisplayName("ST-01: change destination of a routed cargo in transit => tracking shows new destination, no ETA, no next activity")
  void st01_changeDestinationOfCargoInTransit() throws Exception {
    JsonNode before = track(CARGO);
    assertThat(before.get("destination").asText()).isEqualTo("Helsinki");
    assertThat(before.get("eta").asText()).isEqualTo(ORIGINAL_ETA);

    ResponseEntity<String> response = postChangeDestination(CARGO, "SESTO");
    assertThat(response.getStatusCode().isError()).isFalse();

    JsonNode after = track(CARGO);
    assertThat(after.get("destination").asText()).isEqualTo("Stockholm");
    assertThat(after.get("eta").asText()).isEqualTo("Unknown");
    assertThat(after.get("nextExpectedActivity").asText()).isEmpty();
    assertThat(after.get("isMisdirected").asBoolean()).isFalse();
    assertThat(after.get("handlingEvents")).hasSize(3);
  }

  @Test
  @DisplayName("ST-02: changing the destination and changing back restores ETA and next activity")
  void st02_changeAndChangeBack() throws Exception {
    JsonNode original = track(CARGO);

    postChangeDestination(CARGO, "SESTO");
    assertThat(track(CARGO).get("eta").asText()).isEqualTo("Unknown");

    postChangeDestination(CARGO, "FIHEL");
    JsonNode restored = track(CARGO);

    assertThat(restored.get("destination").asText()).isEqualTo("Helsinki");
    assertThat(restored.get("eta").asText()).isEqualTo(original.get("eta").asText());
    assertThat(restored.get("nextExpectedActivity").asText()).isEqualTo(original.get("nextExpectedActivity").asText());
  }

  @Test
  @DisplayName("ST-03: unknown UN/LOCODE => client error, destination unchanged (known defect D4)")
  void st03_unknownUnLocode() throws Exception {
    ResponseEntity<String> response = postChangeDestination(CARGO, "ZZZZZ");

    assertThat(response.getStatusCode().is4xxClientError()).as("HTTP status %s", response.getStatusCode()).isTrue();
    assertThat(track(CARGO).get("destination").asText()).isEqualTo("Helsinki");
  }

  @Test
  @DisplayName("ST-04: destination equal to origin => client error, cargo unchanged (known defect D4)")
  void st04_destinationEqualToOrigin() throws Exception {
    ResponseEntity<String> response = postChangeDestination(CARGO, "CNHKG");

    assertThat(response.getStatusCode().is4xxClientError()).as("HTTP status %s", response.getStatusCode()).isTrue();
    assertThat(track(CARGO).get("destination").asText()).isEqualTo("Helsinki");
  }

  @Test
  @DisplayName("ST-05: unknown tracking id => client error (known defect D4)")
  void st05_unknownTrackingId() {
    ResponseEntity<String> response = postChangeDestination("NOSUCH", "SESTO");

    assertThat(response.getStatusCode().is4xxClientError()).as("HTTP status %s", response.getStatusCode()).isTrue();
  }
}

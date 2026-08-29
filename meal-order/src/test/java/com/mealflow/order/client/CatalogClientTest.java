package com.mealflow.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.config.HttpClientConfig;
import com.mealflow.order.config.ServiceEndpoints;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class CatalogClientTest {

  @Test
  void preservesCatalogBusinessErrorInsteadOfTurningItIntoSystemError() {
    RestTemplate restTemplate = new HttpClientConfig(new InternalAuthProperties()).restTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("http://catalog/catalog/internal/stocks/reserve"))
        .andExpect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":false,\"code\":\"STOCK_NOT_ENOUGH\",\"message\":\"库存不足\",\"data\":null}"));

    CatalogClient client = new CatalogClient(restTemplate,
        new ServiceEndpoints("http://catalog", "", "", "", "", ""));
    CatalogClient.ReserveStockRequest request = new CatalogClient.ReserveStockRequest(
        "stock-test", 101L, 10L, null, null, List.of(new OrderSkuItem(1L, 1)), LocalDateTime.now().plusMinutes(5));

    assertThatThrownBy(() -> client.reserve(request))
        .isInstanceOfSatisfying(BizException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.STOCK_NOT_ENOUGH));
    server.verify();
  }
}

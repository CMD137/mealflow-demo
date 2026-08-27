package com.mealflow.common.internal;

import com.mealflow.common.trace.TraceContext;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@code ClientHttpRequestInterceptor} that signs every request emitted by a shared
 * {@code RestTemplate} or {@code RestClient}. Internal clients only ever talk to peer business
 * services, so signing all requests is the simplest correct policy.
 */
public class InternalSigningInterceptor implements ClientHttpRequestInterceptor {

  private final InternalRequestSigner signer;

  public InternalSigningInterceptor(InternalRequestSigner signer) {
    this.signer = signer;
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {
    if (!signer.isConfigured()) {
      return execution.execute(request, body);
    }
    URI uri = request.getURI();
    String rawPath = uri.getRawPath() != null ? uri.getRawPath() : uri.getPath();
    String rawQuery = uri.getRawQuery();
    HttpRequest signed = new HttpRequest() {
      @Override
      public HttpMethod getMethod() {
        return request.getMethod();
      }

      @Override
      public URI getURI() {
        return request.getURI();
      }

      @Override
      public HttpHeaders getHeaders() {
        HttpHeaders headers = HttpHeaders.writableHttpHeaders(request.getHeaders());
        String traceId = TraceContext.current();
        if (traceId != null && !traceId.isBlank()) {
          headers.set(TraceContext.TRACE_ID_HEADER, traceId);
        }
        signer.sign(headers, request.getMethod() == null ? "GET" : request.getMethod().name(), rawPath, rawQuery);
        return headers;
      }
    };
    return execution.execute(signed, body);
  }
}

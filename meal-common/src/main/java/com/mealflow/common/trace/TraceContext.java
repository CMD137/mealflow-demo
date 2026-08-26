package com.mealflow.common.trace;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Trace context helpers shared by every service.
 *
 * <p>Each request carries an {@code X-Trace-Id} header generated or propagated by the gateway.
 * The id is placed into the SLF4J MDC ({@code traceId}) so that every log line produced while
 * handling the request can be correlated end-to-end (gateway -&gt; order -&gt; catalog / queue /
 * payment ...). The gateway and business services expose the same id in the response header.</p>
 */
public final class TraceContext {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String MDC_TRACE_ID = "traceId";

  private TraceContext() {
  }

  /** Starts a trace scope from an incoming header value; generates a new id when absent. */
  public static String start(String incomingTraceId) {
    String traceId = (incomingTraceId == null || incomingTraceId.isBlank())
        ? UUID.randomUUID().toString()
        : sanitize(incomingTraceId);
    MDC.put(MDC_TRACE_ID, traceId);
    return traceId;
  }

  /** Removes the trace scope from the current thread. */
  public static void clear() {
    MDC.remove(MDC_TRACE_ID);
  }

  public static String current() {
    return MDC.get(MDC_TRACE_ID);
  }

  private static String sanitize(String value) {
    String trimmed = value.trim();
    return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
  }
}

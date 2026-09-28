package ch.chillcode.sonar.quality.mutation.api;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.sonar.api.server.ws.Response;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.utils.text.XmlWriter;

/**
 * A response that records what was streamed into it: the status, the media type and the body. A
 * hand-written fake rather than a mock, so the suite needs no Mockito agent attached to the JVM.
 * Every method the web service does not stream through throws.
 */
final class RecordingResponse implements Response, Response.Stream {

  private final ByteArrayOutputStream body = new ByteArrayOutputStream();
  private Integer status;
  private String mediaType;

  Integer status() {
    return status;
  }

  String mediaType() {
    return mediaType;
  }

  String body() {
    return body.toString(StandardCharsets.UTF_8);
  }

  @Override
  public Stream stream() {
    return this;
  }

  @Override
  public Stream setMediaType(String mediaType) {
    this.mediaType = mediaType;
    return this;
  }

  @Override
  public Stream setStatus(int status) {
    this.status = status;
    return this;
  }

  @Override
  public OutputStream output() {
    return body;
  }

  @Override
  public JsonWriter newJsonWriter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public XmlWriter newXmlWriter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Response noContent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Response setHeader(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<String> getHeaderNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getHeader(String name) {
    throw new UnsupportedOperationException();
  }
}

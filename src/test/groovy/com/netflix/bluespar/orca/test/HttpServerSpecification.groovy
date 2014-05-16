package com.netflix.bluespar.orca.test

import com.google.common.base.Optional
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import spock.lang.Specification

import static com.google.common.net.HttpHeaders.CONTENT_TYPE
import static java.net.HttpURLConnection.HTTP_BAD_METHOD
import static java.util.Collections.EMPTY_MAP

/**
 * A base {@link Specification} class for specs that need to connect to a REST server or similar endpoint.
 *
 * Expectations for HTTP requests are set up using {@link HttpServerSpecification#expect}. You can set up multiple
 * expectations in a single specification method. Order is not enforced
 */
abstract class HttpServerSpecification extends Specification {

    private static final int SERVER_SHUTDOWN_TIMEOUT = 3
    private String baseURI
    private HttpServer server

    @CompileStatic
    def setup() {
        startServer()
    }

    @CompileStatic
    def cleanup() {
        server.stop SERVER_SHUTDOWN_TIMEOUT
    }

    /**
     * @return the URI of the root of the web server.
     */
    @CompileStatic
    protected final String getBaseURI() {
        baseURI
    }

    /**
     * @see #expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)
     */
    @CompileStatic
    protected final void expect(String method, String path, int status) {
        expect method, path, status, EMPTY_MAP, Optional.absent()
    }

    /**
     * @see #expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)
     */
    protected final void expect(String method, String path, int status, Closure content) {
        expect method, path, status, EMPTY_MAP, Optional.of(content)
    }

    /**
     * @see #expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)
     */
    @CompileStatic
    protected final void expect(String method, String path, int status, Map<String, String> headers) {
        expect method, path, status, headers, Optional.absent()
    }

    /**
     * @see #expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)
     */
    @CompileStatic
    protected final void expect(String method, String path, int status, Map<String, String> headers, Closure content) {
        expect method, path, status, headers, Optional.of(content) as Optional<Closure> // cast necessary due to GROOVY-6800
    }

    /**
     * Sets up an expectation for an HTTP request. If a request to {@code path} is made using the specified
     * {@code method} then the server will respond according to the parameters supplied to this method. If a
     * request is made to {@code path} using a different HTTP method the server will respond with
     * {@value HttpURLConnection#HTTP_BAD_METHOD}.
     *
     * @param method the HTTP method expected
     * @param path the literal path expected relative to the base URI of the server. Note this cannot use wildcards or any other clever things.
     * @param status the HTTP status of the response.
     * @param responseHeaders any HTTP responseHeaders that should be sent with the response.
     * @param content a <em>Closure</em> used to construct a response using {@link JsonBuilder}.
     */
    @CompileStatic
    protected final void expect(String method, String path, int status, Map<String, String> headers, Optional<Closure> content) {
        server.createContext path, { HttpExchange exchange ->
            if (exchange.requestMethod == method) {
                def response = ""
                if (content.present) {
                    def json = new JsonBuilder()
                    json(content.get())
                    response = json.toString()
                }
                exchange.with {
                    headers.each { key, value ->
                        responseHeaders.add(key, value)
                    }
                    sendResponseHeaders status, response.length()
                    if (response.length() > 0) {
                        responseHeaders.add(CONTENT_TYPE, "application/json")
                        responseBody.write response.bytes
                    }
                    close()
                }
            } else {
                exchange.with {
                    sendResponseHeaders HTTP_BAD_METHOD, 0
                    close()
                }
            }
        }
    }

    private void startServer() {
        def address = new InetSocketAddress(0)
        server = HttpServer.create(address, 0)
        server.with {
            executor = null
            start()
        }
        baseURI = "http://localhost:$server.address.port"
    }
}

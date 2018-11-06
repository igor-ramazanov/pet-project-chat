/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.extensions.jetty.auth.oauth2;

import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.util.Throwables;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Semaphore;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

/**
 * OAuth 2.0 verification code receiver that runs a Jetty server on a free port, waiting for a
 * redirect with the verification code.
 *
 * <p>
 * Implementation is thread-safe.
 * </p>
 *
 * @author Yaniv Inbar
 * @since 1.11
 */
public final class CustomLocalServerReceiver implements VerificationCodeReceiver {

    private static final String LOCALHOST = "localhost";

    private static final String ALL = "0.0.0.0";

    private static final String CALLBACK_PATH = "/Callback";

    /**
     * Server or {@code null} before {@link #getRedirectUri()}.
     */
    private Server server;

    /**
     * Verification code or {@code null} for none.
     */
    private String code;

    /**
     * Error code or {@code null} for none.
     */
    private String error;

    /**
     * To block until receiving an authorization response or stop() is called.
     */
    private final Semaphore waitUnlessSignaled = new Semaphore(0 /* initially zero permit */);

    /**
     * Port to use or {@code -1} to select an unused port in {@link #getRedirectUri()}.
     */
    private int port;

    /**
     * Host name to use.
     */
    private final String callbackHost;

    /**
     * Host name to bind.
     */
    private final String serverHost;

    /**
     * Callback path of redirect_uri
     */
    private final String callbackPath;

    /**
     * URL to an HTML page to be shown (via redirect) after successful login. If null, a canned
     * default landing page will be shown (via direct response).
     */
    private String successLandingPageUrl;

    /**
     * URL to an HTML page to be shown (via redirect) after failed login. If null, a canned
     * default landing page will be shown (via direct response).
     */
    private String failureLandingPageUrl;

    /**
     * Constructor that starts the server on {@link #LOCALHOST} and an unused port.
     *
     * <p>
     * Use {@link Builder} if you need to specify any of the optional parameters.
     * </p>
     */
    public CustomLocalServerReceiver() {
        this(LOCALHOST, ALL,-1, CALLBACK_PATH, null, null);
    }

    /**
     * Constructor.
     *
     * @param callbackHost Host name to use
     * @param port Port to use or {@code -1} to select an unused port
     */
    private CustomLocalServerReceiver(String callbackHost, String serverHost, int port, String callbackPath,
                                      String successLandingPageUrl, String failureLandingPageUrl) {
        this.callbackHost = callbackHost;
        this.serverHost = serverHost;
        this.port = port;
        this.callbackPath = callbackPath;
        this.successLandingPageUrl = successLandingPageUrl;
        this.failureLandingPageUrl = failureLandingPageUrl;
    }

    @Override
    public String getRedirectUri() throws IOException {
        server = new Server(port != -1 ? port : 0);
        Connector connector = server.getConnectors()[0];
        connector.setHost(getServerHost());
        server.addHandler(new CallbackHandler());
        try {
            server.start();
            port = connector.getLocalPort();
        } catch (Exception e) {
            Throwables.propagateIfPossible(e);
            throw new IOException(e);
        }
        return "http://" + getCallbackHost() + ":" + port + callbackPath;
    }

    @Override
    public String waitForCode() throws IOException {
        waitUnlessSignaled.acquireUninterruptibly();
        if (error != null) {
            throw new IOException("User authorization failed (" + error + ")");
        }
        return code;
    }

    @Override
    public void stop() throws IOException {
        waitUnlessSignaled.release();
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Throwables.propagateIfPossible(e);
                throw new IOException(e);
            }
            server = null;
        }
    }

    /**
     * Returns the callbackHost name to use.
     */
    private String getCallbackHost() {
        return callbackHost;
    }

    private String getServerHost() {
        return serverHost;
    }

    /**
     * Builder.
     *
     * <p>
     * Implementation is not thread-safe.
     * </p>
     */
    public static final class Builder {

        /**
         * Host name to use.
         */
        private String callbackHost = LOCALHOST;
        private String serverHost = "0.0.0.0";

        /**
         * Port to use or {@code -1} to select an unused port.
         */
        private int port = -1;

        private String successLandingPageUrl;
        private String failureLandingPageUrl;

        private String callbackPath = CALLBACK_PATH;

        /**
         * Builds the {@link CustomLocalServerReceiver}.
         */
        public CustomLocalServerReceiver build() {
            return new CustomLocalServerReceiver(callbackHost, serverHost, port, callbackPath,
                    successLandingPageUrl, failureLandingPageUrl);
        }

        /**
         * Sets the port to use or {@code -1} to select an unused port.
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

    }

    class CallbackHandler extends AbstractHandler {

        @Override
        public void handle(
                String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException {
            if (!callbackPath.equals(target)) {
                return;
            }

            try {
                ((Request) request).setHandled(true);
                error = request.getParameter("error");
                code = request.getParameter("code");

                if (error == null && successLandingPageUrl != null) {
                    response.sendRedirect(successLandingPageUrl);
                } else if (error != null && failureLandingPageUrl != null) {
                    response.sendRedirect(failureLandingPageUrl);
                } else {
                    writeLandingHtml(response);
                }
                response.flushBuffer();
            } finally {
                waitUnlessSignaled.release();
            }
        }

        private void writeLandingHtml(HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");

            PrintWriter doc = response.getWriter();
            doc.println("<html>");
            doc.println("<head><title>OAuth 2.0 Authentication Token Received</title></head>");
            doc.println("<body>");
            doc.println("Received verification code. You may now close this window.");
            doc.println("</body>");
            doc.println("</html>");
            doc.flush();
        }
    }
}

/*
 * ******************************************************************************
 *   Copyright (c) 2017 Symplectic. All rights reserved.
 *   This Source Code Form is subject to the terms of the Mozilla Public
 *   License, v. 2.0. If a copy of the MPL was not distributed with this
 *   file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ******************************************************************************
 */

package uk.co.symplectic.utils.http;

import org.apache.commons.lang.NullArgumentException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

/**
 * Simple HTTPClient class based on apache's http components and core.
 */
public class HttpClient {
    final private String username;
    final private String password;
    final private String url;

    private String getUsername(){return username;}
    private String getPassword(){return password;}
    private String getUrl(){return url;}

    private static final int defaultSoTimeout = 5 * 60 * 1000; // 5 minutes, in milliseconds
    private static final int defaultConnectionTimeout = 30000; // 30 seconds

    private static RequestConfig defaultRequestConfig;
    private static PoolingHttpClientConnectionManager connectionManager;

    public static synchronized void setRequestDelay(int millis) {
        intervalInMSecs = millis;
    }

    private static Date lastRequest = null;
    private static int intervalInMSecs = 250;

    static{
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);
        defaultRequestConfig = RequestConfig.custom().setConnectTimeout(defaultConnectionTimeout).setSocketTimeout(defaultSoTimeout).build();
    }

    public static synchronized void setSocketTimeout(int millis) {
        defaultRequestConfig = RequestConfig.copy(defaultRequestConfig).setSocketTimeout(millis).build();
    }

    public static synchronized void ignoreSslErrors() {
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                builder.build(), new NoopHostnameVerifier());

            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory> create()
                            .register("https", sslsf)
                            .register("http", new PlainConnectionSocketFactory()).build();

            connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(20);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    private static synchronized PoolingHttpClientConnectionManager getConnectionManager(){return connectionManager;}
    private static synchronized RequestConfig getDefaultRequestConfig(){return defaultRequestConfig;}

    /**
     * Delay method - ensure that requests are not sent too frequently to the Elements API,
     * by calling this method prior to executing the HttpClient request.
     */
    private static synchronized void regulateRequestFrequency() {
        try {
            if (lastRequest != null) {
                Date current = new Date();
                if (lastRequest.getTime() + intervalInMSecs > current.getTime()) {
                    Thread.sleep(intervalInMSecs - (current.getTime() - lastRequest.getTime()));
                }
            }
        } catch (InterruptedException ie) {
            // Ignore an interrupt
        } finally {
            lastRequest = new Date();
        }
    }

    public HttpClient(String url) throws URISyntaxException {
        this(url, null, null);
    }

    public HttpClient(String url, String username, String password) throws URISyntaxException {
        this(new ValidatedUrl(url), username, password);
    }

    public HttpClient(ValidatedUrl url, String username, String password) {
        if(url == null) throw new NullArgumentException("url");
        this.url = url.getUrl();

        //Only store the username and password if the scheme being used is considered "secure" - to avoid accidentally sending credentials in the clear.
        if(url.isSecure() && username != null) {
            this.username = username;
            this.password = password;
        } else {
            this.username = null;
            this.password = null;
        }
    }

    private CloseableHttpClient getNewApacheClient(){
        // Prepare the HttpClient
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        if (getUsername() != null){
            credsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), new UsernamePasswordCredentials(getUsername(), getPassword()));
        }

        return HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
                .setConnectionManager(getConnectionManager()).setDefaultRequestConfig(getDefaultRequestConfig()).build();
    }


    public ApiResponse executeGetRequest() throws IOException {

        //note we deliberately don't close these to keep the connection manager alive - this is a bit hacky...
        CloseableHttpClient httpclient  = getNewApacheClient();

        // Ensure we do not send request too frequently
        regulateRequestFrequency();

        // Issue get request
        HttpGet getMethod = new HttpGet(getUrl());
        CloseableHttpResponse response = httpclient.execute(getMethod);

        ApiResponse responseToReturn = new ApiResponse(response);

        ///convert non 200 responses into exceptions.
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != HttpStatus.SC_OK){
            String codeDescription = EnglishReasonPhraseCatalog.INSTANCE.getReason(responseCode, null);
            String message = MessageFormat.format("Invalid Http response code received: {0} ({1})", responseCode, codeDescription);
            responseToReturn.dispose();
            throw new InvalidResponseException(message, responseCode);
        }
        return new ApiResponse(response);
    }

    /**
     * Execute a get request,
     * @param maxRetries Number of times to retry the request
     * @return InputStream corresponding to the request body
     * @throws IOException Failure reading the request stream
     */
    public ApiResponse executeGetRequest(int maxRetries) throws IOException {
        if (maxRetries == 0) {
            return executeGetRequest();
        }

        IOException lastError = null;

        while (maxRetries-- > 0) {
            try {
                return executeGetRequest();
            } catch (IOException io) {
                lastError = io;
            }
        }

        throw lastError;
    }

    public ApiResponse executePost(List<NameValuePair> nameValuePairs) throws IOException {
        //note we deliberately don't close these to keep the connection manager alive - this is a bit hacky...
        CloseableHttpClient httpclient  = getNewApacheClient();

        // Ensure we do not send request too frequently
        regulateRequestFrequency();

        // Issue post request
        HttpPost post = new HttpPost(getUrl());
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));

        CloseableHttpResponse response = httpclient.execute(post);

        ApiResponse responseToReturn = new ApiResponse(response);
        ///convert non 200 responses into exceptions.
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != HttpStatus.SC_OK){
            String codeDescription = EnglishReasonPhraseCatalog.INSTANCE.getReason(responseCode, null);
            String message = MessageFormat.format("Invalid Http response code received: {0} ({1})", responseCode, codeDescription);
            responseToReturn.dispose();
            throw new HttpClient.InvalidResponseException(message, responseCode);
        }
        return responseToReturn;
    }

    /*
    Inner class to represent the response from an API and offer a "dispose" method to close http connections when finished with the stream.
     */
    public static class ApiResponse{
        final private CloseableHttpResponse response;
        final private HttpEntity entity;
        private boolean disposed = false;

        private ApiResponse(CloseableHttpResponse response){
            if(response == null) throw new NullArgumentException("response");
            this.response = response;
            entity = this.response.getEntity();
        }
        public InputStream getResponseStream() throws IOException{
            if(!disposed) {
                if(entity != null) {
                    return new BufferedInputStream(entity.getContent());
                }
            }
            throw new IOException("APIResponse object already disposed");
        }

        public void dispose() throws IOException{
            if(!disposed) {
                if(entity != null){
                    getResponseStream().close();
                }
                response.close();
                disposed = true;
            }
        }

        public int getResponseCode() {
            return response.getStatusLine().getStatusCode();
        }
    }

    public static class InvalidResponseException extends IOException{
        final int responseCode;
        public int getResponseCode(){ return responseCode; }

        private InvalidResponseException(String message, int responseCode){
            super(message);
            this.responseCode = responseCode;
        }
    }
}

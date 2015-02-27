package org.openstack4j.connectors.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import org.openstack4j.core.transport.HttpRequest;
import org.openstack4j.core.transport.HttpResponse;
import org.openstack4j.core.transport.ObjectMapperSingleton;
import org.openstack4j.core.transport.functions.EndpointURIFromRequestFunction;
import org.openstack4j.openstack.logging.Logger;
import org.openstack4j.openstack.logging.LoggerFactory;

/**
 * HttpCommand is responsible for executing the actual request driven by the HttpExecutor. 
 * 
 * @param <R>
 */
public final class HttpCommand<R> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpCommand.class);
    
    private HttpRequest<R> request;
    private URL connectionUrl;
    private HttpURLConnection connection;
    private int retries;

    private HttpCommand(HttpRequest<R> request) {
//        System.out.println("Setting Request" + request);
        this.request = request;
    }

    /**
     * Creates a new HttpCommand from the given request
     * @param request the request
     * @return the command
     */
    public static <R> HttpCommand<R> create(HttpRequest<R> request) {
        HttpCommand<R> command = new HttpCommand<R>(request);
        command.initialize();
        return command;
    }

    private void initialize() {
        try {
            //        client = new OkHttpClient();
//
//        Config config = request.getConfig();
//        if (config.getConnectTimeout() > 0)
//            client.setConnectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS);
//        
//        if (config.getReadTimeout() > 0)
//            client.setReadTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS);
//        
//        if (config.isIgnoreSSLVerification())
//        {
//            client.setHostnameVerifier(UntrustedSSL.getHostnameVerifier());
//            client.setSslSocketFactory(UntrustedSSL.getSSLContext().getSocketFactory());
//        }
//
//        if (config.getSslContext() != null) 
//            client.setSslSocketFactory(config.getSslContext().getSocketFactory());
//
//        if (config.getHostNameVerifier() != null)
//            client.setHostnameVerifier(config.getHostNameVerifier());
//        
//        clientReq = new Request.Builder();
            
            populateQueryParams();
            populateHeaders();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
        
    }

    /**
     * Executes the command and returns the Response
     * 
     * @return the response
     * @throws Exception 
     */
    public HttpResponse execute() throws Exception {
        StringBuilder requestBody = new StringBuilder();
        if (request.getEntity() != null) {
            String content = ObjectMapperSingleton.getContext(request.getEntity().getClass()).writer().writeValueAsString(request.getEntity());
            requestBody.append(content);
            
        } else if(request.hasJson()) {
            requestBody.append(request.getJson());
        }
        
        
        StringBuilder contentBuilder = new StringBuilder();
        BufferedReader in = null;
        try {
            connection.setRequestMethod(request.getMethod().name());
            if (requestBody.length() > 0) {
                System.out.println(requestBody.toString());
                connection.setDoOutput(true);
                BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream());
                out.write(requestBody.toString().getBytes());
                out.flush();
            }

            //contentBuilder;
            in = new BufferedReader(new InputStreamReader(
                                        connection.getInputStream()));
            String inputLine;
            
            while ((inputLine = in.readLine()) != null)  {
                contentBuilder.append(inputLine);
            }
            
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            
            in = new BufferedReader(new InputStreamReader(
                                        connection.getErrorStream()));
            String inputLine;
            
            while ((inputLine = in.readLine()) != null)  {
                contentBuilder.append(inputLine);
            }
            
        } finally {
            if (in != null) {
                in.close();
            }
        }
        
//        System.err.println(contentBuilder.toString());

        HttpResponseImpl responseImpl = 
                HttpResponseImpl.wrap(connection.getHeaderFields(), 
                    connection.getResponseCode(), connection.getResponseMessage(),
                    contentBuilder.toString());

        
        connection.disconnect();
        return responseImpl;
    }

    /**
     * @return true if a request entity has been set
     */
    public boolean hasEntity() {
        return request.getEntity() != null;
    }

    /**
     * @return current retry execution count for this command
     */
    public int getRetries() {
        return retries;
    }

    /**
     * @return incremement's the retry count and returns self
     */
    public HttpCommand<R> incrementRetriesAndReturn() {
        initialize();
        retries++;
        return this;
    }

    public HttpRequest<R> getRequest() {
        return request;
    }

    private void populateQueryParams() throws MalformedURLException  {

        StringBuilder url = new StringBuilder();
        url.append(new EndpointURIFromRequestFunction().apply(request));

        if (!request.hasQueryParams()) 
        {
            connectionUrl = new URL(url.toString());
            return;
        }

        url.append("?");
        
        for(Map.Entry<String, List<Object> > entry : request.getQueryParams().entrySet()) {
            for (Object o : entry.getValue()) {
                try
                {
                  url.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(String.valueOf(o), "UTF-8"));
                }
                catch (UnsupportedEncodingException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        connectionUrl = new URL(url.toString());
    }

    private void populateHeaders() throws IOException {

//        System.err.println(connectionUrl);
        connection = (HttpURLConnection) connectionUrl.openConnection();
        
//        System.out.println("Headers ???");
        if (!request.hasHeaders()) return;
//        System.out.println("Headers available");
        for(Map.Entry<String, Object> h : request.getHeaders().entrySet()) {
//            System.out.println(h.getKey() + ":" + String.valueOf(h.getValue()));
            connection.setRequestProperty(h.getKey(), String.valueOf(h.getValue()));
        }
        
        connection.setRequestProperty("Content-Type", "application/json");
    }
}

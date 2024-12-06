import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.Request;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private Map<String, Map<String, Handler>> handlersServer = new ConcurrentHashMap();

    public void start(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            addDefaultHandler();
            while (true) {
                Socket socket = serverSocket.accept();
                executorService.execute(() -> {
                    processingMessage(socket);
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void processingMessage(Socket socket) {
        try (
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {

            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final String requestLine = in.readLine();
            if (requestLine == null) {
                return;
            }
            final String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                return;
            }
            Request request = new Request() {
                @Override
                public URI getRequestURI() {
                    return URI.create(parts[1]);
                }

                @Override
                public String getRequestMethod() {
                    return parts[0];
                }

                @Override
                public Headers getRequestHeaders() {
                    return null;
                }
            };

            if (handlersServer.containsKey(request.getRequestURI().toString())) {
                if (handlersServer.get(request.getRequestURI().toString()).containsKey(request.getRequestMethod())) {
                    handlersServer.get(request.getRequestURI().toString()).get(request.getRequestMethod()).handle(request, out);
                }

            } else {
                handlersServer.get("default").get("GET").handle(request, out);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (handlersServer.containsKey(path)) {
            handlersServer.get(path).put(method, handler);
        } else {
            Map<String, Handler> map = new ConcurrentHashMap<>();
            map.put(method, handler);
            handlersServer.put(path, map);
        }
    }

    private void addDefaultHandler() {
        handlersServer.put("default", Map.of("GET", new Handler() {
            @Override
            public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
                responseStream.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                responseStream.flush();
            }
        }));
    }

    public void getHandler() {
        for (String uri : handlersServer.keySet()) {
            for (String method : handlersServer.get(uri).keySet()) {
                System.out.println("Handler - " + uri + " " + method + " " + handlersServer.get(uri).get(method));
            }
        }
    }
}

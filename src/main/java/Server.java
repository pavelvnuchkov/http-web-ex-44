import org.apache.http.NameValuePair;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                final BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {

            int limit = 4096;
            in.mark(limit);
            byte[] buffer = new byte[limit];
            int read = in.read(buffer);

            byte[] requestLineDelimited = new byte[]{'\r', '\n'};
            int requestLineEnd = indexOf(buffer, requestLineDelimited, 0, read);

            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            //  final String requestLine = in.readLine();
            if (requestLineEnd == -1) {
                badRequest(out);
                return;
            }


            final String[] requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                return;
            }
            System.out.println("Стартовая строка!");
            for (String param : requestLine) {
                System.out.println(param);
            }
            byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            int headersStart = requestLineEnd + requestLineDelimited.length;
            int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);

            if (headersEnd == -1) {
                badRequest(out);
                return;
            }

            in.reset();
            in.skip(headersStart);

            byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
            List<String> headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println("Заголовки!\n" + headers);
            Request request = new Request(requestLine[0], requestLine[1]);

            if (!requestLine[0].equals("GET")) {
                in.skip(headersDelimiter.length);
                Optional<String> contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    String body = new String(in.readNBytes(Integer.parseInt(contentLength.get())));
                    Optional<String> contentType = extractHeader(headers, "Content-Type");
                    if (contentType.isPresent()) {
                        request.addBody(contentType.get(), body);
                    }
                }

            }

            if (handlersServer.containsKey(request.getRequestURI().toString())) {
                if (handlersServer.get(request.getRequestURI().toString()).containsKey(request.getRequestMethod())) {
                    System.out.println("Параметры запроса!");
                    if (request.getQueryParams() != null) {
                        for (NameValuePair param : request.getQueryParams()) {
                            System.out.println(param.getName() + " " + param.getValue());
                        }
                    } else {
                        System.out.println("Запрос без параметров.");
                    }

                    handlersServer.get(request.getRequestURI().toString()).get(request.getRequestMethod()).handle(request, out);
                } else {
                    badRequest(out);
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

    public int indexOf(byte[] array, byte[] target, int start, int max) {
        for (int i = start; i < array.length; i++) {
            int coincidences = 0;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    break;
                }
                coincidences++;
            }
            if (coincidences == target.length) {
                return i;
            }
        }
        return -1;
    }

    public Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream().filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(o -> o.trim())
                .findFirst();
    }

    public void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
}

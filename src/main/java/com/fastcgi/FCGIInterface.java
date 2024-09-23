package com.fastcgi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Класс FCGIInterface представляет интерфейс для работы с запросами FastCGI.
 * Он обрабатывает подключения от FastCGI-клиентов и настраивает потоки ввода и вывода для взаимодействия
 * с сервером через протокол FastCGI.
 */
public class FCGIInterface {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIInterface.java,v 1.4 2000/03/27 15:37:25 robs Exp $";

    /** Текущий запрос FastCGI, обрабатываемый сервером. */
    public static FCGIRequest request = null;

    /** Флаг, указывающий, был ли вызван метод accept для обработки соединений. */
    public static boolean acceptCalled = false;

    /** Флаг, указывающий, является ли это подключение FastCGI-соединением. */
    public static boolean isFCGI = true;

    /** Параметры, переданные при инициализации FastCGI. */
    public static Properties startupProps;

    /** Сокет сервера для приема соединений FastCGI. */
    public static ServerSocket srvSocket;

    /**
     * Конструктор по умолчанию.
     * Создает новый экземпляр интерфейса FastCGI, но не инициализирует подключение.
     */
    public FCGIInterface() {
    }

    /**
     * Принимает и обрабатывает соединения FastCGI. Этот метод инициализирует сокет сервера
     * и настраивает потоки ввода/вывода для взаимодействия с клиентами FastCGI.
     *
     * @return 0 — если соединение успешно обработано, -1 — если произошла ошибка.
     */
    public int FCGIaccept() {
        int acceptResult = 0;

        // Проверяем, был ли уже вызван accept
        if (!acceptCalled) {
            isFCGI = System.getProperties().containsKey("FCGI_PORT");
            acceptCalled = true;

            // Если это FastCGI соединение, создаем серверный сокет
            if (isFCGI) {
                startupProps = new Properties(System.getProperties());
                String portStr = System.getProperty("FCGI_PORT");
                if (portStr.length() <= 0) {
                    return -1;
                }

                int portNum = Integer.parseInt(portStr);
                try {
                    srvSocket = new ServerSocket(portNum);
                } catch (IOException e) {
                    if (request != null) {
                        request.socket = null;
                    }
                    srvSocket = null;
                    request = null;
                    return -1;
                }
            }
        } else if (!isFCGI) {
            return -1;
        }

        // Обрабатываем соединение FastCGI
        if (isFCGI) {
            try {
                acceptResult = this.FCGIAccept();
            } catch (IOException e) {
                return -1;
            }

            if (acceptResult < 0) {
                return -1;
            }

            // Настраиваем стандартные потоки ввода/вывода для работы с запросом FastCGI
            System.setIn(new BufferedInputStream(request.inStream, 8192));
            System.setOut(new PrintStream(new BufferedOutputStream(request.outStream, 8192)));
            System.setErr(new PrintStream(new BufferedOutputStream(request.errStream, 512)));
            System.setProperties(request.params);
        }

        return 0;
    }

    /**
     * Вспомогательный метод для приема и обработки нового соединения FastCGI.
     * Настраивает сокет, инициализирует входной и выходной потоки и проверяет, был ли запрос завершен.
     *
     * @return 0 — если запрос успешно обработан, -1 — если произошла ошибка.
     * @throws IOException Если произошла ошибка при приеме соединения или обработке запроса.
     */
    int FCGIAccept() throws IOException {
        boolean errCloseEx = false;
        boolean outCloseEx = false;

        // Закрываем предыдущий запрос, если он существует
        if (request != null) {
            System.err.close();
            System.out.close();

            boolean prevRequestFailed = errCloseEx || outCloseEx || request.inStream.getFCGIError() != 0 || request.inStream.getException() != null;
            if (prevRequestFailed || !request.keepConnection) {
                request.socket.close();
                request.socket = null;
            }

            if (prevRequestFailed) {
                request = null;
                return -1;
            }
        } else {
            // Инициализируем новый запрос
            request = new FCGIRequest();
            request.socket = null;
            request.inStream = null;
        }

        boolean isNewConnection = false;

        // Принимаем новое соединение FastCGI
        do {
            if (request.socket == null) {
                try {
                    request.socket = srvSocket.accept();
                } catch (IOException e) {
                    request.socket = null;
                    request = null;
                    return -1;
                }
                isNewConnection = true;
            }

            // Читаем данные из входного потока
            request.isBeginProcessed = false;
            request.inStream = new FCGIInputStream(request.socket.getInputStream(), 8192, 0, request);
            request.inStream.fill();

            if (request.isBeginProcessed) {
                // Инициализируем параметры запроса
                request.params = new Properties(startupProps);
                switch (request.role) {
                    case 1 -> request.params.put("ROLE", "RESPONDER");
                    case 2 -> request.params.put("ROLE", "AUTHORIZER");
                    case 3 -> request.params.put("ROLE", "FILTER");
                    default -> {
                        return -1;
                    }
                }

                // Чтение параметров запроса
                request.inStream.setReaderType(4);
                if ((new FCGIMessage(request.inStream)).readParams(request.params) < 0) {
                    return -1;
                }

                // Настройка потоков вывода и ошибок
                request.inStream.setReaderType(5);
                request.outStream = new FCGIOutputStream(request.socket.getOutputStream(), 8192, 6, request);
                request.errStream = new FCGIOutputStream(request.socket.getOutputStream(), 512, 7, request);
                request.numWriters = 2;
                return 0;
            }

            // Закрытие сокета, если запрос не завершен
            request.socket.close();
            request.socket = null;
        } while (!isNewConnection);

        return -1;
    }
}

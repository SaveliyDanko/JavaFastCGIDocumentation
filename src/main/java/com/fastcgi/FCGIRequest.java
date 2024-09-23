package com.fastcgi;

import java.net.Socket;
import java.util.Properties;

/**
 * Класс FCGIRequest представляет собой один запрос FastCGI, который включает в себя
 * информацию о соединении, идентификатор запроса, параметры запроса и потоки для обработки
 * ввода и вывода данных.
 */
public class FCGIRequest {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIRequest.java,v 1.3 2000/03/21 12:12:26 robs Exp $";

    /** Сокет, через который происходит соединение с FastCGI сервером. */
    public Socket socket;

    /** Флаг, указывающий, был ли начат процесс обработки запроса. */
    public boolean isBeginProcessed;

    /** Уникальный идентификатор запроса FastCGI. */
    public int requestID;

    /** Флаг, указывающий, следует ли поддерживать соединение после завершения запроса. */
    public boolean keepConnection;

    /** Роль приложения FastCGI, определяющая поведение при обработке запроса (Responder, Authorizer, Filter). */
    public int role;

    /** Статус завершения обработки запроса (например, успех или ошибка). */
    public int appStatus;

    /** Количество потоков, которые используют этот запрос для записи данных. */
    public int numWriters;

    /** Поток для чтения входящих данных FastCGI (stdin). */
    public FCGIInputStream inStream;

    /** Поток для записи выходных данных FastCGI (stdout). */
    public FCGIOutputStream outStream;

    /** Поток для записи ошибок FastCGI (stderr). */
    public FCGIOutputStream errStream;

    /** Параметры запроса (например, заголовки HTTP-запроса). */
    public Properties params;

    /**
     * Конструктор по умолчанию. Создаёт новый запрос FastCGI с пустыми полями.
     * После создания объект должен быть инициализирован в процессе приёма и обработки запроса.
     */
    public FCGIRequest() {
    }
}

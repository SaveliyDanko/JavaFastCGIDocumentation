package com.fastcgi;

/**
 * FCGIGlobalDefs содержит глобальные константы и определения для работы с протоколом FastCGI.
 * Эти константы используются для различных типов сообщений FastCGI, работы с заголовками, ролями и статусами запросов.
 */
public abstract class FCGIGlobalDefs {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIGlobalDefs.java,v 1.3 2000/03/21 12:12:25 robs Exp $";

    /** Максимальная длина данных в одном сообщении FastCGI. */
    public static final int def_FCGIMaxLen = 65535;

    /** Длина заголовка FastCGI (8 байт). */
    public static final int def_FCGIHeaderLen = 8;

    /** Длина тела сообщения EndRequest (8 байт). */
    public static final int def_FCGIEndReqBodyLen = 8;

    /** Длина тела сообщения BeginRequest (8 байт). */
    public static final int def_FCGIBeginReqBodyLen = 8;

    /** Длина тела сообщения неизвестного типа (8 байт). */
    public static final int def_FCGIUnknownBodyTypeBodyLen = 8;

    /** Версия протокола FastCGI, используется версия 1. */
    public static int def_FCGIVersion1 = 1;

    // Типы сообщений FastCGI

    /** Тип сообщения для начала запроса. */
    public static final int def_FCGIBeginRequest = 1;

    /** Тип сообщения для прерывания запроса. */
    public static final int def_FCGIAbortRequest = 2;

    /** Тип сообщения для завершения запроса. */
    public static final int def_FCGIEndRequest = 3;

    /** Тип сообщения для передачи параметров (например, заголовков запроса). */
    public static final int def_FCGIParams = 4;

    /** Тип сообщения для передачи входных данных (stdin). */
    public static final int def_FCGIStdin = 5;

    /** Тип сообщения для передачи выходных данных (stdout). */
    public static final int def_FCGIStdout = 6;

    /** Тип сообщения для передачи ошибок (stderr). */
    public static final int def_FCGIStderr = 7;

    /** Тип сообщения для передачи данных, не связанных с запросом (например, отладочные данные). */
    public static final int def_FCGIData = 8;

    /** Тип сообщения для запроса значений переменных от сервера (GetValues). */
    public static final int def_FCGIGetValues = 9;

    /** Тип сообщения с результатом запроса значений переменных (GetValuesResult). */
    public static final int def_FCGIGetValuesResult = 10;

    /** Тип сообщения для обработки неизвестного типа сообщения. */
    public static final int def_FCGIUnknownType = 11;

    /** Максимальное значение типа сообщения (используется для валидации). */
    public static final int def_FCGIMaxType = 11;

    /** Специальное значение для RequestID, обозначающее отсутствие запроса. */
    public static final int def_FCGINullRequestID = 0;

    /** Флаг, указывающий на сохранение соединения после завершения запроса. */
    public static int def_FCGIKeepConn = 1;

    // Роли FastCGI

    /** Роль приложения Responder, отвечающего на HTTP-запросы. */
    public static final int def_FCGIResponder = 1;

    /** Роль приложения Authorizer, проверяющего права доступа к ресурсу. */
    public static final int def_FCGIAuthorizer = 2;

    /** Роль приложения Filter, которое фильтрует данные запроса или ответа. */
    public static final int def_FCGIFilter = 3;

    // Статусы завершения запросов FastCGI

    /** Статус завершения запроса - запрос завершен успешно. */
    public static final int def_FCGIRequestComplete = 0;

    /** Статус завершения запроса - приложение не поддерживает мультиплексирование. */
    public static final int def_FCGICantMpxConn = 1;

    /** Статус завершения запроса - сервер перегружен. */
    public static final int def_FCGIOverload = 2;

    /** Статус завершения запроса - неизвестная роль. */
    public static final int def_FCGIUnknownRole = 3;

    // Параметры для GetValues

    /** Параметр для запроса максимального количества соединений (MaxConns). */
    public static final String def_FCGIMaxConns = "FCGI_MAX_CONNS";

    /** Параметр для запроса максимального количества запросов (MaxReqs). */
    public static final String def_FCGIMaxReqs = "FCGI_MAX_REQS";

    /** Параметр для запроса поддержки мультиплексированных соединений (MpxsConns). */
    public static final String def_FCGIMpxsConns = "FCGI_MPXS_CONNS";

    // Статусы обработки записей

    /** Статус записи потока. */
    public static final int def_FCGIStreamRecord = 0;

    /** Статус пропуска записи. */
    public static final int def_FCGISkip = 1;

    /** Статус начала записи. */
    public static final int def_FCGIBeginRecord = 2;

    /** Статус записи управления (менеджмент записи). */
    public static final int def_FCGIMgmtRecord = 3;

    // Коды ошибок

    /** Код ошибки - неподдерживаемая версия протокола. */
    public static final int def_FCGIUnsupportedVersion = -2;

    /** Код ошибки - протокольная ошибка. */
    public static final int def_FCGIProtocolError = -3;

    /** Код ошибки - ошибка параметров (некорректные параметры). */
    public static final int def_FCGIParamsError = -4;

    /** Код ошибки - ошибка последовательности вызовов. */
    public static final int def_FCGICallSeqError = -5;

    /**
     * Конструктор по умолчанию. Этот класс является абстрактным, поэтому конструктор здесь
     * в основном для того, чтобы предотвратить создание экземпляров.
     */
    public FCGIGlobalDefs() {
    }
}

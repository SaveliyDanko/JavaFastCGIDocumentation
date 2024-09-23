package com.fastcgi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Класс FCGIMessage предназначен для обработки и создания сообщений FastCGI.
 * Он отвечает за чтение и обработку заголовков FastCGI, параметров запроса,
 * а также за формирование заголовков и тел сообщений для отправки ответа серверу.
 */
public class FCGIMessage {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIMessage.java,v 1.4 2000/10/02 15:09:07 robs Exp $";

    /** Версия FastCGI протокола в заголовке. */
    private int h_version;

    /** Тип сообщения FastCGI (например, BeginRequest, Params, Stdout). */
    private int h_type;

    /** Уникальный идентификатор запроса FastCGI. */
    private int h_requestID;

    /** Длина содержимого в сообщении. */
    private int h_contentLength;

    /** Длина отступа (padding) в сообщении. */
    private int h_paddingLength;

    /** Роль FastCGI приложения (например, Responder, Authorizer). */
    private int br_role;

    /** Флаги FastCGI, используемые в сообщениях (например, Keep-Alive). */
    private int br_flags;

    /** Входной поток данных FastCGI. */
    private FCGIInputStream in;

    /**
     * Конструктор по умолчанию для создания пустого объекта FCGIMessage.
     */
    public FCGIMessage() {
    }

    /**
     * Конструктор для создания объекта FCGIMessage с заданным входным потоком данных.
     *
     * @param instream Входной поток данных FastCGI.
     */
    public FCGIMessage(FCGIInputStream instream) {
        this.in = instream;
    }

    /**
     * Обрабатывает заголовок сообщения FastCGI и возвращает статус обработки.
     * Проверяет версию протокола и тип сообщения, вызывает соответствующие методы
     * для обработки начального запроса или управляющих сообщений.
     *
     * @param hdr Массив байт, представляющий заголовок FastCGI.
     * @return Код состояния: 0 — успешная обработка, отрицательные значения — ошибки.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public int processHeader(byte[] hdr) throws IOException {
        this.processHeaderBytes(hdr);
        if (this.h_version != FCGIGlobalDefs.def_FCGIVersion1) {
            return -2;
        } else {
            this.in.contentLen = this.h_contentLength;
            this.in.paddingLen = this.h_paddingLength;
            if (this.h_type == 1) {
                return this.processBeginRecord(this.h_requestID);
            } else if (this.h_requestID == 0) {
                return this.processManagementRecord(this.h_type);
            } else if (this.h_requestID != this.in.request.requestID) {
                return 1;
            } else {
                return this.h_type != this.in.type ? -3 : 0;
            }
        }
    }

    /**
     * Обрабатывает байты заголовка сообщения FastCGI и сохраняет их в соответствующие поля.
     *
     * @param hdrBuf Массив байт, представляющий заголовок FastCGI.
     */
    private void processHeaderBytes(byte[] hdrBuf) {
        this.h_version = hdrBuf[0] & 255;
        this.h_type = hdrBuf[1] & 255;
        this.h_requestID = (hdrBuf[2] & 255) << 8 | hdrBuf[3] & 255;
        this.h_contentLength = (hdrBuf[4] & 255) << 8 | hdrBuf[5] & 255;
        this.h_paddingLength = hdrBuf[6] & 255;
    }

    /**
     * Обрабатывает сообщение BeginRequest для инициализации нового запроса.
     * Устанавливает роль FastCGI приложения и проверяет флаг сохранения соединения (Keep-Alive).
     *
     * @param requestID Идентификатор запроса FastCGI.
     * @return Код состояния: 2 — успешная обработка, отрицательные значения — ошибки.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public int processBeginRecord(int requestID) throws IOException {
        if (requestID != 0 && this.in.contentLen == 8) {
            if (this.in.request.isBeginProcessed) {
                byte[] endReqMsg = new byte[16];
                System.arraycopy(this.makeHeader(3, requestID, 8, 0), 0, endReqMsg, 0, 8);
                System.arraycopy(this.makeEndrequestBody(0, 1), 0, endReqMsg, 8, 8);

                try {
                    this.in.request.outStream.write(endReqMsg, 0, 16);
                } catch (IOException var5) {
                    this.in.request.outStream.setException(var5);
                    return -1;
                }
            }

            this.in.request.requestID = requestID;
            byte[] beginReqBody = new byte[8];
            if (this.in.read(beginReqBody, 0, 8) != 8) {
                return -3;
            } else {
                this.br_flags = beginReqBody[2] & 255;
                this.in.request.keepConnection = (this.br_flags & FCGIGlobalDefs.def_FCGIKeepConn) != 0;
                this.br_role = (beginReqBody[0] & 255) << 8 | beginReqBody[1] & 255;
                this.in.request.role = this.br_role;
                this.in.request.isBeginProcessed = true;
                return 2;
            }
        } else {
            return -3;
        }
    }

    /**
     * Обрабатывает управляющие сообщения FastCGI (например, GetValues).
     * Формирует ответ на запросы о параметрах сервера (например, максимальное количество соединений).
     *
     * @param type Тип управляющего сообщения FastCGI.
     * @return Код состояния: 3 — успешная обработка, отрицательные значения — ошибки.
     * @throws IOException Если произошла ошибка при отправке ответа.
     */
    public int processManagementRecord(int type) throws IOException {
        byte[] response = new byte[64];
        int wrndx = response[8];
        if (type == 9) {
            Properties tmpProps = new Properties();
            this.readParams(tmpProps);
            if (this.in.getFCGIError() != 0 || this.in.contentLen != 0) {
                return -3;
            }

            if (tmpProps.containsKey("FCGI_MAX_CONNS")) {
                this.makeNameVal("FCGI_MAX_CONNS", "1", response, wrndx);
            } else if (tmpProps.containsKey("FCGI_MAX_REQS")) {
                this.makeNameVal("FCGI_MAX_REQS", "1", response, wrndx);
            } else if (tmpProps.containsKey("FCGI_MPXS_CONNS")) {
                this.makeNameVal("FCGI_MPXS_CONNS", "0", response, wrndx);
            }

            int plen = 64 - wrndx;
            int len = wrndx - 8;
            System.arraycopy(this.makeHeader(10, 0, len, plen), 0, response, 0, 8);
        } else {
            System.arraycopy(this.makeHeader(11, 0, 8, 0), 0, response, 0, 8);
            System.arraycopy(this.makeUnknownTypeBodyBody(this.h_type), 0, response, 8, 8);
        }

        try {
            this.in.request.socket.getOutputStream().write(response, 0, 16);
            return 3;
        } catch (IOException var8) {
            return -1;
        }
    }

    /**
     * Генерирует заголовок FastCGI для отправки ответа.
     *
     * @param type Тип сообщения FastCGI.
     * @param requestId Идентификатор запроса.
     * @param contentLength Длина содержимого сообщения.
     * @param paddingLength Длина отступа (padding).
     * @return Массив байт, представляющий заголовок FastCGI.
     */
    public byte[] makeHeader(int type, int requestId, int contentLength, int paddingLength) {
        return new byte[]{(byte) FCGIGlobalDefs.def_FCGIVersion1, (byte) type, (byte) (requestId >> 8 & 255), (byte) (requestId & 255), (byte) (contentLength >> 8 & 255), (byte) (contentLength & 255), (byte) paddingLength, 0};
    }

    /**
     * Генерирует тело сообщения EndRequest для завершения запроса FastCGI.
     *
     * @param appStatus Статус завершения приложения.
     * @param protocolStatus Статус завершения протокола FastCGI.
     * @return Массив байт, представляющий тело сообщения EndRequest.
     */
    public byte[] makeEndrequestBody(int appStatus, int protocolStatus) {
        byte[] body = new byte[8];
        body[0] = (byte) (appStatus >> 24 & 255);
        body[1] = (byte) (appStatus >> 16 & 255);
        body[2] = (byte) (appStatus >> 8 & 255);
        body[3] = (byte) (appStatus & 255);
        body[4] = (byte) protocolStatus;

        for (int i = 5; i < 8; ++i) {
            body[i] = 0;
        }

        return body;
    }

    /**
     * Читает параметры запроса FastCGI и сохраняет их в объект Properties.
     *
     * @param props Объект Properties для хранения параметров запроса.
     * @return Код состояния: 0 — успешная обработка, отрицательные значения — ошибки.
     * @throws IOException Если произошла ошибка при чтении параметров.
     */
    public int readParams(Properties props) throws IOException {
        byte[] lenBuff = new byte[3];
        int i = 1;

        int nameLen;
        while ((nameLen = this.in.read()) != -1) {
            ++i;
            if ((nameLen & 128) != 0) {
                if (this.in.read(lenBuff, 0, 3) != 3) {
                    this.in.setFCGIError(-4);
                    return -1;
                }

                nameLen = (nameLen & 127) << 24 | (lenBuff[0] & 255) << 16 | (lenBuff[1] & 255) << 8 | lenBuff[2] & 255;
            }

            int valueLen;
            if ((valueLen = this.in.read()) == -1) {
                this.in.setFCGIError(-4);
                return -1;
            }

            if ((valueLen & 128) != 0) {
                if (this.in.read(lenBuff, 0, 3) != 3) {
                    this.in.setFCGIError(-4);
                    return -1;
                }

                valueLen = (valueLen & 127) << 24 | (lenBuff[0] & 255) << 16 | (lenBuff[1] & 255) << 8 | lenBuff[2] & 255;
            }

            byte[] name = new byte[nameLen];
            byte[] value = new byte[valueLen];
            if (this.in.read(name, 0, nameLen) != nameLen) {
                this.in.setFCGIError(-4);
                return -1;
            }

            if (this.in.read(value, 0, valueLen) != valueLen) {
                this.in.setFCGIError(-4);
                return -1;
            }

            String strName = new String(name);
            String strValue = new String(value);
            props.put(strName, strValue);
        }

        return 0;
    }

    /**
     * Генерирует тело сообщения для неизвестного типа FastCGI.
     *
     * @param type Тип неизвестного сообщения.
     * @return Массив байт, представляющий тело сообщения для неизвестного типа.
     */
    public byte[] makeUnknownTypeBodyBody(int type) {
        byte[] body = new byte[8];
        body[0] = (byte) type;

        for (int i = 1; i < 8; ++i) {
            body[i] = 0;
        }

        return body;
    }

    /**
     * Создает параметр "имя-значение" для ответа FastCGI.
     *
     * @param name Имя параметра.
     * @param value Значение параметра.
     * @param dest Массив байт для записи данных.
     * @param pos Позиция в массиве для записи.
     */
    void makeNameVal(String name, String value, byte[] dest, int pos) {
        int nameLen = name.length();
        if (nameLen < 128) {
            dest[pos++] = (byte) nameLen;
        } else {
            dest[pos++] = (byte) ((nameLen >> 24 | 128) & 255);
            dest[pos++] = (byte) (nameLen >> 16 & 255);
            dest[pos++] = (byte) (nameLen >> 8 & 255);
            dest[pos++] = (byte) nameLen;
        }

        int valLen = value.length();
        if (valLen < 128) {
            dest[pos++] = (byte) valLen;
        } else {
            dest[pos++] = (byte) ((valLen >> 24 | 128) & 255);
            dest[pos++] = (byte) (valLen >> 16 & 255);
            dest[pos++] = (byte) (valLen >> 8 & 255);
            dest[pos++] = (byte) valLen;
        }

        try {
            System.arraycopy(name.getBytes("UTF-8"), 0, dest, pos, nameLen);
            pos += nameLen;
            System.arraycopy(value.getBytes("UTF-8"), 0, dest, pos, valLen);
        } catch (UnsupportedEncodingException var8) {
        }
    }
}

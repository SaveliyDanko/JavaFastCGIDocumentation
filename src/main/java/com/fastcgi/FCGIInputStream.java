package com.fastcgi;

import java.io.IOException;
import java.io.InputStream;

/**
 * Класс FCGIInputStream представляет поток данных, поступающих от FastCGI-сервера.
 * Он предназначен для буферизации данных и их чтения в формате, соответствующем протоколу FastCGI.
 * <p>
 * Основная задача этого класса — чтение данных запроса, включая заголовки, тело запроса и другие части сообщения FastCGI.
 */
public class FCGIInputStream extends InputStream {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIInputStream.java,v 1.4 2000/03/21 12:12:25 robs Exp $";

    /** Указатель на следующий байт, который нужно прочитать. */
    public int rdNext;

    /** Указатель на конец данных в буфере. */
    public int stop;

    /** Флаг, указывающий, закрыт ли поток. */
    public boolean isClosed;

    /** Код ошибки, если произошла ошибка FastCGI. */
    private int errno;

    /** Исключение, возникшее при обработке данных. */
    private Exception errex;

    /** Буфер для хранения данных из входного потока. */
    public byte[] buff;

    /** Длина буфера для хранения данных. */
    public int buffLen;

    /** Указатель на конец данных в буфере. */
    public int buffStop;

    /** Тип потока (например, stdin, stdout, stderr). */
    public int type;

    /** Длина содержимого данных в текущем FastCGI-сообщении. */
    public int contentLen;

    /** Длина отступа (padding) в текущем FastCGI-сообщении. */
    public int paddingLen;

    /** Флаг, указывающий, нужно ли пропустить данные. */
    public boolean skip;

    /** Флаг, указывающий на конец записи (end of record). */
    public boolean eorStop;

    /** Запрос FastCGI, к которому относится данный поток. */
    public FCGIRequest request;

    /** Входной поток данных, который передаётся FastCGI-сервером. */
    public InputStream in;

    /**
     * Конструктор класса FCGIInputStream. Инициализирует входной поток с буфером заданного размера.
     *
     * @param inStream Входной поток данных FastCGI.
     * @param bufLen Размер буфера для хранения данных.
     * @param streamType Тип потока (stdin, stdout, stderr).
     * @param inReq Объект запроса FastCGI.
     */
    public FCGIInputStream(InputStream inStream, int bufLen, int streamType, FCGIRequest inReq) {
        this.in = inStream;
        this.buffLen = Math.min(bufLen, 65535); // Максимальный размер буфера 65535 байт.
        this.buff = new byte[this.buffLen];
        this.type = streamType;
        this.stop = this.rdNext = this.buffStop = 0;
        this.isClosed = false;
        this.contentLen = 0;
        this.paddingLen = 0;
        this.skip = false;
        this.eorStop = false;
        this.request = inReq;
    }

    /**
     * Читает один байт данных из потока.
     * Если буфер пуст, вызывает метод {@link #fill()} для его заполнения.
     *
     * @return Значение следующего байта или -1, если поток закрыт.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public int read() throws IOException {
        if (this.rdNext != this.stop) {
            return this.buff[this.rdNext++] & 255;
        } else if (this.isClosed) {
            return -1;
        } else {
            this.fill();
            return this.rdNext != this.stop ? this.buff[this.rdNext++] & 255 : -1;
        }
    }

    /**
     * Читает массив байт из потока.
     *
     * @param b Массив байт для записи данных.
     * @return Количество прочитанных байт.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public int read(byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    /**
     * Читает часть массива байт из потока.
     *
     * @param b Массив байт для записи данных.
     * @param off Смещение, с которого начинается запись.
     * @param len Количество байт для чтения.
     * @return Количество прочитанных байт.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        } else if (len <= this.stop - this.rdNext) {
            System.arraycopy(this.buff, this.rdNext, b, off, len);
            this.rdNext += len;
            return len;
        } else {
            int bytesMoved = 0;

            while (true) {
                if (this.rdNext != this.stop) {
                    int m = Math.min(len - bytesMoved, this.stop - this.rdNext);
                    System.arraycopy(this.buff, this.rdNext, b, off, m);
                    bytesMoved += m;
                    this.rdNext += m;
                    if (bytesMoved == len) {
                        return bytesMoved;
                    }

                    off += m;
                }

                if (this.isClosed) {
                    return bytesMoved;
                }

                this.fill();
            }
        }
    }

    /**
     * Загружает данные в буфер из входного потока FastCGI.
     * Этот метод управляет чтением заголовков и отступов FastCGI.
     *
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public void fill() throws IOException {
        byte[] headerBuf = new byte[8];
        int headerLen = 0;

        while (true) {
            int count;
            do {
                while (true) {
                    if (this.rdNext == this.buffStop) {
                        try {
                            count = this.in.read(this.buff, 0, this.buffLen);
                        } catch (IOException var6) {
                            IOException e = var6;
                            this.setException(e);
                            return;
                        }

                        if (count == 0) {
                            this.setFCGIError(-3);
                            return;
                        }

                        this.rdNext = 0;
                        this.buffStop = count;
                    }

                    if (this.contentLen <= 0) {
                        break;
                    }

                    count = Math.min(this.contentLen, this.buffStop - this.rdNext);
                    this.contentLen -= count;
                    if (!this.skip) {
                        this.stop = this.rdNext + count;
                        return;
                    }

                    this.rdNext += count;
                    if (this.contentLen <= 0) {
                        this.skip = false;
                        break;
                    }
                }

                if (this.paddingLen <= 0) {
                    break;
                }

                count = Math.min(this.paddingLen, this.buffStop - this.rdNext);
                this.paddingLen -= count;
                this.rdNext += count;
            } while (this.paddingLen > 0);

            if (this.eorStop) {
                this.stop = this.rdNext;
                this.isClosed = true;
                return;
            }

            count = Math.min(headerBuf.length - headerLen, this.buffStop - this.rdNext);
            System.arraycopy(this.buff, this.rdNext, headerBuf, headerLen, count);
            headerLen += count;
            this.rdNext += count;
            if (headerLen >= headerBuf.length) {
                headerLen = 0;
                this.eorStop = true;
                this.stop = this.rdNext;
                int status = (new FCGIMessage(this)).processHeader(headerBuf);
                this.eorStop = false;
                this.isClosed = false;
                switch (status) {
                    case 0:
                        if (this.contentLen == 0) {
                            this.stop = this.rdNext;
                            this.isClosed = true;
                            return;
                        }
                        break;
                    case 1:
                        this.skip = true;
                        break;
                    case 2:
                        return;
                    case 3:
                        break;
                    default:
                        this.setFCGIError(status);
                        return;
                }
            }
        }
    }

    /**
     * Пропускает указанное количество байт в потоке.
     *
     * @param n Количество байт для пропуска.
     * @return Количество фактически пропущенных байт.
     * @throws IOException Если произошла ошибка при чтении данных.
     */
    public long skip(long n) throws IOException {
        byte[] data = new byte[(int) n];
        return (long) this.in.read(data);
    }

    /**
     * Устанавливает код ошибки FastCGI и закрывает поток.
     *
     * @param errnum Код ошибки.
     */
    public void setFCGIError(int errnum) {
        if (this.errno == 0) {
            this.errno = errnum;
        }

        this.isClosed = true;
    }

    /**
     * Устанавливает исключение, произошедшее при чтении данных, и закрывает поток.
     *
     * @param errexpt Исключение.
     */
    public void setException(Exception errexpt) {
        if (this.errex == null) {
            this.errex = errexpt;
        }

        this.isClosed = true;
    }

    /**
     * Очищает код ошибки FastCGI.
     */
    public void clearFCGIError() {
        this.errno = 0;
    }

    /**
     * Очищает информацию о произошедшем исключении.
     */
    public void clearException() {
        this.errex = null;
    }

    /**
     * Возвращает код ошибки FastCGI.
     *
     * @return Код ошибки.
     */
    public int getFCGIError() {
        return this.errno;
    }

    /**
     * Возвращает последнее произошедшее исключение.
     *
     * @return Исключение, если оно было.
     */
    public Exception getException() {
        return this.errex;
    }

    /**
     * Устанавливает тип потока для чтения (stdin, stdout, stderr).
     *
     * @param streamType Тип потока.
     */
    public void setReaderType(int streamType) {
        this.type = streamType;
        this.eorStop = false;
        this.skip = false;
        this.contentLen = 0;
        this.paddingLen = 0;
        this.stop = this.rdNext;
        this.isClosed = false;
    }

    /**
     * Закрывает поток и освобождает ресурсы.
     *
     * @throws IOException Если произошла ошибка при закрытии потока.
     */
    public void close() throws IOException {
        this.isClosed = true;
        this.stop = this.rdNext;
    }

    /**
     * Возвращает количество байт, доступных для чтения из потока.
     *
     * @return Количество байт, доступных для чтения.
     * @throws IOException Если произошла ошибка при проверке доступных данных.
     */
    public int available() throws IOException {
        return this.stop - this.rdNext + this.in.available();
    }
}

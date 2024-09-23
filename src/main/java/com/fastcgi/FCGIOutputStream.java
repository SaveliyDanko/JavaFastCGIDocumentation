package com.fastcgi;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Класс FCGIOutputStream представляет собой поток данных для отправки информации
 * от FastCGI приложения к веб-серверу. Этот класс управляет буферизацией данных и
 * обработкой различных типов FastCGI сообщений, таких как stdout, stderr и другие.
 */
public class FCGIOutputStream extends OutputStream {

    /** Идентификатор версии исходного кода. */
    private static final String RCSID = "$Id: FCGIOutputStream.java,v 1.3 2000/03/21 12:12:26 robs Exp $";

    /** Указатель на следующий байт, который нужно записать в буфер. */
    public int wrNext;

    /** Указатель на конец буфера. */
    public int stop;

    /** Флаг, указывающий, закрыт ли поток. */
    public boolean isClosed;

    /** Код ошибки, если произошла ошибка FastCGI. */
    private int errno;

    /** Исключение, возникшее при записи данных. */
    private Exception errex;

    /** Буфер для хранения данных, которые будут отправлены. */
    public byte[] buff;

    /** Размер буфера для хранения данных. */
    public int buffLen;

    /** Указатель на конец данных в буфере. */
    public int buffStop;

    /** Тип FastCGI потока (stdout, stderr и т.д.). */
    public int type;

    /** Флаг, указывающий, были ли уже записаны какие-либо данные. */
    public boolean isAnythingWritten;

    /** Флаг, указывающий, нужно ли писать данные напрямую без заголовков FastCGI. */
    public boolean rawWrite;

    /** Текущий запрос FastCGI, к которому относится этот поток. */
    public FCGIRequest request;

    /** Основной поток вывода, в который отправляются данные. */
    public OutputStream out;

    /**
     * Конструктор класса FCGIOutputStream. Инициализирует поток с буфером заданного размера.
     *
     * @param outStream Поток, в который будут записываться данные.
     * @param bufLen Размер буфера для записи данных.
     * @param streamType Тип потока FastCGI (stdout, stderr и т.д.).
     * @param inreq Объект запроса FastCGI.
     */
    public FCGIOutputStream(OutputStream outStream, int bufLen, int streamType, FCGIRequest inreq) {
        this.out = outStream;
        this.buffLen = Math.min(bufLen, 65535); // Максимальный размер буфера 65535 байт.
        this.buff = new byte[this.buffLen];
        this.type = streamType;
        this.stop = this.buffStop = this.buffLen;
        this.isAnythingWritten = false;
        this.rawWrite = false;
        this.wrNext = 8; // Отступ для заголовка FastCGI
        this.isClosed = false;
        this.request = inreq;
    }

    /**
     * Записывает один байт в поток.
     * Если буфер заполнен, вызывается метод {@link #empty(boolean)}, чтобы освободить буфер.
     *
     * @param c Байт данных, который нужно записать.
     * @throws IOException Если произошла ошибка при записи данных.
     */
    public void write(int c) throws IOException {
        if (this.wrNext != this.stop) {
            this.buff[this.wrNext++] = (byte) c;
        } else if (this.isClosed) {
            throw new EOFException();
        } else {
            this.empty(false);
            if (this.wrNext != this.stop) {
                this.buff[this.wrNext++] = (byte) c;
            } else {
                throw new EOFException();
            }
        }
    }

    /**
     * Записывает массив байт в поток.
     *
     * @param b Массив данных, которые нужно записать.
     * @throws IOException Если произошла ошибка при записи данных.
     */
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    /**
     * Записывает часть массива байт в поток.
     * Если данных больше, чем свободного места в буфере, данные сначала записываются в буфер,
     * а затем буфер очищается и записывается оставшаяся часть данных.
     *
     * @param b Массив данных.
     * @param off Смещение, с которого начинается запись.
     * @param len Длина данных для записи.
     * @throws IOException Если произошла ошибка при записи данных.
     */
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= this.stop - this.wrNext) {
            System.arraycopy(b, off, this.buff, this.wrNext, len);
            this.wrNext += len;
        } else {
            int bytesMoved = 0;

            while (true) {
                if (this.wrNext != this.stop) {
                    int m = Math.min(len - bytesMoved, this.stop - this.wrNext);
                    System.arraycopy(b, off, this.buff, this.wrNext, m);
                    bytesMoved += m;
                    this.wrNext += m;
                    if (bytesMoved == len) {
                        return;
                    }

                    off += m;
                }

                if (this.isClosed) {
                    throw new EOFException();
                }

                this.empty(false);
            }
        }
    }

    /**
     * Освобождает буфер, отправляя данные в поток, и, при необходимости, закрывает поток.
     * Если параметр {@code doClose} установлен в {@code true}, добавляются финальные записи FastCGI.
     *
     * @param doClose Указывает, нужно ли закрыть поток после записи.
     * @throws IOException Если произошла ошибка при отправке данных.
     */
    public void empty(boolean doClose) throws IOException {
        if (!this.rawWrite) {
            int cLen = this.wrNext - 8;
            if (cLen > 0) {
                System.arraycopy((new FCGIMessage()).makeHeader(this.type, this.request.requestID, cLen, 0), 0, this.buff, 0, 8);
            } else {
                this.wrNext = 0;
            }
        }

        if (doClose) {
            this.writeCloseRecords();
        }

        if (this.wrNext != 0) {
            this.isAnythingWritten = true;

            try {
                this.out.write(this.buff, 0, this.wrNext);
            } catch (IOException var4) {
                IOException e = var4;
                this.setException(e);
                return;
            }

            this.wrNext = 0;
        }

        if (!this.rawWrite) {
            this.wrNext += 8; // Отступ для следующего заголовка
        }
    }

    /**
     * Закрывает поток, отправляя все данные и добавляя необходимые записи FastCGI для завершения запроса.
     *
     * @throws IOException Если произошла ошибка при закрытии потока.
     */
    public void close() throws IOException {
        if (!this.isClosed) {
            this.empty(true);
            this.isClosed = true;
            this.stop = this.wrNext;
        }
    }

    /**
     * Очищает буфер и отправляет данные в поток.
     * Данные не закрываются, поток остается открытым.
     *
     * @throws IOException Если произошла ошибка при отправке данных.
     */
    public void flush() throws IOException {
        if (!this.isClosed) {
            this.empty(false);
        }
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
     * Устанавливает исключение, возникшее при записи данных, и закрывает поток.
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
    public int etFCGIError() {
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
     * Записывает финальные записи FastCGI для завершения запроса.
     * Добавляются записи для стандартного вывода, вывода ошибок и окончательного статуса.
     *
     * @throws IOException Если произошла ошибка при отправке данных.
     */
    public void writeCloseRecords() throws IOException {
        FCGIMessage msg = new FCGIMessage();
        this.rawWrite = true;
        byte[] endReq;
        if (this.type != 7 || this.wrNext != 0 || this.isAnythingWritten) {
            endReq = new byte[8];
            System.arraycopy(msg.makeHeader(this.type, this.request.requestID, 0, 0), 0, endReq, 0, 8);
            this.write(endReq, 0, endReq.length);
        }

        if (this.request.numWriters == 1) {
            endReq = new byte[16];
            System.arraycopy(msg.makeHeader(3, this.request.requestID, 8, 0), 0, endReq, 0, 8);
            System.arraycopy(msg.makeEndrequestBody(this.request.appStatus, 0), 0, endReq, 8, 8);
            this.write(endReq, 0, 16);
        }

        --this.request.numWriters;
    }
}
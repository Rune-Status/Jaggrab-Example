/*
 * Copyright (C) 2019 Dylan Vicchiarelli
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.lemon.net.jaggrab;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.zip.CRC32;

public class FileDemultiplexer extends Thread {

    /**
     * A connection will timeout if no data is received within this time-frame.
     * Measured in milliseconds.
     */
    public static final long TIME_OUT_DELAY = 5000;

    /**
     * The release number of the client.
     */
    public static final int CLIENT_MODEL_NUMBER = 377;

    /**
     * The path to cache files.
     */
    public static final String PATH_TO_CACHE = "./data/cache/";

    /**
     * The prefix for a file request.
     */
    public static final String REQUEST_PREFIX = "JAGGRAB";

    /**
     * Denotes if this thread is paused.
     */
    private static boolean pause;

    /**
     * Denotes if status messages are printed to the console.
     */
    private static boolean debug;

    /**
     * The server's address.
     */
    public static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(43595);

    /**
     * The blocking mode of the channel.
     */
    public static final boolean SERVER_CHANNEL_BLOCKS = false;

    /**
     * The hash for the cyclical redundancy checks.
     */
    public static final int CRC32_HASH = 1234;

    /**
     * The maximum amount of index files the cache can contain.
     */
    public static final int MAXIMUM_INDEX_FILES = 0xFF;

    /**
     * The size of an index file.
     */
    public static final int INDEX_SIZE = 6;

    /**
     * The amount of bytes of data stored.
     */
    public static final int DATA_LENGTH = 512;

    /**
     * The amount of bytes that compose a header.
     */
    public static final int HEADER_LENGTH = 8;

    /**
     * The cumulative byte count.
     */
    public static final int TOTAL_LENGTH = DATA_LENGTH + HEADER_LENGTH;

    /**
     * The maximum amount of cache archives.
     */
    public static final int MAXIMUM_CACHE_ARCHIVES = 9;

    /**
     * The main cache data file.
     */
    private static RandomAccessFile main_cache_file;

    /**
     * The cache index files.
     */
    private static RandomAccessFile[] index_cache_files;

    private static final FileDemultiplexer serv_instance = new FileDemultiplexer();
    private static Selector serv_selector;
    private static ServerSocketChannel serv_channel;

    public static void main(String[] commands) {
        try {

            /**
             * The files in the cache directory.
             */
            File[] files_in_directory = new File(PATH_TO_CACHE).listFiles();

            /**
             * Counts the index files.
             */
            int index_file_count = (int) Stream.of(files_in_directory).filter(file -> file.getPath().contains(".idx")).count();

            if (index_file_count == 0)
                return;
            index_cache_files = new RandomAccessFile[index_file_count];
            main_cache_file = new RandomAccessFile(PATH_TO_CACHE + "/main_file_cache.dat", "r");
            for (int i = 0; i < index_file_count; i++) {
                index_cache_files[i] = new RandomAccessFile(PATH_TO_CACHE + "/main_file_cache.idx" + i, "r");
            }

            /**
             * The cache files have been successfully loaded.
             */
            System.out.print("\nSuccessfully loaded the main cache file and " + index_file_count + " .idx files.");
        } catch (FileNotFoundException exception) {

            /**
             * The cache files could not be found.
             */
            exception.printStackTrace(System.out);
        }
        try {
            serv_selector = Selector.open();
            serv_channel = ServerSocketChannel.open();

            serv_channel.configureBlocking(SERVER_CHANNEL_BLOCKS);
            serv_channel.register(serv_selector, SelectionKey.OP_ACCEPT);
            serv_channel.bind(SERVER_ADDRESS);
        } catch (IOException exception) {
            exception.printStackTrace(System.out);
        }

        /**
         * Starts the thread.
         */
        serv_instance.start();

        /**
         * The server has successfully started.
         */
        System.out.print("\nFile server has been started on " + serv_channel);
    }

    public static ByteBuffer crcs() throws IOException {
        final CRC32 crc_32 = new CRC32();
        final int[] checksums = new int[MAXIMUM_CACHE_ARCHIVES];
        checksums[0] = CLIENT_MODEL_NUMBER;
        for (int file_ = 1; file_ < checksums.length; file_++) {
            ByteBuffer file = getFile(0, file_).getData();

            /**
             * Resets to initial value.
             */
            crc_32.reset();

            /**
             * Updates the checksum with the specified array of bytes.
             */
            crc_32.update(file.array(), 0, file.limit());

            /**
             * The value for this file.
             */
            checksums[file_] = (int) crc_32.getValue();
        }

        int hash = CRC32_HASH;
        for (int i = 0; i < checksums.length; i++) {

            /**
             * Calculate the hash.
             */
            hash = (hash << 1) + checksums[i];
        }

        ByteBuffer buffer = ByteBuffer.allocate((checksums.length + 1) * Integer.BYTES);
        for (int i = 0; i < checksums.length; i++) {
            buffer.putInt(checksums[i]);
        }

        buffer.putInt(hash);
        buffer.flip();
        return buffer.asReadOnlyBuffer();
    }

    public static FileRequest getFile(int index, int file) throws IOException {

        /**
         * The index file of interest.
         */
        RandomAccessFile index_file = index_cache_files[index];

        /**
         * The data from that file.
         */
        ByteBuffer data = index_file.getChannel().map(MapMode.READ_ONLY, INDEX_SIZE * file, INDEX_SIZE);

        int file_length = ((data.get() & 0xFF) << 16) | ((data.get() & 0xFF) << 8) | (data.get() & 0xFF);
        int file_block = ((data.get() & 0xFF) << 16) | ((data.get() & 0xFF) << 8) | (data.get() & 0xFF);

        int remaining = file_length;
        int current_block = file_block;

        ByteBuffer file_buffer = ByteBuffer.allocate(file_length);
        while (remaining > 0) {

            /**
             * The current length.
             */
            int length = TOTAL_LENGTH;

            /**
             * The current position.
             */
            int position = (int) (main_cache_file.length() - current_block * TOTAL_LENGTH);
            length = position < TOTAL_LENGTH ? position : length;

            /**
             * The data for this block.
             */
            ByteBuffer block = main_cache_file.getChannel().map(MapMode.READ_ONLY, current_block * TOTAL_LENGTH, length);

            int next_file = block.getShort() & 0xFFFF;
            int current_chunk = block.getShort() & 0xFFFF;
            int next_block = ((block.get() & 0xFF) << 16) | ((block.get() & 0xFF) << 8) | (block.get() & 0xFF);
            int next_type = block.get() & 0xFF;

            /**
             * Four bytes and two words were just read.
             */
            length -= ((Byte.BYTES * 4) + (Short.BYTES * 2));

            int chunk_length = remaining;
            chunk_length = chunk_length > DATA_LENGTH ? DATA_LENGTH : chunk_length;

            byte[] chunk = new byte[chunk_length];
            block.get(chunk);
            file_buffer.put(chunk, 0, chunk_length);
            remaining -= chunk_length;

            current_block = next_block;
        }

        file_buffer.flip();
        return new FileRequest(index, file, file_buffer);
    }

    @Override
    public void run() {
        for (;;) {
            long start_time = System.currentTimeMillis();
            if (pause)
                continue;
            try {

                /**
                 * Selects a set of keys whose corresponding channels are ready
                 * for operations.
                 */
                serv_selector.select();
            } catch (IOException exception) {
                exception.printStackTrace(System.out);
            }
            for (Iterator<SelectionKey> $it
                    = serv_selector.selectedKeys().iterator(); $it.hasNext();) {
                SelectionKey token = $it.next();

                /**
                 * Determines if the key is valid.
                 */
                if (!token.isValid())
                    return;
                try {
                    
                    /**
                     * Determines if this connection has timed out.
                     */
                    timeout(token);

                    if (token.isAcceptable())

                        /**
                         * Accepts a new connection.
                         */
                        accept(token);
                    else if (token.isReadable())

                        /**
                         * Reads from an existing connection.
                         */
                        read(token);
                } finally {
                    $it.remove();
                }
            }
            long end_time = System.currentTimeMillis() - start_time;
            if (debug)
                System.out.print("\nTook " + end_time + " milliseconds to complete the iteration.");
        }
    }

    private static void timeout(SelectionKey token) {
        RequestingClient req_client = (RequestingClient) token.attachment();
        if (req_client == null)
            return;

        if ((System.currentTimeMillis() - req_client.last_read) < TIME_OUT_DELAY) {

            /**
             * If no data has been received in this time-frame, disconnect this
             * client.
             */
            req_client.disconnect();
        }
    }

    private static void read(SelectionKey token) {
        RequestingClient req_client = (RequestingClient) token.attachment();
        if (req_client == null)
            return;

        /**
         * Reads bytes from the client's channel.
         */
        req_client.read();
    }

    private static void accept(SelectionKey token) {
        try {
            SocketChannel priv_channel = serv_channel.accept();
            if (priv_channel == null)
                return;

            priv_channel.configureBlocking(SERVER_CHANNEL_BLOCKS);
            SelectionKey priv_token = priv_channel.register(serv_selector, SelectionKey.OP_READ);
            priv_token.attach(new RequestingClient(priv_token, priv_channel));
        } catch (IOException exception) {
            exception.printStackTrace(System.out);
        }
    }
}

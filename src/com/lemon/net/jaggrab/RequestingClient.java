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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class RequestingClient {

    /**
     * The registration token.
     */
    private final SelectionKey token;

    /**
     * The connectable channel.
     */
    private final SocketChannel socket;

    /**
     * The inbound data.
     */
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    /**
     * Denotes if this connection has been disconnected.
     */
    private boolean disconnected;

    /**
     * The last time a byte was read.
     */
    public long last_read = 0;

    public RequestingClient(SelectionKey token, SocketChannel socket) {
        this.token = token;
        this.socket = socket;
    }

    public void disconnect() {
        if (disconnected)
            return;
        disconnected = true;
        try {
            socket.close();
        } catch (IOException exception) {
            exception.printStackTrace(System.out);
        } finally {
            token.cancel();
        }
    }

    public int write(ByteBuffer response) throws IOException {

        /**
         * The amount of bytes that were written.
         */
        int bytes = 0;

        /**
         * Forcefully writes the contents of this response.
         */
        while (response.hasRemaining()) {
            bytes += socket.write(response);
        }

        return bytes;
    }

    public void read() {
        buffer.clear();
        try {
            if (socket.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    String request = StandardCharsets.UTF_8.decode(buffer).toString();
                    if (request.startsWith("JAGGRAB /")) {
                        
                        /**
                         * Denotes the client's current archive interest.
                         */
                        request = request.substring(FileDemultiplexer.REQUEST_PREFIX.length()).trim();

                        /**
                         * Serves the cyclical redundancy checks.
                         */
                        if (request.startsWith("/crc")) {
                            write(FileDemultiplexer.crcs());

                            /**
                             * Serves the title screen.
                             */
                        } else if (request.startsWith("/title")) {
                            write(FileDemultiplexer.getFile(0, 1).getData());

                            /**
                             * Serves the configurations.
                             */
                        } else if (request.startsWith("/config")) {
                            write(FileDemultiplexer.getFile(0, 2).getData());

                            /**
                             * Serves the interface file.
                             */
                        } else if (request.startsWith("/interface")) {
                            write(FileDemultiplexer.getFile(0, 3).getData());

                            /**
                             * Serves the media files.
                             */
                        } else if (request.startsWith("/media")) {
                            write(FileDemultiplexer.getFile(0, 4).getData());

                            /**
                             * Serves the version lists.
                             */
                        } else if (request.startsWith("/versionlist")) {
                            write(FileDemultiplexer.getFile(0, 5).getData());

                            /**
                             * Serves the textures.
                             */
                        } else if (request.startsWith("/textures")) {
                            write(FileDemultiplexer.getFile(0, 6).getData());

                            /**
                             * Serves the chat filter.
                             */
                        } else if (request.startsWith("/wordenc")) {
                            write(FileDemultiplexer.getFile(0, 7).getData());

                            /**
                             * Serves the sounds.
                             */
                        } else if (request.startsWith("/sounds")) {
                            write(FileDemultiplexer.getFile(0, 8).getData());

                            /**
                             * Can't find the archive.
                             */
                        } else {
                            System.out.println("Unable to decode " + request + ".");
                        }
                    }
                    last_read = System.currentTimeMillis();
                }
            }
        } catch (IOException exception) {

            /**
             * Exception occurred. Disconnect.
             */
            disconnect();
        } finally {

            /**
             * Request was served. Disconnect.
             */
            disconnect();
        }
    }
}

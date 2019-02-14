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

import java.nio.ByteBuffer;

public class FileRequest {

    private final int index;
    private final int file;
    private final ByteBuffer data;

    public FileRequest(int index, int file, ByteBuffer data) {
        this.index = index;
        this.file = file;
        this.data = data;
    }

    public int getIndex() {
        return index;
    }

    public int getFile() {
        return file;
    }

    public ByteBuffer getData() {
        return data;
    }
}

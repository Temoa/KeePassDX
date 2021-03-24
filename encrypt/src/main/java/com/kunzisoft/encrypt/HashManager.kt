/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.encrypt

import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object HashManager {

    fun getHash256(): MessageDigest {
        val messageDigest: MessageDigest
        try {
            messageDigest = MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IOException("SHA-256 not implemented here.", e)
        }
        return messageDigest
    }

    fun hashSha256(data: ByteArray, offset: Int = 0, count: Int = data.size): ByteArray {
        return hashGen("SHA-256", data, offset, count)
    }

    fun hashSha512(data: ByteArray, offset: Int = 0, count: Int = data.size): ByteArray {
        return hashGen("SHA-512", data, offset, count)
    }

    private fun hashGen(transform: String, data: ByteArray, offset: Int, count: Int): ByteArray {
        val hash: MessageDigest
        try {
            hash = MessageDigest.getInstance(transform)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        hash.update(data, offset, count)
        return hash.digest()
    }
}

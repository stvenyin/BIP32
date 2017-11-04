/*
 *  BIP32 library, a Java implementation of BIP32
 *  Copyright (C) 2017 Alan Evans, NovaCrypto
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *  Original source: https://github.com/NovaCrypto/BIP32
 *  You can contact the authors via github issues.
 */

package io.github.novacrypto.bip32;

import io.github.novacrypto.toruntime.CheckedExceptionToRuntime;

import java.math.BigInteger;
import java.util.Arrays;

import static io.github.novacrypto.bip32.BigIntegerUtils.parse256;
import static io.github.novacrypto.bip32.BigIntegerUtils.ser256;
import static io.github.novacrypto.bip32.ByteArrayWriter.head32;
import static io.github.novacrypto.bip32.ByteArrayWriter.tail32;
import static io.github.novacrypto.bip32.HmacSha512.hmacSha512;
import static io.github.novacrypto.bip32.Secp256k1BC.n;
import static io.github.novacrypto.toruntime.CheckedExceptionToRuntime.toRuntime;

/**
 * A BIP32 private key
 */
public final class PrivateKey implements
        CKDpriv,
        CKDpub,
        ToByteArray {

    private static final Derivation<PrivateKey> DERIVATION = new Derivation<>(new Derivation.Visitor<PrivateKey>() {
        @Override
        public PrivateKey visit(final PrivateKey parent, final int childIndex) {
            return parent.cKDpriv(childIndex);
        }
    });

    private static final byte[] BITCOIN_SEED = getBytes("Bitcoin seed");

    private final HdKey hdKey;

    private PrivateKey(final Network network, final byte[] key, final byte[] chainCode) {
        this(new HdKey.Builder()
                .network(network)
                .neutered(false)
                .key(key)
                .chainCode(chainCode)
                .depth(0)
                .childNumber(0)
                .parentFingerprint(0)
                .build());
    }

    private PrivateKey(final HdKey hdKey) {
        this.hdKey = hdKey;
    }

    public static PrivateKey fromSeed(final byte[] seed, final Network network) {
        final byte[] I = hmacSha512(BITCOIN_SEED, seed);

        final byte[] Il = head32(I);
        final byte[] Ir = tail32(I);

        return new PrivateKey(network, Il, Ir);
    }

    private static byte[] getBytes(final String seed) {
        return toRuntime(new CheckedExceptionToRuntime.Func<byte[]>() {
            @Override
            public byte[] run() throws Exception {
                return seed.getBytes("UTF-8");
            }
        });
    }

    @Override
    public byte[] toByteArray() {
        return hdKey.serialize();
    }

    @Override
    public PrivateKey cKDpriv(final int index) {
        final byte[] data = new byte[37];
        final ByteArrayWriter writer = new ByteArrayWriter(data);

        if (Index.hardened(index)) {
            writer.concat((byte) 0);
            writer.concat(hdKey.getKey(), 32);
        } else {
            writer.concat(hdKey.getPoint());
        }
        writer.concatSer32(index);

        final byte[] I = hmacSha512(hdKey.getChainCode(), data);
        Arrays.fill(data, (byte) 0);

        final byte[] Il = head32(I);
        final byte[] Ir = tail32(I);

        final byte[] key = hdKey.getKey();
        final BigInteger mod = parse256(Il).add(parse256(key)).mod(n());

        ser256(Il, mod);

        return new PrivateKey(new HdKey.Builder()
                .network(hdKey.getNetwork())
                .neutered(false)
                .key(Il)
                .chainCode(Ir)
                .depth(hdKey.depth() + 1)
                .childNumber(index)
                .parentFingerprint(hdKey.calculateFingerPrint())
                .build());
    }

    @Override
    public PublicKey cKDpub(final int index) {
        return cKDpriv(index).neuter();
    }

    public PublicKey neuter() {
        return PublicKey.from(hdKey);
    }

    public PrivateKey derive(final CharSequence derivationPath) {
        return DERIVATION.derive(this, derivationPath);
    }
}
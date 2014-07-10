/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.protocols.http2;

import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_CONTINUATION;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_DATA;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_GOAWAY;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_HEADERS;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_PUSH_PROMISE;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_RST_STREAM;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_SETTINGS;
import static io.undertow.protocols.http2.Http2Channel.FRAME_TYPE_WINDOW_UPDATE;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.xnio.Bits;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;

/**
 * @author Stuart Douglas
 */
class Http2FrameHeaderParser implements FrameHeaderData {

    final byte[] header = new byte[9];
    int read = 0;

    int length;
    int type;
    int flags;
    int streamId;

    Http2PushBackParser parser = null;

    private static final int SECOND_RESERVED_MASK = ~(1 << 7);
    private Http2Channel http2Channel;

    public Http2FrameHeaderParser(Http2Channel http2Channel) {
        this.http2Channel = http2Channel;
    }

    public boolean handle(final ByteBuffer byteBuffer) throws IOException {
        if (parser == null) {
            if (!parseFrameHeader(byteBuffer)) {
                return false;
            }
            switch (type) {
                case FRAME_TYPE_DATA: {
                    parser = new Http2DataFrameParser(length);
                    break;
                }
                case FRAME_TYPE_HEADERS: {
                    parser = new Http2HeadersParser( length, http2Channel.getDecoder());
                    break;
                }
                case FRAME_TYPE_RST_STREAM: {
                    parser = new Http2RstStreamParser(length);
                    break;
                }
                case FRAME_TYPE_CONTINUATION: {
                    //parser = new Http2HeadersParser(http2Channel.getBufferPool(), http2Channel, length, inflater);
                    throw new RuntimeException("NYI"); //TODO: continuations
                }
                case FRAME_TYPE_PUSH_PROMISE: {
                    throw new RuntimeException("NYI"); //TODO: push promise
                    // break;
                }
                case FRAME_TYPE_GOAWAY: {
                    parser = new Http2GoAwayParser(length);
                    break;
                }
                case Http2Channel.FRAME_TYPE_PING: {
                    if (length != 8) {
                        throw new ConnectionErrorException(Http2Channel.ERROR_FRAME_SIZE_ERROR, UndertowMessages.MESSAGES.invalidPingSize());
                    }
                    if (streamId != 0) {
                        throw new ConnectionErrorException(Http2Channel.ERROR_PROTOCOL_ERROR, UndertowMessages.MESSAGES.streamIdMustBeZeroForFrameType(Http2Channel.FRAME_TYPE_PING));
                    }
                    parser = new Http2PingParser(length);
                    break;
                }
                case FRAME_TYPE_SETTINGS: {
                    parser = new Http2SettingsParser(length);
                    break;
                }
                case FRAME_TYPE_WINDOW_UPDATE: {
                    parser = new Http2WindowUpdateParser(length);
                    break;
                }
                default: {
                    return true;
                }
            }
        }
        parser.parse(byteBuffer, this);
        return parser.isFinished();
    }

    private boolean parseFrameHeader(ByteBuffer byteBuffer) {
        while (read < 9 && byteBuffer.hasRemaining()) {
            header[read++] = byteBuffer.get();
        }
        if (read != 9) {
            return false;
        }
        length = (header[0] & 0xFF) << 16;
        length += (header[1] & 0xff) << 8;
        length += header[2] & 0xff;
        type = header[3] & 0xff;
        flags = header[4] & 0xff;
        streamId = (header[5] & SECOND_RESERVED_MASK & 0xFF) << 24;
        streamId += (header[6] & 0xFF) << 16;
        streamId += (header[7] & 0xFF) << 8;
        streamId += (header[8] & 0xFF);
        return true;
    }

    @Override
    public long getFrameLength() {
        //we only consider data frames to have length, all other frames are fully consumed by header parsing
        if (type != FRAME_TYPE_DATA) {
            return 0;
        }
        return length;
    }

    @Override
    public AbstractFramedStreamSourceChannel<?, ?, ?> getExistingChannel() {
        if (type == FRAME_TYPE_DATA ||
                type == FRAME_TYPE_HEADERS ||
                type == Http2Channel.FRAME_TYPE_CONTINUATION ||
                type == Http2Channel.FRAME_TYPE_PRIORITY) {

            if (Bits.anyAreSet(flags, Http2Channel.DATA_FLAG_END_STREAM)) {
                return http2Channel.getIncomingStreams().remove(streamId);
            } else {
                return http2Channel.getIncomingStreams().get(streamId);
            }
        }
        return null;
    }
}
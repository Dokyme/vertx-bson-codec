package bson.vertx.eventbus;

import bson.vertx.MaxKey;
import bson.vertx.MinKey;
import bson.vertx.ObjectId;
import org.vertx.java.core.buffer.Buffer;

import java.util.*;
import java.util.regex.Pattern;

import static bson.vertx.eventbus.LE.*;

final class BSONCodec {

    private static final byte FLOAT = (byte) 0x01;
    private static final byte STRING = (byte) 0x02;
    private static final byte EMBEDDED_DOCUMENT = (byte) 0x03;
    private static final byte ARRAY = (byte) 0x04;
    private static final byte BINARY = (byte) 0x05;
    private static final byte BINARY_BINARY = (byte) 0x00;
    private static final byte BINARY_FUNCTION = (byte) 0x01;
    private static final byte BINARY_BINARY_OLD = (byte) 0x02;
    private static final byte BINARY_UUID_OLD = (byte) 0x03;
    private static final byte BINARY_UUID = (byte) 0x04;
    private static final byte BINARY_MD5 = (byte) 0x05;
    private static final byte BINARY_USERDEFINED = (byte) 0x80;
    @Deprecated
    private static final byte UNDEFINED = (byte) 0x06;
    private static final byte OBJECT_ID = (byte) 0x07;
    private static final byte BOOLEAN = (byte) 0x08;
    private static final byte UTC_DATETIME = (byte) 0x09;
    private static final byte NULL = (byte) 0x0A;
    private static final byte REGEX = (byte) 0x0B;
    @Deprecated
    private static final byte DBPOINTER = (byte) 0x0C;
    private static final byte JSCODE = (byte) 0x0D;
    @Deprecated
    private static final byte SYMBOL = (byte) 0x0E;
    private static final byte JSCODE_WS = (byte) 0x0F;
    private static final byte INT32 = (byte) 0x10;
    private static final byte TIMESTAMP = (byte) 0x11;
    private static final byte INT64 = (byte) 0x12;
    private static final byte MINKEY = (byte) 0xFF;
    private static final byte MAXKEY = (byte) 0x7F;

    private static void encodeType(Buffer buffer, byte type, String key) {
        appendByte(buffer, type);
        appendCString(buffer, key);
    }

    private static void encode(Buffer buffer, String key, Object value) {
        if (value == null) {
            encodeType(buffer, NULL, key);
        } else if (value instanceof Double) {
            encodeType(buffer, FLOAT, key);
            appendDouble(buffer, (Double) value);
        } else if (value instanceof String) {
            encodeType(buffer, STRING, key);
            appendString(buffer, (String) value);
        } else if (value instanceof Map) {
            encodeType(buffer, EMBEDDED_DOCUMENT, key);
            buffer.appendBuffer(encode((Map) value));
        } else if (value instanceof List) {
            encodeType(buffer, ARRAY, key);
            buffer.appendBuffer(encode((List) value));
        } else if (value instanceof UUID) {
            encodeType(buffer, BINARY, key);
            // append length
            appendInt(buffer, 16);
            appendByte(buffer, BINARY_UUID);
            // append data
            UUID uuid = (UUID) value;
            appendLong(buffer, uuid.getLeastSignificantBits());
            appendLong(buffer, uuid.getMostSignificantBits());
        } else if (value instanceof byte[]) {
            encodeType(buffer, BINARY, key);
            // append length
            byte[] data = (byte[]) value;
            appendInt(buffer, data.length);
            appendByte(buffer, BINARY_BINARY);
            // append data
            appendBytes(buffer, data);
        }
//            if (value instanceof ) {
//                encodeType(buffer, UNDEFINED, key);
//            }
        else if (value instanceof ObjectId) {
            encodeType(buffer, OBJECT_ID, key);
            // TODO: get bytes from ObjectId
            appendBytes(buffer, new byte[12]);
        } else if (value instanceof Boolean) {
            encodeType(buffer, BOOLEAN, key);
            appendBoolean(buffer, (Boolean) value);
        } else if (value instanceof Date) {
            encodeType(buffer, UTC_DATETIME, key);
            appendLong(buffer, ((Date) value).getTime());
        } else if (value instanceof Pattern) {
            encodeType(buffer, REGEX, key);
            Pattern pattern = (Pattern) value;
            appendCString(buffer, pattern.pattern());
            int iFlags = pattern.flags();
            StringBuilder flags = new StringBuilder();
            if ((iFlags & Pattern.CASE_INSENSITIVE) == Pattern.CASE_INSENSITIVE) {
                flags.append('i');
            }
            if ((iFlags & Pattern.MULTILINE) == Pattern.MULTILINE) {
                flags.append('m');
            }
            if ((iFlags & Pattern.DOTALL) == Pattern.DOTALL) {
                flags.append('s');
            }
            if ((iFlags & Pattern.UNICODE_CASE) == Pattern.UNICODE_CASE) {
                flags.append('u');
            }
            // TODO: convert flags to BSON flags x,l
            appendCString(buffer, flags.toString());
        }
//            if (value instanceof JSCode) {
//                encodeType(buffer, JSCODE, key);
//                continue;
//            }
//            if (value instanceof JSCodeWS) {
//                encodeType(buffer, JSCODE_WS, key);
//                continue;
//            }
        else if (value instanceof Integer) {
            encodeType(buffer, INT32, key);
            appendInt(buffer, (Integer) value);
        }
//            if (value instanceof Timestamp) {
//                encodeType(buffer, TIMESTAMP, key);
//                continue;
//            }
        else if (value instanceof Long) {
            encodeType(buffer, INT64, key);
            appendLong(buffer, (Long) value);
        } else if (value instanceof MinKey) {
            encodeType(buffer, MINKEY, key);
        } else if (value instanceof MaxKey) {
            encodeType(buffer, MAXKEY, key);
        } else {
            throw new RuntimeException("Dont know how to encode: " + value);
        }
    }

    public static Buffer encode(Map map) {
        Buffer buffer = new Buffer();
        // allocate space for the document length
        appendInt(buffer, 0);

        for (Object entry : map.entrySet()) {
            Map.Entry entrySet = (Map.Entry) entry;
            Object key = entrySet.getKey();
            if (!(key instanceof String)) {
                throw new RuntimeException("BSON only allows CString as key");
            }
            Object value = entrySet.getValue();
            encode(buffer, (String) key, value);
        }

        setInt(buffer, 0, buffer.length() + 1);
        appendByte(buffer, (byte) 0x00);
        return buffer;
    }

    public static Buffer encode(List list) {
        Buffer buffer = new Buffer();
        // allocate space for the document length
        appendInt(buffer, 0);

        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            encode(buffer, String.valueOf(i), value);
        }

        setInt(buffer, 0, buffer.length() + 1);
        appendByte(buffer, (byte) 0x00);
        return buffer;
    }

    public static Map<String, Object> decode(Buffer buffer) {
        return decodeDocument(buffer, 0);
    }

    private static Map<String, Object> decodeDocument(Buffer buffer, int pos) {

        // skip the last 0x00
        int length = pos + getInt(buffer, pos) - 1;
        if (length == pos) {
            return null;
        }
        pos += 4;

        Map<String, Object> document = new HashMap<>();

        while (pos < length) {
            // get type
            byte type = getByte(buffer, pos);
            pos++;
            String key = getCString(buffer, pos);
            pos += key.length() + 1;

            switch (type) {
                case FLOAT:
                    document.put(key, getDouble(buffer, pos));
                    pos += 8;
                    break;
                case STRING:
                    int utfLength = getInt(buffer, pos);
                    pos += 4;
                    document.put(key, getString(buffer, pos, utfLength - 1));
                    pos += utfLength;
                    break;
                case EMBEDDED_DOCUMENT:
                    int docLen = getInt(buffer, pos);
                    document.put(key, decodeDocument(buffer, pos));
                    pos += docLen;
                    break;
                case ARRAY:
                    int arrLen = getInt(buffer, pos);
                    document.put(key, decodeList(buffer, pos));
                    pos += arrLen;
                    break;
                case BINARY:
                case UNDEFINED:
                case OBJECT_ID:
                    throw new RuntimeException("Not Implemented");
                case BOOLEAN:
                    document.put(key, getBoolean(buffer, pos));
                    pos++;
                    break;
                case UTC_DATETIME:
                    document.put(key, new Date(getLong(buffer, pos)));
                    pos += 8;
                    break;
                case NULL:
                    document.put(key, null);
                    break;
                case REGEX:
                case DBPOINTER:
                case JSCODE:
                case SYMBOL:
                case JSCODE_WS:
                    throw new RuntimeException("Not Implemented");
                case INT32:
                    document.put(key, getInt(buffer, pos));
                    pos += 4;
                    break;
                case TIMESTAMP:
                    throw new RuntimeException("Not Implemented");
                case INT64:
                    document.put(key, getLong(buffer, pos));
                    pos += 8;
                    break;
                case MINKEY:
                    document.put(key, new MinKey());
                    break;
                case MAXKEY:
                    document.put(key, new MaxKey());
                    break;
            }
        }

        return document;
    }

    private static List<Object> decodeList(Buffer buffer, int pos) {
        // skip the last 0x00
        int length = pos + getInt(buffer, pos) - 1;
        if (length == pos) {
            return null;
        }
        pos += 4;

        List<Object> list = new LinkedList<>();

        while (pos < length) {
            // get type
            byte type = getByte(buffer, pos);
            pos++;
            String key = getCString(buffer, pos);
            pos += key.length() + 1;

            switch (type) {
                case FLOAT:
                    list.add(Integer.parseInt(key), getDouble(buffer, pos));
                    pos += 8;
                    break;
                case STRING:
                    int utfLength = getInt(buffer, pos);
                    pos += 4;
                    list.add(Integer.parseInt(key), getString(buffer, pos, utfLength - 1));
                    pos += utfLength;
                    break;
                case EMBEDDED_DOCUMENT:
                    int docLen = getInt(buffer, pos);
                    list.add(Integer.parseInt(key), decodeDocument(buffer, pos));
                    pos += docLen;
                    break;
                case ARRAY:
                    int arrLen = getInt(buffer, pos);
                    list.add(Integer.parseInt(key), decodeList(buffer, pos));
                    pos += arrLen;
                    break;
                case BINARY:
                case UNDEFINED:
                case OBJECT_ID:
                    throw new RuntimeException("Not Implemented");
                case BOOLEAN:
                    list.add(Integer.parseInt(key), getBoolean(buffer, pos));
                    pos++;
                    break;
                case UTC_DATETIME:
                    list.add(Integer.parseInt(key), new Date(getLong(buffer, pos)));
                    pos += 8;
                    break;
                case NULL:
                    list.add(Integer.parseInt(key), null);
                    break;
                case REGEX:
                case DBPOINTER:
                case JSCODE:
                case SYMBOL:
                case JSCODE_WS:
                    throw new RuntimeException("Not Implemented");
                case INT32:
                    list.add(Integer.parseInt(key), getInt(buffer, pos));
                    pos += 4;
                    break;
                case TIMESTAMP:
                    throw new RuntimeException("Not Implemented");
                case INT64:
                    list.add(Integer.parseInt(key), getLong(buffer, pos));
                    pos += 8;
                    break;
                case MINKEY:
                    list.add(Integer.parseInt(key), new MinKey());
                    break;
                case MAXKEY:
                    list.add(Integer.parseInt(key), new MaxKey());
                    break;
            }
        }

        return list;
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> test = new HashMap<>();
        test.put("hello", "world");
        test.put("PI", Math.PI);
        test.put("null", null);
        test.put("createDate", new Date(0));
        List<Object> list = new ArrayList<>();
        list.add("awesome");
        list.add(5.05);
        list.add(1986);
        list.add(true);
        list.add(null);
        list.add(new Date());
        test.put("BSON", list);

//        FileOutputStream out = new FileOutputStream("out.bin");
//        out.write(encode(test).getBytes());
//        out.close();
        System.out.println(decode(encode(test)));

//        Map<String, Object> test2 = new HashMap<>();
//        List list = new ArrayList();
//        list.add("awesome");
//        list.add(5.05);
//        list.add(1986);
//        test2.put("BSON", list);
//        decode(encode(test2));
//
////        FileOutputStream out2 = new FileOutputStream("out2.bin");
////        out2.write(encode(test2).getBytes());
////        out2.close();
    }
}
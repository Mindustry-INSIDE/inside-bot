package inside.util.codec;

import inside.util.Preconditions;

import java.nio.charset.StandardCharsets;

public class Base64Coder{
    public static final CharMap regularMap = new CharMap('+', '/');
    public static final CharMap urlsafeMap = new CharMap('-', '_');

    private Base64Coder(){

    }

    public static String encodeString(String s){
        return encodeString(s, false);
    }

    public static String encodeString(String s, boolean useUrlsafeEncoding){
        return new String(encode(s.getBytes(StandardCharsets.UTF_8),
                useUrlsafeEncoding ? urlsafeMap.encodingMap : regularMap.encodingMap));
    }
    public static String encodeLines(byte[] in){
        return encodeLines(in, 0, in.length, 76, System.lineSeparator(), regularMap.encodingMap);
    }

    public static String encodeLines(byte[] in, int iOff, int iLen, int lineLen, String lineSeparator, CharMap charMap){
        return encodeLines(in, iOff, iLen, lineLen, lineSeparator, charMap.encodingMap);
    }

    public static String encodeLines(byte[] in, int iOff, int iLen, int lineLen, String lineSeparator, char[] charMap){
        int blockLen = lineLen * 3 / 4;
        Preconditions.requireArgument(blockLen > 0);

        int lines = (iLen + blockLen - 1) / blockLen;
        int bufLen = (iLen + 2) / 3 * 4 + lines * lineSeparator.length();
        StringBuilder buf = new StringBuilder(bufLen);
        int ip = 0;
        while(ip < iLen){
            int l = Math.min(iLen - ip, blockLen);
            buf.append(encode(in, iOff + ip, l, charMap));
            buf.append(lineSeparator);
            ip += l;
        }
        return buf.toString();
    }

    public static char[] encode(byte[] in){
        return encode(in, regularMap.encodingMap);
    }

    public static char[] encode(byte[] in, CharMap charMap){
        return encode(in, 0, in.length, charMap);
    }

    public static char[] encode(byte[] in, char[] charMap){
        return encode(in, 0, in.length, charMap);
    }

    public static char[] encode(byte[] in, int iLen){
        return encode(in, 0, iLen, regularMap.encodingMap);
    }

    public static char[] encode(byte[] in, int iOff, int iLen, CharMap charMap){
        return encode(in, iOff, iLen, charMap.encodingMap);
    }

    public static char[] encode(byte[] in, int iOff, int iLen, char[] charMap){
        int oDataLen = (iLen * 4 + 2) / 3; // output length without padding
        int oLen = (iLen + 2) / 3 * 4; // output length including padding
        char[] out = new char[oLen];
        int ip = iOff;
        int iEnd = iOff + iLen;
        int op = 0;
        while(ip < iEnd){
            int i0 = in[ip++] & 0xff;
            int i1 = ip < iEnd ? in[ip++] & 0xff : 0;
            int i2 = ip < iEnd ? in[ip++] & 0xff : 0;
            int o0 = i0 >>> 2;
            int o1 = (i0 & 3) << 4 | i1 >>> 4;
            int o2 = (i1 & 0xf) << 2 | i2 >>> 6;
            int o3 = i2 & 0x3F;
            out[op++] = charMap[o0];
            out[op++] = charMap[o1];
            out[op] = op < oDataLen ? charMap[o2] : '=';
            op++;
            out[op] = op < oDataLen ? charMap[o3] : '=';
            op++;
        }
        return out;
    }

    public static String decodeString(String s){
        return decodeString(s, false);
    }

    public static String decodeString(String s, boolean useUrlSafeEncoding){
        return new String(decode(s.toCharArray(), useUrlSafeEncoding ? urlsafeMap.decodingMap : regularMap.decodingMap));
    }

    public static byte[] decodeLines(String s){
        return decodeLines(s, regularMap.decodingMap);
    }

    public static byte[] decodeLines(String s, CharMap inverseCharMap){
        return decodeLines(s, inverseCharMap.decodingMap);
    }

    public static byte[] decodeLines(String s, byte[] inverseCharMap){
        char[] buf = new char[s.length()];
        int p = 0;
        for(int ip = 0; ip < s.length(); ip++){
            char c = s.charAt(ip);
            if(c != ' ' && c != '\r' && c != '\n' && c != '\t'){
                buf[p++] = c;
            }
        }
        return decode(buf, 0, p, inverseCharMap);
    }

    public static byte[] decode(String s){
        return decode(s.toCharArray());
    }

    public static byte[] decode(String s, CharMap inverseCharMap){
        return decode(s.toCharArray(), inverseCharMap);
    }

    public static byte[] decode(char[] in, byte[] inverseCharMap){
        return decode(in, 0, in.length, inverseCharMap);
    }

    public static byte[] decode(char[] in, CharMap inverseCharMap){
        return decode(in, 0, in.length, inverseCharMap);
    }

    public static byte[] decode(char[] in){
        return decode(in, 0, in.length, regularMap.decodingMap);
    }

    public static byte[] decode(char[] in, int iOff, int iLen, CharMap inverseCharMap){
        return decode(in, iOff, iLen, inverseCharMap.decodingMap);
    }

    public static byte[] decode(char[] in, int iOff, int iLen, byte[] inverseCharMap){
        Preconditions.requireArgument(iLen % 4 == 0, "Length of Base64 encoded input string is not a multiple of 4.");

        while(iLen > 0 && in[iOff + iLen - 1] == '='){
            iLen--;
        }
        int oLen = iLen * 3 / 4;
        byte[] out = new byte[oLen];
        int ip = iOff;
        int iEnd = iOff + iLen;
        int op = 0;
        while(ip < iEnd){
            int i0 = in[ip++];
            int i1 = in[ip++];
            int i2 = ip < iEnd ? in[ip++] : 'A';
            int i3 = ip < iEnd ? in[ip++] : 'A';
            if(i0 > 127 || i1 > 127 || i2 > 127 || i3 > 127){
                throw new IllegalArgumentException("Illegal character in Base64 encoded data. " +
                        "One of the four characters are the latter characters in the ASCII table!");
            }
            int b0 = inverseCharMap[i0];
            int b1 = inverseCharMap[i1];
            int b2 = inverseCharMap[i2];
            int b3 = inverseCharMap[i3];
            if(b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0){
                throw new IllegalArgumentException("Illegal character in Base64 encoded data. " +
                        "One of the 4 characters are first characters in the ASCII table!");
            }
            int o0 = b0 << 2 | b1 >>> 4;
            int o1 = (b1 & 0xf) << 4 | b2 >>> 2;
            int o2 = (b2 & 3) << 6 | b3;
            out[op++] = (byte)o0;
            if(op < oLen){
                out[op++] = (byte)o1;
            }
            if(op < oLen){
                out[op++] = (byte)o2;
            }
        }
        return out;
    }

    public static class CharMap{
        protected final char[] encodingMap = new char[64];
        protected final byte[] decodingMap = new byte[128];

        public CharMap(char char63, char char64){
            int i = 0;
            for(char c = 'A'; c <= 'Z'; c++){
                encodingMap[i++] = c;
            }
            for(char c = 'a'; c <= 'z'; c++){
                encodingMap[i++] = c;
            }
            for(char c = '0'; c <= '9'; c++){
                encodingMap[i++] = c;
            }
            encodingMap[i++] = char63;
            encodingMap[i++] = char64;
            for(i = 0; i < decodingMap.length; i++){
                decodingMap[i] = -1;
            }
            for(i = 0; i < 64; i++){
                decodingMap[encodingMap[i]] = (byte)i;
            }
        }

        public byte[] getDecodingMap(){
            return decodingMap;
        }

        public char[] getEncodingMap(){
            return encodingMap;
        }
    }
}

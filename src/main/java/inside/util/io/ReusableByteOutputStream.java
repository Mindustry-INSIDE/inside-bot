package inside.util.io;

import java.io.ByteArrayOutputStream;

public class ReusableByteOutputStream extends ByteArrayOutputStream{

    public ReusableByteOutputStream(int capacity){
        super(capacity);
    }

    public ReusableByteOutputStream(){}

    public byte[] getBytes(){
        return buf;
    }
}

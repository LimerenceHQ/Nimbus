package cc.nimbus.utils.helper;

public final class StringDecoder {
    private static final int DEFAULT_XOR_KEY=0x6C;
    private StringDecoder(){}
    public static String decode(String value){return decode(value,DEFAULT_XOR_KEY);}
    public static String decode(String value,int key){
        if(value==null||value.isEmpty())return "";
        if((value.length()&1)!=0)throw new IllegalArgumentException("Encoded hex length must be even");
        StringBuilder result=new StringBuilder(value.length()/2);
        for(int i=0;i<value.length();i+=2)result.append((char)(Integer.parseInt(value.substring(i,i+2),16)^key));
        return result.toString();
    }
    public static String encode(String value){return encode(value,DEFAULT_XOR_KEY);}
    public static String encode(String value,int key){
        if(value==null||value.isEmpty())return "";
        StringBuilder result=new StringBuilder(value.length()*2);
        for(int i=0;i<value.length();i++)result.append(String.format("%02x",value.charAt(i)^key));
        return result.toString();
    }
}

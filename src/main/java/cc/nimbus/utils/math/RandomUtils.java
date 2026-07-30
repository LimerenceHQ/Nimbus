package cc.nimbus.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtils {
    private RandomUtils(){}
    public static boolean nextBoolean(){return ThreadLocalRandom.current().nextBoolean();}
    public static int nextInt(int start,int end){return end<=start?start:ThreadLocalRandom.current().nextInt(start,end);}
    public static double nextDouble(double start,double end){return end<=start?start:ThreadLocalRandom.current().nextDouble(start,end);}
    public static float nextFloat(float start,float end){return end<=start?start:ThreadLocalRandom.current().nextFloat(start,end);}
    public static String random(int length,String chars){return randomString(length,chars);}
    public static String random(int length,char[] chars){return randomString(length,chars);}
    public static String randomNumber(int length){return randomString(length,"123456789");}
    public static String randomString(int length){return randomString(length,"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");}
    public static String randomString(int length,String chars){return randomString(length,chars.toCharArray());}
    public static String randomString(int length,char[] chars){if(length<0||chars.length==0)throw new IllegalArgumentException();StringBuilder value=new StringBuilder(length);for(int i=0;i<length;i++)value.append(chars[ThreadLocalRandom.current().nextInt(chars.length)]);return value.toString();}
    public static double getRandom(double min,double max){if(min>max){double swap=min;min=max;max=swap;}return nextDouble(min,max);}
    public static float getRandom(float min,float max){if(min>max){float swap=min;min=max;max=swap;}return nextFloat(min,max);}
    public static int getRandom(int min,int max){if(min>max){int swap=min;min=max;max=swap;}return min==max?min:ThreadLocalRandom.current().nextInt(min,max);}
}

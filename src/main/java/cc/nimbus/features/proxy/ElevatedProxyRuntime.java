package cc.nimbus.features.proxy;

import cc.nimbus.managers.ProxyManager;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class ElevatedProxyRuntime {
    private ElevatedProxyRuntime() {}
    public static void inject(ProxyManager.TargetProcess target,Path library,int port,Path directory) throws Exception {
        if(directory==null||!NativeProcessScanner.supported())throw new IOException("管理员加载需要 Windows x64");
        ProxyManager.validateTarget(target);
        Path nativeDir=directory.toAbsolutePath().resolve("native");Files.createDirectories(nativeDir);
        Path request=Files.createTempDirectory(nativeDir,"attach-request-");
        try {
        Path helper=request.resolve("attach-helper.jar"),result=request.resolve("result.properties");
        Path logDir = nativeDir.resolve("log");
        Files.createDirectories(logDir);
        Path log = Files.createTempFile(logDir, "nimbus-proxy-", ".log");
        try(var in=ElevatedProxyRuntime.class.getResourceAsStream("/assets/nimbus/native/windows-x86_64/nimbus_attach_helper.jar")){
            if(in==null)throw new IOException("Nimbus 未打包管理员加载助手，请重新构建");
            Files.copy(in,helper);
        }
        Path java=Path.of(System.getProperty("java.home"),"bin","javaw.exe").toAbsolutePath();
        if(!Files.isRegularFile(java))throw new IOException("当前 Java 运行时缺少 javaw.exe");
        byte[] nonce=new byte[32];new SecureRandom().nextBytes(nonce);String token=HexFormat.of().formatHex(nonce);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(library)));
        String[] arguments=arguments(helper,target,library,port,digest,result,token,log);
        int code=NativeProcessScanner.runElevated(directory,java,arguments,request);
        if(code==1223)throw new IOException("已取消管理员授权；未加载 A，点击后可重新尝试");
        if(!Files.isRegularFile(result))throw new IOException("加载助手未返回结果 (code "+code+")；请检查 Java 运行时和 A 日志");
        Properties response=new Properties();try(var in=Files.newBufferedReader(result,StandardCharsets.UTF_8)){response.load(in);}
        validateResponse(response,token,code);
        } finally {
            deleteTree(request);
        }
    }
    public static String[] arguments(Path helper,ProxyManager.TargetProcess target,Path library,int port,String digest,Path result,String token,Path log){
        return new String[]{"--disable-@files","--add-modules","jdk.attach","-jar",helper.toAbsolutePath().toString(),
            Long.toString(target.pid()),target.startedAt().toString(),Integer.toString(port),library.toAbsolutePath().toString(),digest,
            result.toAbsolutePath().toString(),token,Long.toString(ProcessHandle.current().pid()),log.toAbsolutePath().toString()};
    }
    private static void deleteTree(Path root) {
        try (var paths=Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch(IOException ignored) {} });
        } catch(IOException ignored) {}
    }
    public static void validateResponse(Properties response,String token,int code)throws IOException{
        if(!token.equals(response.getProperty("token")))throw new IOException("加载助手返回的任务标识不匹配");
        String stage=response.getProperty("stage","UNKNOWN");
        if(code!=0||!"0".equals(response.getProperty("exit"))||!"READY".equals(stage))
            throw new IOException("A 端管理员加载失败，阶段 "+stage+"："+response.getProperty("detail","未返回错误详情")+" (code "+code+")");
    }
}

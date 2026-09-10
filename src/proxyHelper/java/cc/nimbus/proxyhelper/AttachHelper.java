package cc.nimbus.proxyhelper;

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;

public final class AttachHelper {
    private AttachHelper() {}
    public static void main(String[] args) {
        if(args.length!=9){System.err.println("Expected one fixed Attach request");System.exit(64);return;}
        Path result=Path.of(args[5]).toAbsolutePath();
        String token=args[6];String stage="VALIDATE";int exit=2;String detail="";
        VirtualMachine vm=null;
        try {
            long pid=Long.parseLong(args[0]), parent=Long.parseLong(args[7]);
            int port=Integer.parseInt(args[2]);
            if(pid<=0||pid==parent||pid==ProcessHandle.current().pid()||port<1||port>65535||!token.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid Attach request");
            if(!ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false))throw new IllegalStateException("Nimbus has exited");
            Path library=Path.of(args[3]).toRealPath();
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(library)));
            if(!actual.equals(args[4]))throw new IllegalStateException("DLL SHA-256 mismatch");
            ProcessHandle target=ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElseThrow(()->new IllegalStateException("A has exited"));
            var info=target.info();
            String exe=Path.of(info.command().orElseThrow(()->new IllegalStateException("A executable unavailable"))).getFileName().toString();
            if(!exe.equalsIgnoreCase("java.exe")&&!exe.equalsIgnoreCase("javaw.exe"))throw new IllegalArgumentException("A must be a Java process");
            Instant expected=Instant.parse(args[1]);
            if(!expected.equals(Instant.EPOCH)&&!info.startInstant().filter(actualStart -> actualStart.toEpochMilli()==expected.toEpochMilli()).isPresent())throw new IllegalStateException("PID identity changed");
            stage="ATTACH";vm=VirtualMachine.attach(Long.toString(pid));
            Path log = Path.of(args[8]).toAbsolutePath();
            Files.createDirectories(log.getParent());
            stage="LOAD";vm.loadAgentPath(library.toString(),"port="+port+";log="+log);
            stage="DETACH";vm.detach();vm=null;
            stage="READY";detail="A listener ready on 127.0.0.1:"+port;exit=0;
        } catch(Exception|LinkageError e) { detail=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()); if(e instanceof com.sun.tools.attach.AgentInitializationException init)detail+=" (native code "+init.returnValue()+")"; }
        finally {if(vm!=null)try{vm.detach();}catch(Exception ignored){} }
        try {
            Properties data=new Properties();data.setProperty("token",token);data.setProperty("stage",stage);data.setProperty("detail",detail);data.setProperty("exit",Integer.toString(exit));
            Path temp=result.resolveSibling(result.getFileName()+".tmp");
            try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){data.store(out,"Nimbus Attach helper result");}
            Files.move(temp,result,StandardCopyOption.ATOMIC_MOVE);
        } catch(Exception e){System.err.println("Result delivery failed: "+e);exit=3;}
        System.exit(exit);
    }
}

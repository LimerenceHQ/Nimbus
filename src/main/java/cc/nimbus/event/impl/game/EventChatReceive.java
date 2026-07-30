package cc.nimbus.event.impl.game;
import net.minecraft.network.chat.Component;
import cc.nimbus.event.Cancelable;
public class EventChatReceive extends Cancelable { private Component message; private final long timestamp=System.currentTimeMillis(); public EventChatReceive(Component message){this.message=message;} public Component getMessage(){return message;} public void setMessage(Component value){message=value;} public String getMessageString(){return message==null?"":message.getString();} public long getTimestamp(){return timestamp;} }

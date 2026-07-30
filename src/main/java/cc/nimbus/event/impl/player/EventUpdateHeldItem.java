package cc.nimbus.event.impl.player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import cc.nimbus.event.Cancelable;
public class EventUpdateHeldItem extends Cancelable { private InteractionHand hand; private ItemStack item; public EventUpdateHeldItem(){} public EventUpdateHeldItem(InteractionHand hand,ItemStack item){this.hand=hand;this.item=item;} public EventUpdateHeldItem set(InteractionHand hand,ItemStack item){reset();this.hand=hand;this.item=item;return this;} public InteractionHand getHand(){return hand;} public ItemStack getItem(){return item;} public void setItem(ItemStack value){item=value;} }

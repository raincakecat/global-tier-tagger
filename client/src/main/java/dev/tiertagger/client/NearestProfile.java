package dev.tiertagger.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import java.util.*;
/** Select once, before vanilla consumes the shared P binding. */
public final class NearestProfile {
 public record Target(UUID uuid,String username,double distance){}
 public static KeyMapping key;
 private NearestProfile(){}
 public static Optional<Target> nearest(Minecraft client){
  if(client.player==null||client.level==null||client.getConnection()==null)return Optional.empty();
  return client.level.players().stream().filter(p->p!=client.player&&!p.isRemoved()&&p.isAlive()&&client.getConnection().getPlayerInfo(p.getUUID())!=null)
   .min(Comparator.comparingDouble((net.minecraft.world.entity.player.Player p)->client.player.distanceToSqr(p)).thenComparing(p->p.getUUID().toString()))
   .map(p->new Target(p.getUUID(),p.getName().getString(),Math.sqrt(client.player.distanceToSqr(p))));
 }
 public static boolean tick(Minecraft client){
  boolean pressed=false;while(key.consumeClick())pressed=true;
  if(!pressed||client.player==null||client.level==null||client.getConnection()==null||Platform.screen(client)!=null)return false;
  if(key.same(client.options.keySocialInteractions))while(client.options.keySocialInteractions.consumeClick()){}
  var target=nearest(client);if(target.isEmpty()){Platform.overlay(client,net.minecraft.network.chat.Component.literal("No other players loaded"));return true;}
  var screen=new SettingsScreen(null);client.setScreenAndShow(screen);screen.openNearestProfile(target.get());return true;
 }
}

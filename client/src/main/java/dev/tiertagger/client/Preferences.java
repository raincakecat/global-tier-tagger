package dev.tiertagger.client;
import com.google.gson.*; import dev.tiertagger.core.GameModes; import net.fabricmc.loader.api.FabricLoader; import java.nio.file.*;
public final class Preferences {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("multi-tier-tagger.json");
 public boolean setupComplete=false,enabled=true,nametags=true,tab=true;
 public java.util.Map<String,String> modeIcons=new java.util.HashMap<>();
 public String preferredSubtiersMode="minecart";
 public String sharedMode="crystal"; public Slot primary=new Slot("mctiers",null); public Slot secondary=new Slot("off",null); public Slot tertiary=new Slot("off",null);
 public static final class Slot {public String provider; public String modeOverride; public java.util.Map<String,String> iconOverrides=new java.util.HashMap<>(); public Slot(String p,String m){provider=p;modeOverride=m;} public String mode(String shared){return modeOverride==null?shared:modeOverride;}}
 public static Preferences load(){ Preferences p=null; try {if(Files.exists(FILE))p=JSON.fromJson(Files.readString(FILE),Preferences.class);}catch(Exception e){TierTagger.LOG.warn("Unable to read preferences; defaults used",e);} if(p==null)p=new Preferences(); p.normalize();return p; }
 private void normalize(){if(preferredSubtiersMode==null||!GameModes.ALL.contains(preferredSubtiersMode))preferredSubtiersMode="minecart";if(modeIcons==null)modeIcons=new java.util.HashMap<>();if(sharedMode == null || !GameModes.ALL.contains(sharedMode))sharedMode="crystal"; if(primary==null)primary=new Slot("mctiers",null); if(secondary==null)secondary=new Slot("off",null); if(tertiary==null)tertiary=new Slot("off",null); for(Slot s:new Slot[]{primary,secondary,tertiary}) {if(s.iconOverrides==null)s.iconOverrides=new java.util.HashMap<>();if(dev.tiertagger.core.ProviderCatalog.find(s.provider)==null||!dev.tiertagger.core.ProviderCatalog.find(s.provider).supported())s.provider="off";if(s.modeOverride != null && !GameModes.ALL.contains(s.modeOverride))s.modeOverride=null;} if(primary.provider.equals("off"))primary.provider="mctiers"; }
 public void save(){try {Files.createDirectories(FILE.getParent());Path tmp=Files.createTempFile(FILE.getParent(),"tier-tagger-",".tmp");try{Files.writeString(tmp,JSON.toJson(this));try{Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}catch(Exception e){TierTagger.LOG.warn("Unable to save preferences",e);}}
}

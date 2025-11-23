package golden.botc_mc.botc_mc.game.voice;

import java.util.UUID;

public class PersistentGroup {
    public String name;
    public String password;
    public boolean hidden;
    public boolean persistent = true;
    public String type = "NORMAL";
    public UUID voicechatId;

    public PersistentGroup() {}

    public PersistentGroup(String name) {
        this.name = name;
    }

    public String toString() {
        return "PersistentGroup[name="+name+",id="+voicechatId+",hidden="+hidden+",type="+type+"]";
    }
}


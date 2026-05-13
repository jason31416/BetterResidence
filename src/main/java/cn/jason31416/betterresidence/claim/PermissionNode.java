package cn.jason31416.betterresidence.claim;

import cn.jason31416.planetlib.wrapper.SimplePlayer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class PermissionNode {
    private final String claimUUID;
    private final String name;
    private final String material; // can be a direct Material or a material group's id. Consider material only for dev.
    private final int weight; // The node will only apply to group weights greater than or equal to this value
    private final boolean state;

    public String getPermissionKey(){
        if(material==null){
            return name;
        }
        return name+":"+material;
    }
}

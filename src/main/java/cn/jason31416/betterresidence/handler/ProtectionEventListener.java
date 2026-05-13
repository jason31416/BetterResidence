package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ProtectionEventListener implements Listener {
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        SimpleLocation location = SimpleLocation.of(event.getBlock());
        Claim claim = Claim.findClaimAt(location);
        if(claim==null){
            return;
        }

    }
}

package com.thomas15v.noxray;

import com.google.inject.Inject;
import com.thomas15v.noxray.api.BlockModifier;
import com.thomas15v.noxray.event.PlayerEventListener;
import com.thomas15v.noxray.modifier.GenerationModifier;
import org.spongepowered.api.Game;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.world.World;

@Plugin(id = "noxray", name = "NoXray", version = "0.3", authors = "thomas15v")
public class NoXrayPlugin {

    @Inject
    private Game game;

    private static NoXrayPlugin instance;

    public static NoXrayPlugin getInstance(){
        return instance;
    }

    private BlockModifier blockModifier;

    @Listener
    public void onStart(GameInitializationEvent event){
        blockModifier = new GenerationModifier();
        instance = this;
        game.getEventManager().registerListeners(this, new PlayerEventListener());
    }

    public BlockModifier getBlockModifier() {
        return blockModifier;
    }
}

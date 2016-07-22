package com.thomas15v.noxray;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.thomas15v.noxray.api.BlockModifier;
import com.thomas15v.noxray.config.NoXrayConfig;
import com.thomas15v.noxray.event.PlayerEventListener;
import com.thomas15v.noxray.modifier.GenerationModifier;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.spongepowered.api.Game;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;

import java.io.IOException;
import java.util.Optional;

@Plugin(id = "noxray", name = "NoXray", version = "0.3-beta", authors = "thomas15v")
public class NoXrayPlugin {

    @Inject
    private Game game;

    private static NoXrayPlugin instance;

    public static NoXrayPlugin getInstance(){
        return instance;
    }

    private BlockModifier blockModifier;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configLoader;

    private NoXrayConfig config;

    @Listener
    public void onStart(GameInitializationEvent event){
        loadConfig();
        blockModifier = new GenerationModifier();
        instance = this;
        game.getEventManager().registerListeners(this, new PlayerEventListener());


    }

    public BlockModifier getBlockModifier() {
        return blockModifier;
    }

    private void loadConfig(){

        TypeSerializers.getDefaultSerializers().registerType(TypeToken.of(BlockType.class), new TypeSerializer<BlockType>() {
            @Override
            public BlockType deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
                Optional<BlockType> blockTypeOptional = game.getRegistry().getType(BlockType.class, value.getString());
                if (blockTypeOptional.isPresent()){
                    return blockTypeOptional.get();
                }
                return null;
            }

            @Override
            public void serialize(TypeToken<?> type, BlockType obj, ConfigurationNode value) throws ObjectMappingException {
                value.setValue(obj.getName());
            }
        });


        try {
            CommentedConfigurationNode node = configLoader.load();
            config = new NoXrayConfig(node);
            if (config.isSave()) {
                configLoader.save(node);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

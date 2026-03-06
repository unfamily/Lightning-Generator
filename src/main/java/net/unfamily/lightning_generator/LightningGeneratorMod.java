package net.unfamily.lightning_generator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.ModBlockEntities;
import net.unfamily.lightning_generator.item.ModCreativeModeTabs;
import net.unfamily.lightning_generator.item.ModItems;

@Mod(LightningGeneratorMod.MOD_ID)
public class LightningGeneratorMod {

    public static final String MOD_ID = "lightning_generator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightningGeneratorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("iceandfire")) {
            net.unfamily.lightning_generator.integration.iceandfire.IceAndFireIntegration.register();
        }
    }
}

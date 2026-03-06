package net.unfamily.lightning_generator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.unfamily.lightning_generator.block.LightningGeneratorBlock;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;
import net.unfamily.lightning_generator.blockentity.ModBlockEntities;
import net.unfamily.lightning_generator.item.ModCreativeModeTabs;
import net.unfamily.lightning_generator.item.ModItems;

@Mod(LightningGeneratorMod.MOD_ID)
public class LightningGeneratorMod {

    public static final String MOD_ID = "lightning_generator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightningGeneratorMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LIGHTNING_GENERATOR_BE.get(),
                (be, direction) -> direction == be.getBlockState().getValue(LightningGeneratorBlock.FACING)
                        ? ((LightningGeneratorBlockEntity) be).getEnergyStorageForCapability() : null);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("iceandfire")) {
            net.unfamily.lightning_generator.integration.iceandfire.IceAndFireIntegration.register();
        }
    }
}

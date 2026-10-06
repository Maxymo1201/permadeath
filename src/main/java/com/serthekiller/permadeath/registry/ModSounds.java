package com.serthekiller.permadeath.registry;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** "permadeath:permadeath" (sounds.json + sounds/permadeath.ogg, unchanged from the Fabric mod). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PermadeathMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PERMADEATH = SOUNDS.register("permadeath",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, "permadeath")));

    private ModSounds() {
    }
}

package net.freak4u.terrainforged.forge;

import net.freak4u.terrainforged.mc.client.TerrainForgedPresetEditor;
import net.minecraftforge.client.event.RegisterPresetEditorsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/** Client-only registrations (kept in a separate class so dedicated servers never load client code). */
final class TerrainForgedForgeClient {
    private TerrainForgedForgeClient() {}

    static void register(BusGroup modBus) {
        RegisterPresetEditorsEvent.getBus(modBus).addListener(event -> TerrainForgedPresetEditor.editors().forEach(event::register));
    }
}

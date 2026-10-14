package net.freak4u.terrainforged.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.freak4u.terrainforged.mc.client.TerrainForgedPresetEditor;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Vanilla looks preset editors up in an immutable map ({@code PresetEditor.EDITORS}) and Fabric API has no hook
 * for it, so this enables the "Customize" button for the TerrainForged presets. Client only.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainforged$presetEditor(CallbackInfoReturnable<PresetEditor> cir) {
        var preset = ((WorldCreationUiState) (Object) this).getWorldType().preset();
        if (preset == null) return;
        preset.unwrapKey().map(TerrainForgedPresetEditor::forPreset).ifPresent(cir::setReturnValue);
    }
}

package com.hack337.omniisle.mixin.client;

import com.hack337.omniisle.client.OmniIsleEditorScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTypeEditorMixin {
    private static final Identifier ISLAND_PRESET = Identifier.fromNamespaceAndPath("omniisle", "omniisle");

    @Shadow @Final private Button customizeTypeButton;
    @Shadow @Final private CreateWorldScreen this$0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void omniisle$initializeButton(CreateWorldScreen screen, CallbackInfo ci) {
        omniisle$updateButton(screen.getUiState());
    }

    @Inject(method = "lambda$new$3", at = @At("TAIL"))
    private void omniisle$enableEditor(WorldCreationUiState creator, CallbackInfo ci) {
        omniisle$updateButton(creator);
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void omniisle$openEditor(CallbackInfo ci) {
        if (isIsland(this$0.getUiState())) {
            this$0.getUiState().onChanged();
            try {
                var context = this$0.getUiState().getSettings();
                if (context != null && context.worldgenLoadContext() != null) {
                    var biomeRegistry = context.worldgenLoadContext().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
                    com.hack337.omniisle.client.DynamicBiomeRegistry.updateFromRegistry(biomeRegistry);
                }
            } catch (Exception ignored) {}
            com.hack337.omniisle.client.ClientScreenNavigator.open(
                net.minecraft.client.Minecraft.getInstance(), new OmniIsleEditorScreen(this$0));
            ci.cancel();
        }
    }

    private void omniisle$updateButton(WorldCreationUiState creator) {
        boolean island = isIsland(creator);
        if (island) customizeTypeButton.active = true;
        customizeTypeButton.setMessage(Component.translatable(island ? "omniisle.editor.open" : "selectWorld.customizeType"));
    }

    private static boolean isIsland(WorldCreationUiState creator) {
        return creator.getWorldType().preset().unwrapKey()
            .map(key -> key.identifier().equals(ISLAND_PRESET)).orElse(false);
    }
}

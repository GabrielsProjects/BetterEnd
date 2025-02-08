package org.betterx.betterend.integration.trinkets;

import org.betterx.bclib.items.elytra.BCLElytraItem;
import org.betterx.bclib.items.elytra.BCLElytraUtils;

import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class Elytra {
    private static boolean isElytra(ItemStack stack) {
        return stack.getItem() instanceof ElytraItem
                || stack.getItem() instanceof FabricElytraItem;
    }

    public static void register() {
        BCLElytraUtils.slotProvider = (entity, slotGetter) -> {
            ItemStack itemStack = slotGetter.apply(EquipmentSlot.CHEST);
            if (isElytra(itemStack)) return itemStack;

            Optional<TrinketComponent> oTrinketComponent = TrinketsApi.getTrinketComponent(entity);
            if (oTrinketComponent.isPresent()) {
                List<Tuple<SlotReference, ItemStack>> equipped =
                        oTrinketComponent.get().getEquipped(Elytra::isElytra);

                if (!equipped.isEmpty()) return equipped.get(0).getB();
            }
            return null;
        };

BCLElytraUtils.onBreak = (entity, chestStack) -> {
    var oTrinketComponent = TrinketsApi.getTrinketComponent(entity);

    var possibleTrinketSlot = null;

    if (oTrinketComponent.isPresent()) {
        List<Tuple<SlotReference, ItemStack>> equipped = oTrinketComponent.get().getEquipped(Elytra::isElytra);

        for (var slot : equipped) {
            var slotStack = slot.getB();
            if (slotStack == chestStack) {
                possibleTrinketSlot = slot.getA();
                break;
            }
        }
    }
    
    if (possibleTrinketSlot == null) {
        chestStack.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
    } else if(entity instanceof ServerPlayer player) {
        // Mostly 1:1 copy of Fabric API handling for having custom damage handler for breaking: https://github.com/FabricMC/fabric/blob/5ca99b905c7ea1ddfe796066fc10b0d16e37d869/fabric-item-api-v1/src/main/java/net/fabricmc/fabric/mixin/item/ItemStackMixin.java#L49
        int hurtAmount = 1;

        Consumer<Item> onBreakConsumer = (item) -> {
            TrinketsApi.onTrinketBroken(chestStack, possibleTrinketSlot, player);
        };

        var handler = ((ItemExtensions)chestStack.getItem()).fabric_getCustomDamageHandler();
        if (handler != null && !player.isInCreativeMode()) {
            // Track whether an item has been broken by custom handler
            var isBroken = new MutableBoolean(false);

            hurtAmount = handler.damage(chestStack, amount, player, EquipmentSlot.CHEST, () -> {
                isBroken.setTrue();
                chestStack.decrement(1);
                onBreakConsumer.accept(this.getItem());
            });

              // If item is broken, there's no reason to call the original.
            if (isBroken.booleanValue()) return;
        } 

        chestStack.hurtAndBreak(hurtAmount, (ServerLevel) player.level(), player, onBreakConsumer);
    }
};

        EntityElytraEvents.CUSTOM.register(Elytra::useElytraTrinket);
    }

    private static boolean useElytraTrinket(LivingEntity entity, boolean tickElytra) {
        Optional<TrinketComponent> oTrinketComponent = TrinketsApi.getTrinketComponent(entity);
        if (oTrinketComponent.isPresent()) {
            List<Tuple<SlotReference, ItemStack>> equipped =
                    oTrinketComponent.get().getEquipped(Elytra::isElytra);

            for (Tuple<SlotReference, ItemStack> slot : equipped) {
                ItemStack stack = slot.getB();
                Item item = stack.getItem();

                if (item instanceof ElytraItem) {
                    if (ElytraItem.isFlyEnabled(stack)) {
                        BCLElytraItem.vanillaElytraTick(entity, stack);
                        return true;
                    }
                } else if (item instanceof FabricElytraItem fabricElytraItem) {
                    if (fabricElytraItem.useCustomElytra(entity, stack, tickElytra)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}



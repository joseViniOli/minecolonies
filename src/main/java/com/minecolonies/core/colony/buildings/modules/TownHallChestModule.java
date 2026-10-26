package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.InventoryUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * ZM: The shared chest of the colony, kept by the Town Hall. It is not a new block: it is the inventory the Town Hall already has as the "warehouse" of the building (the block of
 * the Town Hall is a rack, and the racks placed in its structure are part of the same inventory). Everything that works with the inventory of a building therefore works with it:
 * <ul>
 * <li>Villagers hosted by the Town Hall dump what they do not need to keep into it (the keep lists of their professions say what stays with them, so they never deposit the tools
 * of their own profession), and fetch their tools from it when the AI looks into "the hut".</li>
 * <li>The request system sees it as the inventory of the building: a request of a hosted villager is resolved from it, and the stacks that open requests need are reserved,
 * which is how reservations of this chest work (no separate reservation list).</li>
 * <li>The player donates by opening the inventory of the Town Hall (sneak and use the block, or the Colony chest button of the Town Hall window).</li>
 * </ul>
 * This module only adds what the Town Hall inventory lacks: the capacity of a double chest (54 slots, like the reference rules of the project) and a small deposit and withdraw
 * API for the code that works with the colony stock.
 */
public class TownHallChestModule extends AbstractBuildingModule implements ITickingModule
{
    /**
     * Slots of the shared chest, the size of a double chest.
     */
    public static final int CHEST_SLOTS = 54;

    /**
     * Upper limit of upgrade steps in one call, so a broken tile entity cannot loop forever.
     */
    private static final int MAX_UPGRADE_STEPS = 8;

    /**
     * Grows the inventory of the Town Hall to {@link #CHEST_SLOTS} once it is built.
     *
     * @param colony the colony that ticks.
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (building.getBuildingLevel() > 0)
        {
            ensureCapacity();
        }
    }

    /**
     * Make sure the inventory of the Town Hall has at least {@link #CHEST_SLOTS} slots. A rack grows by whole lines of nine slots, and keeps its content when it grows.
     *
     * @return true if the chest has the slots.
     */
    public boolean ensureCapacity()
    {
        final AbstractTileEntityColonyBuilding tile = building.getTileEntity();
        if (tile == null || tile.getLevel() == null)
        {
            return false;
        }

        int steps = 0;
        while (tile.getInventory().getSlots() < CHEST_SLOTS && steps++ < MAX_UPGRADE_STEPS)
        {
            tile.upgradeRackSize();
        }
        return tile.getInventory().getSlots() >= CHEST_SLOTS;
    }

    /**
     * The inventory of the chest, as the AI and the request system see it.
     *
     * @return the item handler, null while the Town Hall block entity is not loaded.
     */
    @Nullable
    public IItemHandler getHandler()
    {
        return building.getItemHandlerCap();
    }

    /**
     * Put a stack into the chest.
     *
     * @param stack the stack, not changed.
     * @return what did not fit, empty if everything was stored.
     */
    public ItemStack deposit(final ItemStack stack)
    {
        final IItemHandler handler = getHandler();
        if (handler == null || stack.isEmpty())
        {
            return stack.copy();
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    /**
     * Count the items in the chest that match.
     *
     * @param predicate the items to count.
     * @return the number of items.
     */
    public int count(final Predicate<ItemStack> predicate)
    {
        return InventoryUtils.getCountFromBuilding(building, predicate);
    }

    /**
     * Take items out of the chest. All items of the result are of the same kind: the first match decides.
     *
     * @param predicate the items to take.
     * @param amount    how many to take at most.
     * @return the stack taken, empty if nothing matched.
     */
    public ItemStack withdraw(final Predicate<ItemStack> predicate, final int amount)
    {
        final IItemHandler handler = getHandler();
        if (handler == null || amount <= 0)
        {
            return ItemStack.EMPTY;
        }

        ItemStack taken = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && taken.getCount() < amount; slot++)
        {
            final ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty() || !predicate.test(inSlot) || (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, inSlot)))
            {
                continue;
            }

            final ItemStack extracted = handler.extractItem(slot, amount - taken.getCount(), false);
            if (extracted.isEmpty())
            {
                continue;
            }
            if (taken.isEmpty())
            {
                taken = extracted;
            }
            else
            {
                taken.grow(extracted.getCount());
            }
        }
        return taken;
    }
}

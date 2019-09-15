package logisticspipes.logisticspipes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Direction;

import logisticspipes.interfaces.ISendRoutedItem;
import logisticspipes.interfaces.SlotUpgradeManager;
import logisticspipes.interfaces.WrappedInventory;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.utils.item.ItemIdentifier;
import network.rs485.logisticspipes.connection.NeighborBlockEntity;

public interface IInventoryProvider extends ISendRoutedItem {

	@Nullable
	WrappedInventory getPointedInventory();

	@Nullable
	WrappedInventory getPointedInventory(ExtractionMode mode);

	@Nullable
	WrappedInventory getSneakyInventory(ModulePositionType slot, int positionInt);

	@Nullable
	WrappedInventory getSneakyInventory(@Nonnull Direction direction);

	@Nullable
	NeighborBlockEntity<BlockEntity> getPointedItemHandler();

	@Nullable
	Direction getPointedOrientation();

	// to interact and send items you need to know about orders, upgrades, and have the ability to send
	LogisticsItemOrderManager getItemOrderManager();

	void queueRoutedItem(IRoutedItem routedItem, Direction from);

	SlotUpgradeManager getUpgradeManager(ModulePositionType slot, int positionInt);

	int countOnRoute(ItemIdentifier item);
}

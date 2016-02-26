package logisticspipes.routing.pathfinder;

import java.util.List;

import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.transport.LPTravelingItem;
import logisticspipes.utils.item.ItemIdentifier;

import network.rs485.logisticspipes.world.DoubleCoordinates;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

public interface IPipeInformationProvider {

	public enum ConnectionPipeType {
		ITEM,
		FLUID,
		BOTH;
	}

	public boolean isCorrect(ConnectionPipeType type);

	public int getX();

	public int getY();

	public int getZ();

	public World getWorld();

	public boolean isRouterInitialized();

	public boolean isRoutingPipe();

	public CoreRoutedPipe getRoutingPipe();

	public TileEntity getNextConnectedTile(ForgeDirection direction);

	public boolean isFirewallPipe();

	public IFilter getFirewallFilter();

	public TileEntity getTile();

	public boolean divideNetwork();

	public boolean powerOnly();

	public boolean isOnewayPipe();

	public boolean isOutputOpen(ForgeDirection direction);

	public boolean canConnect(TileEntity to, ForgeDirection direction, boolean flag);

	public double getDistance();

	public boolean isItemPipe();

	public boolean isFluidPipe();

	public boolean isPowerPipe();

	public double getDistanceTo(int destinationint, ForgeDirection ignore, ItemIdentifier ident, boolean isActive, double travled, double max, List<DoubleCoordinates> visited);

	public boolean acceptItem(LPTravelingItem item, TileEntity from);

	public void refreshTileCacheOnSide(ForgeDirection side);
}

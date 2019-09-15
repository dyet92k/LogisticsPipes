package logisticspipes.pipes.basic.fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Direction;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import logisticspipes.LPConstants;
import logisticspipes.interfaces.ITankUtil;
import logisticspipes.interfaces.routing.IRequireReliableFluidTransport;
import logisticspipes.logistics.LogisticsFluidManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.routing.order.LogisticsOrderManager;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.RoutedItemHelper;
import logisticspipes.utils.TankUtilFactory;
import logisticspipes.utils.item.ItemStack;
import logisticspipes.utils.tuples.Tuple2;
import logisticspipes.utils.tuples.Tuple3;
import network.rs485.logisticspipes.connection.NeighborBlockEntity;
import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

public abstract class FluidRoutedPipe extends CoreRoutedPipe {

	private LogisticsFluidOrderManager _orderFluidManager;

	public FluidRoutedPipe(Item item) {
		super(new PipeFluidTransportLogistics(), item);
	}

	@Override
	public void setTile(BlockEntity tile) {
		super.setTile(tile);
	}

	@Override
	public boolean logisitcsIsPipeConnected(BlockEntity tile, Direction dir) {
		if (SimpleServiceLocator.enderIOProxy.isBundledPipe(tile)) {
			return SimpleServiceLocator.enderIOProxy.isFluidConduit(tile, dir.getOpposite());
		}

		ITankUtil liq = TankUtilFactory.INSTANCE.getTankUtilForTE(tile, dir.getOpposite());
		return (liq != null && liq.containsTanks()) || tile instanceof LogisticsTileGenericPipe;
	}

	@Override
	public ItemSendMode getItemSendMode() {
		return ItemSendMode.Normal;
	}

	@Override
	public TextureType getNonRoutedTexture(Direction connection) {
		if (isFluidSidedTexture(connection)) {
			return Textures.LOGISTICSPIPE_LIQUID_TEXTURE;
		}
		return super.getNonRoutedTexture(connection);
	}

	private boolean isFluidSidedTexture(Direction connection) {
		final NeighborBlockEntity<BlockEntity> neighbor = new WorldCoordinatesWrapper(container).getNeighbor(connection);
		if (neighbor == null) return false;
		BlockEntity tileEntity = neighbor.getBlockEntity();
		ITankUtil liq = TankUtilFactory.INSTANCE.getTankUtilForTE(tileEntity, connection.getOpposite());
		return (liq != null && liq.containsTanks());
	}

	@Override
	public LogisticsModule getLogisticsModule() {
		return null;
	}

	/***
	 * @param flag
	 *            Weather to list a Nearby Pipe or not
	 */

	public final List<ITankUtil> getAdjacentTanks(boolean flag) {
		return new WorldCoordinatesWrapper(container).allNeighborTileEntities()
				.filter(adjacent -> isConnectableTank(adjacent.getBlockEntity(), adjacent.getDirection(), flag))
				.map(adjacent -> TankUtilFactory.INSTANCE.getTankUtilForTE(adjacent.getBlockEntity(), adjacent.getDirection()))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	/***
	 * @param flag
	 *            Weather to list a Nearby Pipe or not
	 */

	public final List<Tuple3<ITankUtil, BlockEntity, Direction>> getAdjacentTanksAdvanced(boolean flag) {
		return new WorldCoordinatesWrapper(container).allNeighborTileEntities()
				.filter(adjacent -> isConnectableTank(adjacent.getBlockEntity(), adjacent.getDirection(), flag))
				.map(adjacent -> new Tuple3<>(
						TankUtilFactory.INSTANCE.getTankUtilForTE(adjacent.getBlockEntity(), adjacent.getDirection()),
						adjacent.getBlockEntity(),
						adjacent.getDirection()))
				.filter(triplet -> triplet.getValue1() != null)
				.collect(Collectors.toList());
	}

	/***
	 * @param tile
	 *            The connected BlockEntity
	 * @param dir
	 *            The direction the BlockEntity is in relative to the currect
	 *            pipe
	 * @param flag
	 *            Weather to list a Nearby Pipe or not
	 */

	public final boolean isConnectableTank(BlockEntity tile, Direction dir, boolean flag) {
		if (SimpleServiceLocator.specialTankHandler.hasHandlerFor(tile)) {
			return true;
		}
		boolean fluidTile = false;
		if (tile != null && tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir)) {
			IFluidHandler fluidHandler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
			if (fluidHandler != null) {
				fluidTile = true;
			}
		}
		if (tile instanceof IFluidHandler) {
			fluidTile = true;
		}
		if (!fluidTile) {
			return false;
		}
		if (!this.canPipeConnect(tile, dir)) {
			return false;
		}
		if (tile instanceof LogisticsTileGenericPipe) {
			if (((LogisticsTileGenericPipe) tile).pipe instanceof FluidRoutedPipe) {
				return false;
			}
			if (!flag) {
				return false;
			}
			if (((LogisticsTileGenericPipe) tile).pipe == null || !(((LogisticsTileGenericPipe) tile).pipe.transport instanceof IFluidHandler)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void enabledUpdateEntity() {
		super.enabledUpdateEntity();
		if (canInsertFromSideToTanks()) {
			int validDirections = 0;
			List<Tuple3<ITankUtil, BlockEntity, Direction>> list = getAdjacentTanksAdvanced(true);
			for (Tuple3<ITankUtil, BlockEntity, Direction> pair : list) {
				if (pair.getValue2() instanceof LogisticsTileGenericPipe) {
					if (((LogisticsTileGenericPipe) pair.getValue2()).pipe instanceof CoreRoutedPipe) {
						continue;
					}
				}
				FluidTank internalTank = ((PipeFluidTransportLogistics) transport).sideTanks[pair.getValue3().ordinal()];
				validDirections++;
				if (internalTank.getFluid() == null) {
					continue;
				}
				ITankUtil externalTank = pair.getValue1();
				int filled = externalTank.fill(FluidIdentifierStack.getFromStack(internalTank.getFluid()), true);
				if (filled == 0) {
					continue;
				}
				FluidStack drain = internalTank.drain(filled, true);
				if (drain == null || filled != drain.amount) {
					if (LPConstants.DEBUG) {
						throw new UnsupportedOperationException("Fluid Multiplication");
					}
				}
			}
			if (validDirections == 0) {
				return;
			}
			FluidTank tank = ((PipeFluidTransportLogistics) transport).internalTank;
			FluidStack stack = tank.getFluid();
			if (stack == null) {
				return;
			}
			for (Tuple3<ITankUtil, BlockEntity, Direction> pair : list) {
				if (pair.getValue2() instanceof LogisticsTileGenericPipe) {
					if (((LogisticsTileGenericPipe) pair.getValue2()).pipe instanceof CoreRoutedPipe) {
						continue;
					}
				}
				FluidTank tankSide = ((PipeFluidTransportLogistics) transport).sideTanks[pair.getValue3().ordinal()];
				stack = tank.getFluid();
				if (stack == null) {
					continue;
				}
				stack = stack.copy();
				int filled = tankSide.fill(stack, true);
				if (filled == 0) {
					continue;
				}
				FluidStack drain = tank.drain(filled, true);
				if (drain == null || filled != drain.amount) {
					if (LPConstants.DEBUG) {
						throw new UnsupportedOperationException("Fluid Multiplication");
					}
				}
			}
		}
	}

	public int countOnRoute(FluidIdentifier ident) {
		int amount = 0;
		for (ItemRoutingInformation next : _inTransitToMe) {
			ItemStack item = next.getItem();
			if (item.getItem().isFluidContainer()) {
				FluidIdentifierStack liquid = LogisticsFluidManager.getInstance().getFluidFromContainer(item);
				if (liquid.getFluid().equals(ident)) {
					amount += liquid.getAmount();
				}
			}
		}
		return amount;
	}

	public abstract boolean canInsertFromSideToTanks();

	public abstract boolean canInsertToTanks();

	public abstract boolean canReceiveFluid();

	public boolean endReached(LPTravelingItemServer arrivingItem, BlockEntity tile) {
		if (canInsertToTanks() && !getWorld().isClient()) {
			getCacheHolder().trigger(CacheTypes.Inventory);
			if (arrivingItem.getItemStack() == null || !(arrivingItem.getItemStack().getItem().isFluidContainer())) {
				return false;
			}
			if (getRouter().getSimpleId() != arrivingItem.getDestination()) {
				return false;
			}
			int filled;
			FluidIdentifierStack liquid = LogisticsFluidManager.getInstance().getFluidFromContainer(arrivingItem.getItemStack());
			if (isConnectableTank(tile, arrivingItem.output, false)) {
				List<ITankUtil> adjTanks = getAdjacentTanks(false);
				// Try to put liquid into all adjacent tanks.
				for (ITankUtil util : adjTanks) {
					filled = util.fill(liquid, true);
					liquid.lowerAmount(filled);
					if (liquid.getAmount() != 0) {
						continue;
					}
					return true;
				}
				// Try inserting the liquid into the pipe side tank
				filled = ((PipeFluidTransportLogistics) transport).sideTanks[arrivingItem.output.ordinal()].fill(liquid.makeFluidStack(), true);
				if (filled == liquid.getAmount()) {
					return true;
				}
				liquid.lowerAmount(filled);
			}
			// Try inserting the liquid into the pipe internal tank
			filled = ((PipeFluidTransportLogistics) transport).internalTank.fill(liquid.makeFluidStack(), true);
			if (filled == liquid.getAmount()) {
				return true;
			}
			// If liquids still exist,
			liquid.lowerAmount(filled);

			// TODO: FIX THIS
			if (this instanceof IRequireReliableFluidTransport) {
				((IRequireReliableFluidTransport) this).liquidNotInserted(liquid.getFluid(), liquid.getAmount());
			}

			IRoutedItem routedItem = RoutedItemHelper.INSTANCE.createNewTravelItem(LogisticsFluidManager.getInstance().getFluidContainer(liquid));
			Tuple2<Integer, Integer> replies = LogisticsFluidManager.getInstance().getBestReply(liquid, getRouter(), routedItem.getJamList());
			int dest = replies.getValue1();
			routedItem.setDestination(dest);
			routedItem.setTransportMode(TransportMode.Passive);
			this.queueRoutedItem(routedItem, arrivingItem.output.getOpposite());
			return true;
		}
		return false;
	}

	@Override
	public boolean isFluidPipe() {
		return true;
	}

	@Override
	public boolean sharesInterestWith(CoreRoutedPipe other) {
		if (!(other instanceof FluidRoutedPipe)) {
			return false;
		}
		List<BlockEntity> theirs = ((FluidRoutedPipe) other).getAllTankTiles();
		for (BlockEntity tile : getAllTankTiles()) {
			if (theirs.contains(tile)) {
				return true;
			}
		}
		return false;
	}

	public List<BlockEntity> getAllTankTiles() {
		List<BlockEntity> list = new ArrayList<>();
		for (Tuple3<ITankUtil, BlockEntity, Direction> pair : getAdjacentTanksAdvanced(false)) {
			list.addAll(SimpleServiceLocator.specialTankHandler.getBaseTileFor(pair.getValue2()));
		}
		return list;
	}

	public LogisticsFluidOrderManager getFluidOrderManager() {
		_orderFluidManager = _orderFluidManager != null ? _orderFluidManager : new LogisticsFluidOrderManager(this);
		return _orderFluidManager;
	}

	@Override
	public LogisticsOrderManager<?, ?> getOrderManager() {
		return getFluidOrderManager();
	}
}

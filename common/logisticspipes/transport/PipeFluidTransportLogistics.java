package logisticspipes.transport;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.Direction;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.pipe.PipeFluidUpdate;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.pathfinder.PipeInformationManager;
import logisticspipes.utils.SafeTimeTracker;
import logisticspipes.utils.item.ItemStack;

public class PipeFluidTransportLogistics extends PipeTransportLogistics {

	public FluidTank[] sideTanks = new FluidTank[Direction.values().length];
	public FluidTank internalTank = new FluidTank(getInnerCapacity());

	public FluidStack[] renderCache = new FluidStack[7];

	public PipeFluidTransportLogistics() {
		super(true);
		for (Direction dir : Direction.values()) {
			sideTanks[dir.ordinal()] = new FluidTank(getSideCapacity());
		}
	}

	public IFluidHandler getIFluidHandler(Direction face) {
		return new FluidHandler(face);
	}

	private FluidRoutedPipe getFluidPipe() {
		return (FluidRoutedPipe) getPipe();
	}

	/**
	 * For internal use only
	 */
	public IFluidTankProperties[] getTankProperties(Direction from) {
		if (from == null) return internalTank.getTankProperties();
		return sideTanks[from.ordinal()].getTankProperties();
	}

	public int fill(Direction from, FluidStack resource, boolean doFill) {
		if (from.ordinal() < Direction.values().length && getFluidPipe().canReceiveFluid()) {
			return sideTanks[from.ordinal()].fill(resource, doFill);
		} else {
			return 0;
		}
	}

	public FluidStack drain(Direction from, int maxDrain, boolean doDrain) {
		if (from.ordinal() < Direction.values().length) {
			return sideTanks[from.ordinal()].drain(maxDrain, doDrain);
		} else {
			return null;
		}
	}

	public FluidStack drain(Direction from, FluidStack resource, boolean doDrain) {
		if (sideTanks[from.ordinal()].getFluid() == null || !(sideTanks[from.ordinal()].getFluid().isFluidEqual(resource))) {
			return new FluidStack(resource.getFluid(), 0);
		}
		return drain(from, resource.amount, doDrain);
	}

	public class FluidHandler implements IFluidHandler {

		private Direction from;

		FluidHandler(Direction from) {
			this.from = from;
		}

		@Override
		public int fill(FluidStack resource, boolean doFill) {
			if (from.ordinal() < Direction.values().length && getFluidPipe().canReceiveFluid()) {
				return sideTanks[from.ordinal()].fill(resource, doFill);
			} else {
				return 0;
			}
		}

		@Override
		public FluidStack drain(int maxDrain, boolean doDrain) {
			if (from.ordinal() < Direction.values().length) {
				return sideTanks[from.ordinal()].drain(maxDrain, doDrain);
			} else {
				return null;
			}
		}

		@Override
		public FluidStack drain(FluidStack resource, boolean doDrain) {
			if (sideTanks[from.ordinal()].getFluid() == null || !(sideTanks[from.ordinal()].getFluid().isFluidEqual(resource))) {
				return new FluidStack(resource.getFluid(), 0);
			}
			return drain(resource.amount, doDrain);
		}

		@Override
		public IFluidTankProperties[] getTankProperties() {
			if (from.ordinal() < Direction.values().length) {
				return sideTanks[from.ordinal()].getTankProperties();
			} else {
				return null;
			}
		}
	}

	@Override
	public void readFromNBT(CompoundTag nbttagcompound) {
		super.readFromNBT(nbttagcompound);

		for (Direction direction : Direction.values()) {
			if (nbttagcompound.hasKey("tank[" + direction.ordinal() + "]")) {
				sideTanks[direction.ordinal()].readFromNBT(nbttagcompound.getCompoundTag("tank[" + direction.ordinal() + "]"));
			}
		}
		if (nbttagcompound.hasKey("tank[middle]")) {
			internalTank.readFromNBT(nbttagcompound.getCompoundTag("tank[middle]"));
		}
	}

	@Override
	public void writeToNBT(CompoundTag nbttagcompound) {
		super.writeToNBT(nbttagcompound);

		for (Direction direction : Direction.values()) {
			CompoundTag subTag = new CompoundTag();
			sideTanks[direction.ordinal()].writeToNBT(subTag);
			nbttagcompound.setTag("tank[" + direction.ordinal() + "]", subTag);
		}
		CompoundTag subTag = new CompoundTag();
		internalTank.writeToNBT(subTag);
		nbttagcompound.setTag("tank[middle]", subTag);
	}

	public int getInnerCapacity() {
		return 10000;
	}

	public int getSideCapacity() {
		return 5000;
	}

	@Override
	public void onNeighborBlockChange() {
		super.onNeighborBlockChange();

		for (Direction direction : Direction.values()) {
			if (!MainProxy.checkPipesConnections(container, container.getTile(PipeFluidTransportLogistics.orientations[direction.ordinal()]), PipeFluidTransportLogistics.orientations[direction.ordinal()])) {
				if (!getWorld().isClient()) {
					FluidStack stack = sideTanks[direction.ordinal()].getFluid();
					if (stack != null) {
						sideTanks[direction.ordinal()].setFluid(null);
						internalTank.fill(stack, true);
					}
				}
				if (renderCache[direction.ordinal()] != null) {
					renderCache[direction.ordinal()].amount = 1;
				}
			}
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();
		updateFluid();
	}

	/*
	 * BuildCraft Fluid Sync Code
	 */
	private final SafeTimeTracker tracker = new SafeTimeTracker(10);
	private long clientSyncCounter = 30;
	public byte initClient = 0;

	private static final Direction[] orientations = Direction.values();

	private void updateFluid() {
		if (MainProxy.isClient(getWorld())) {
			return;
		}
		if (tracker.markTimeIfDelay(getWorld())) {

			boolean init = false;
			if (++clientSyncCounter > 40) {
				clientSyncCounter = 0;
				init = true;
			}
			if (clientSyncCounter < 0) {
				clientSyncCounter = 0;
			}
			ModernPacket packet = computeFluidUpdate(init, true);
			if (packet != null) {
				MainProxy.sendPacketToAllWatchingChunk(container.getPos().getX(), container.getPos().getZ(), getWorld().provider.getDimension(), packet);
			}
		}
	}

	/**
	 * Computes the PacketFluidUpdate packet for transmission to a client
	 *
	 * @param initPacket    everything is sent, no delta stuff ( first packet )
	 * @param persistChange The render cache change is persisted
	 * @return PacketFluidUpdate liquid update packet
	 */
	private ModernPacket computeFluidUpdate(boolean initPacket, boolean persistChange) {

		boolean changed = false;

		if (initClient > 0) {
			initClient--;
			if (initClient == 1) {
				changed = true;
			}
		}

		FluidStack[] renderCache = this.renderCache.clone();

		for (Direction dir : PipeFluidTransportLogistics.orientations) {
			FluidStack current;
			if (dir != null) {
				current = sideTanks[dir.ordinal()].getFluid();
			} else {
				current = internalTank.getFluid();
			}
			FluidStack prev = renderCache[dir.ordinal()];

			if (prev == null && current == null) {
				continue;
			}

			if (prev == null && current != null) {
				changed = true;
				renderCache[dir.ordinal()] = current.copy();
				continue;
			}

			if (prev != null && current == null) {
				changed = true;
				renderCache[dir.ordinal()] = null;
				continue;
			}

			if (prev.getFluid() != current.getFluid() || initPacket) {
				changed = true;
				renderCache[dir.ordinal()] = new FluidStack(current.getFluid(), renderCache[dir.ordinal()].amount);
			}

			if (prev.amount != current.amount || initPacket) {
				changed = true;
				renderCache[dir.ordinal()].amount = current.amount;
			}
		}

		if (persistChange) {
			this.renderCache = renderCache;
		}

		if (changed || initPacket) {
			return PacketHandler.getPacket(PipeFluidUpdate.class).setRenderCache(renderCache).setTilePos(container).setChunkDataPacket(initPacket);
		}

		return null;
	}

	@Override
	protected boolean isItemExitable(ItemStack stack) {
		return true;
	}

	@Override
	protected boolean isPipeCheck(BlockEntity tile) {
		return PipeInformationManager.INSTANCE.isPipe(tile);
	}
}

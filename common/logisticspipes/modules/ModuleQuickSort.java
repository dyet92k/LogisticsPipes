package logisticspipes.modules;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import logisticspipes.interfaces.WrappedInventory;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.packets.modules.QuickSortState;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.specialinventoryhandler.SpecialInventoryHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.Tuple2;

public class ModuleQuickSort extends LogisticsGuiModule {

	protected final int stalledDelay = 24;
	protected final int normalDelay = 6;
	protected int currentTick = 0;
	protected boolean stalled;
	protected int lastStackLookedAt = 0;
	protected int lastSuceededStack = 0;

	private PlayerCollectionList _watchingPlayer = new PlayerCollectionList();
	private int lastPosSend = 0;

	public ModuleQuickSort() {}

	@Override
	public void readFromNBT(CompoundTag nbttagcompound) {}

	@Override
	public void writeToNBT(CompoundTag nbttagcompound) {}

	@Override
	public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit,
			boolean forcePassive) {
		return null;
	}

	@Override
	public void tick() {
		if (--currentTick > 0) {
			return;
		}
		if (stalled) {
			currentTick = stalledDelay;
		} else {
			currentTick = normalDelay;
		}

		// Extract Item
		WrappedInventory invUtil = service.getPointedInventory();
		if (invUtil == null) {
			return;
		}

		if (!service.canUseEnergy(500)) {
			stalled = true;
			return;
		}

		if (invUtil instanceof SpecialInventoryHandler) {
			Map<ItemIdentifier, Integer> items = invUtil.getItemsAndCount();
			if (lastSuceededStack >= items.size()) {
				lastSuceededStack = 0;
			}
			if (lastStackLookedAt >= items.size()) {
				lastStackLookedAt = 0;
			}
			int lookedAt = 0;
			for (Entry<ItemIdentifier, Integer> item : items.entrySet()) {
				// spool to current place
				lookedAt++;
				if (lookedAt <= lastStackLookedAt) {
					continue;
				}

				LinkedList<Integer> jamList = new LinkedList<>();
				Tuple2<Integer, SinkReply> reply = service.hasDestination(item.getKey(), false, jamList);
				if (reply == null) {
					if (lastStackLookedAt == lastSuceededStack) {
						stalled = true;
					}
					lastStackLookedAt++;
					return;
				}
				if (!service.useEnergy(500)) {
					stalled = true;
					lastStackLookedAt++;
					return;
				}
				stalled = false;

				// send up to one stack
				int maxItemsToSend = item.getKey().getMaxStackSize();
				int availableItems = Math.min(maxItemsToSend, item.getValue());
				while (reply != null) {
					int count = availableItems;
					if (reply.getValue2().maxNumberOfItems != 0) {
						count = Math.min(count, reply.getValue2().maxNumberOfItems);
					}
					ItemStack stackToSend = invUtil.getMultipleItems(item.getKey(), count);
					if (stackToSend.isEmpty()) {
						break;
					}

					availableItems -= stackToSend.getCount();
					service.sendStack(stackToSend, reply, ItemSendMode.Fast);

					service.spawnParticle(Particles.OrangeParticle, 8);

					if (availableItems <= 0) {
						break;
					}

					jamList.add(reply.getValue1());
					reply = service.hasDestination(item.getKey(), false, jamList);
				}
				if (availableItems > 0) { // if we didn't send maxItemsToSend, try next item next time
					lastSuceededStack = lastStackLookedAt;
					lastStackLookedAt++;
				} else {
					lastSuceededStack = lastStackLookedAt - 1;
					if (lastSuceededStack < 0) {
						lastSuceededStack = items.size() - 1;
					}
				}
				return;
			}
		} else {

			if ((!(invUtil instanceof SpecialInventoryHandler) && invUtil.getSizeInventory() == 0) || !service.canUseEnergy(500)) {
				stalled = true;
				return;
			}

			if (lastSuceededStack >= invUtil.getSizeInventory()) {
				lastSuceededStack = 0;
			}

			// incremented at the end of the previous loop.
			if (lastStackLookedAt >= invUtil.getSizeInventory()) {
				lastStackLookedAt = 0;
			}

			ItemStack slot = invUtil.getStackInSlot(lastStackLookedAt);

			while (slot.isEmpty()) {
				lastStackLookedAt++;
				if (lastStackLookedAt >= invUtil.getSizeInventory()) {
					lastStackLookedAt = 0;
				}
				slot = invUtil.getStackInSlot(lastStackLookedAt);
				if (lastStackLookedAt == lastSuceededStack) {
					stalled = true;
					send();
					return; // then we have been around the list without sending, halt for now
				}
			}
			send();

			// begin duplicate code
			List<Integer> jamList = new LinkedList<>();
			Tuple2<Integer, SinkReply> reply = service.hasDestination(ItemIdentifier.get(slot), false, jamList);
			if (reply == null) {
				if (lastStackLookedAt == lastSuceededStack) {
					stalled = true;
				}
				lastStackLookedAt++;
				return;
			}
			if (!service.useEnergy(500)) {
				stalled = true;
				lastStackLookedAt++;
				return;
			}

			stalled = false;

			// don't directly modify the stack in the inv
			int sizePrev;
			slot = slot.copy();
			sizePrev = slot.getCount();
			boolean partialSend = false;
			while (reply != null) {
				int count = slot.getCount();
				if (reply.getValue2().maxNumberOfItems > 0) {
					count = Math.min(count, reply.getValue2().maxNumberOfItems);
				}
				ItemStack stackToSend = slot.splitStack(count);

				service.sendStack(stackToSend, reply, ItemSendMode.Fast);
				service.spawnParticle(Particles.OrangeParticle, 8);

				if (slot.isEmpty()) {
					break;
				}

				jamList.add(reply.getValue1());
				reply = service.hasDestination(ItemIdentifier.get(slot), false, jamList);
			}

			int amountToExtract = sizePrev - slot.getCount();
			if (!slot.isEmpty()) {
				partialSend = true;
			}

			ItemStack returned = invUtil.decrStackSize(lastStackLookedAt, amountToExtract);
			if (returned.getCount() != amountToExtract) {
				// item duplication prevention
				throw new UnsupportedOperationException("Couldn't extract the items already sent from the inventory");
			}

			lastSuceededStack = lastStackLookedAt;
			// end duplicate code
			lastStackLookedAt++;
			if (partialSend) {
				if (lastStackLookedAt >= invUtil.getSizeInventory()) {
					lastStackLookedAt = 0;
				}
				while (lastStackLookedAt != lastSuceededStack) {
					ItemStack tstack = invUtil.getStackInSlot(lastStackLookedAt);
					if (!tstack.isEmpty() && !slot.isItemEqual(tstack)) {
						break;
					}
					lastStackLookedAt++;
					if (lastStackLookedAt >= invUtil.getSizeInventory()) {
						lastStackLookedAt = 0;
					}

				}
			}
		}
	}

	protected void send() {
		if (lastPosSend != lastStackLookedAt) {
			lastPosSend = lastStackLookedAt;
			for (EntityPlayer player : _watchingPlayer.players()) {
				sendPacketTo(player);
			}
		}
	}

	private void sendPacketTo(EntityPlayer player) {
		MainProxy.sendPacketToPlayer(PacketHandler.getPacket(QuickSortState.class).setInteger2(lastPosSend).setInteger(getPositionInt()).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), player);
	}

	@Override
	public boolean hasGenericInterests() {
		return false;
	}

	@Override
	public List<ItemIdentifier> getSpecificInterests() {
		return null;
	}

	@Override
	public boolean interestedInAttachedInventory() {
		return false;
	}

	@Override
	public boolean interestedInUndamagedID() {
		return false;
	}

	@Override
	public boolean receivePassive() {
		return true;
	}

	public void addWatchingPlayer(EntityPlayer player) {
		_watchingPlayer.add(player);
		sendPacketTo(player);
	}

	public void removeWatchingPlayer(EntityPlayer player) {
		_watchingPlayer.remove(player);
	}

	@Override
	public boolean hasGui() {
		return false;
	}

	@Override
	protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
		return null;
	}

	@Override
	protected ModuleInHandGuiProvider getInHandGuiProvider() {
		return null;
	}
}

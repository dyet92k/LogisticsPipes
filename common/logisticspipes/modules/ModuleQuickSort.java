package logisticspipes.modules;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import kotlin.Pair;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.modules.QuickSortState;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.specialinventoryhandler.SpecialInventoryHandler;
import logisticspipes.routing.ServerRouter;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import network.rs485.logisticspipes.logistics.LogisticsManager;

public class ModuleQuickSort extends LogisticsModule {

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
	public void readFromNBT(@Nonnull NBTTagCompound nbttagcompound) {}

	@Override
	public void writeToNBT(@Nonnull NBTTagCompound nbttagcompound) {}

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

		//Extract Item
		IInventoryUtil invUtil = _service.getPointedInventory();
		if (invUtil == null) {
			return;
		}

		if (!_service.canUseEnergy(500)) {
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
				final ItemStack stack = item.getKey().makeNormalStack(item.getValue());
				Pair<Integer, SinkReply> reply = LogisticsManager.INSTANCE.getDestination(stack, item.getKey(), false, (ServerRouter) _service.getRouter(), jamList);
				if (reply == null) {
					if (lastStackLookedAt == lastSuceededStack) {
						stalled = true;
					}
					lastStackLookedAt++;
					return;
				}
				if (!_service.useEnergy(500)) {
					stalled = true;
					lastStackLookedAt++;
					return;
				}
				stalled = false;

				//send up to one stack
				int maxItemsToSend = item.getKey().getMaxStackSize();
				int availableItems = Math.min(maxItemsToSend, item.getValue());
				while (reply != null) {
					int count = availableItems;
					if (reply.getSecond().maxNumberOfItems != 0) {
						count = Math.min(count, reply.getSecond().maxNumberOfItems);
					}
					ItemStack stackToSend = invUtil.getMultipleItems(item.getKey(), count);
					if (stackToSend.isEmpty()) {
						break;
					}

					availableItems -= stackToSend.getCount();
					_service.sendStack(stackToSend, reply.getFirst(), reply.getSecond(), ItemSendMode.Fast);

					_service.spawnParticle(Particles.OrangeParticle, 8);

					if (availableItems <= 0) {
						break;
					}

					jamList.add(reply.getFirst());
					reply = LogisticsManager.INSTANCE.getDestination(stackToSend, item.getKey(), false, (ServerRouter) _service.getRouter(), jamList);
				}
				if (availableItems > 0) { //if we didn't send maxItemsToSend, try next item next time
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

			if ((!(invUtil instanceof SpecialInventoryHandler) && invUtil.getSizeInventory() == 0) || !_service.canUseEnergy(500)) {
				stalled = true;
				return;
			}

			if (lastSuceededStack >= invUtil.getSizeInventory()) {
				lastSuceededStack = 0;
			}

			//incremented at the end of the previous loop.
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
			Pair<Integer, SinkReply> reply = LogisticsManager.INSTANCE.getDestination(slot, ItemIdentifier.get(slot), false, (ServerRouter) _service.getRouter(), jamList);
			if (reply == null) {
				if (lastStackLookedAt == lastSuceededStack) {
					stalled = true;
				}
				lastStackLookedAt++;
				return;
			}
			if (!_service.useEnergy(500)) {
				stalled = true;
				lastStackLookedAt++;
				return;
			}

			stalled = false;

			//don't directly modify the stack in the inv
			int sizePrev;
			slot = slot.copy();
			sizePrev = slot.getCount();
			boolean partialSend = false;
			while (reply != null) {
				int count = slot.getCount();
				if (reply.getSecond().maxNumberOfItems > 0) {
					count = Math.min(count, reply.getSecond().maxNumberOfItems);
				}
				ItemStack stackToSend = slot.splitStack(count);

				_service.sendStack(stackToSend, reply.getFirst(), reply.getSecond(), ItemSendMode.Fast);
				_service.spawnParticle(Particles.OrangeParticle, 8);

				if (slot.isEmpty()) {
					break;
				}

				jamList.add(reply.getFirst());
				reply = LogisticsManager.INSTANCE.getDestination(slot, ItemIdentifier.get(slot), false, (ServerRouter) _service.getRouter(), jamList);
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
		MainProxy.sendPacketToPlayer(PacketHandler.getPacket(QuickSortState.class).setInteger(lastPosSend).setModulePos(this), player);
	}

	@Override
	public boolean hasGenericInterests() {
		return false;
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
	public boolean recievePassive() {
		return true;
	}

	public void addWatchingPlayer(EntityPlayer player) {
		_watchingPlayer.add(player);
		sendPacketTo(player);
	}

	public void removeWatchingPlayer(EntityPlayer player) {
		_watchingPlayer.remove(player);
	}

}

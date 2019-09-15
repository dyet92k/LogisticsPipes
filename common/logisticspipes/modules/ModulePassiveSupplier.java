package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompoundTag;

import logisticspipes.gui.hud.modules.HUDSimpleFilterModule;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IModuleInventoryReceive;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.interfaces.WrappedInventory;
import logisticspipes.modules.abstractmodules.LogisticsSimpleFilterModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ModuleInventory;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemStack;

public class ModulePassiveSupplier extends LogisticsSimpleFilterModule implements IClientInformationProvider, IHUDModuleHandler, IModuleWatchReciver, IModuleInventoryReceive, ISimpleInventoryEventHandler {

	private final ItemIdentifierInventory _filterInventory = new ItemIdentifierInventory(9, "Requested items", 64);

	private IHUDModuleRenderer HUD = new HUDSimpleFilterModule(this);

	private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

	public ModulePassiveSupplier() {
		_filterInventory.addListener(this);
	}

	@Override
	public IInventory getFilterInventory() {
		return _filterInventory;
	}

	private SinkReply _sinkReply;

	@Override
	public void registerPosition(ModulePositionType slot, int positionInt) {
		super.registerPosition(slot, positionInt);
		_sinkReply = new SinkReply(FixedPriority.PassiveSupplier, 0, true, false, 2, 0, new ChassiTargetInformation(getPositionInt()));
	}

	@Override
	public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit,
			boolean forcePassive) {
		if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal() && bestCustomPriority >= _sinkReply.customPriority)) {
			return null;
		}

		WrappedInventory targetUtil = service.getSneakyInventory(slot, positionInt);
		if (targetUtil == null) {
			return null;
		}

		if (!_filterInventory.containsItem(item)) {
			return null;
		}

		int targetCount = _filterInventory.itemCount(item);
		int haveCount = targetUtil.itemCount(item);
		if (targetCount <= haveCount) {
			return null;
		}

		if (service.canUseEnergy(2)) {
			return new SinkReply(_sinkReply, targetCount - haveCount);
		}
		return null;
	}

	@Override
	public void readFromNBT(CompoundTag nbttagcompound) {
		_filterInventory.readFromNBT(nbttagcompound, "");
	}

	@Override
	public void writeToNBT(CompoundTag nbttagcompound) {
		_filterInventory.writeToNBT(nbttagcompound, "");
	}

	@Override
	public void tick() {}

	@Override
	public List<String> getClientInformation() {
		List<String> list = new ArrayList<>();
		list.add("Supplied: ");
		list.add("<inventory>");
		list.add("<that>");
		return list;
	}

	@Override
	public void startHUDWatching() {
		MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStartModuleWatchingPacket.class).setModulePos(this));
	}

	@Override
	public void stopHUDWatching() {
		MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStopModuleWatchingPacket.class).setModulePos(this));
	}

	@Override
	public void startWatching(EntityPlayer player) {
		localModeWatchers.add(player);
		MainProxy.sendPacketToPlayer(PacketHandler.getPacket(ModuleInventory.class).setIdentList(ItemStack.getListFromInventory(_filterInventory)).setModulePos(this), player);
	}

	@Override
	public void stopWatching(EntityPlayer player) {
		localModeWatchers.remove(player);
	}

	@Override
	public IHUDModuleRenderer getHUDRenderer() {
		return HUD;
	}

	@Override
	public void handleInvContent(Collection<ItemStack> list) {
		_filterInventory.handleItemIdentifierList(list);
	}

	@Override
	public void InventoryChanged(IInventory inventory) {
		if (MainProxy.isServer(world.getWorld())) {
			MainProxy.sendToPlayerList(PacketHandler.getPacket(ModuleInventory.class).setIdentList(ItemStack.getListFromInventory(_filterInventory)).setModulePos(this), localModeWatchers);
		}
	}

	@Override
	public boolean hasGenericInterests() {
		return false;
	}

	@Override
	public List<ItemIdentifier> getSpecificInterests() {
		Map<ItemIdentifier, Integer> mapIC = _filterInventory.getItemsAndCount();
		List<ItemIdentifier> li = new ArrayList<>(mapIC.size());
		li.addAll(mapIC.keySet());
		return li;
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

}

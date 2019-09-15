package logisticspipes.network.packets.gui;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.blocks.LogisticsSecurityTileEntity;
import logisticspipes.interfaces.routing.ChannelManagerProvider;
import logisticspipes.interfaces.routing.IChannelManager;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.block.SecurityChannelManagerGui;
import logisticspipes.utils.StaticResolve;

@StaticResolve
public class OpenSecurityChannelManagerPacket extends CoordinatesPacket {

	public OpenSecurityChannelManagerPacket(int id) {
		super(id);
	}

	@Override
	public void processPacket(EntityPlayer player) {
		LogisticsSecurityTileEntity securityTile = this.getTile(player.getEntityWorld(), LogisticsSecurityTileEntity.class);
		IChannelManager manager = ChannelManagerProvider.getInstance().getChannelManager(player.getEntityWorld());
		NewGuiHandler.getGui(SecurityChannelManagerGui.class).setChannelInformations(manager.getAllowedChannels(player)).setTilePos(securityTile).open(player);
	}

	@Override
	public ModernPacket template() {
		return new OpenSecurityChannelManagerPacket(getId());
	}
}

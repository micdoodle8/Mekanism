package mekanism.generators.common;

import ic2.api.Direction;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.IEnergyStorage;
import ic2.api.energy.event.EnergyTileSourceEvent;
import ic2.api.energy.tile.IEnergySource;

import java.util.ArrayList;
import java.util.EnumSet;

import com.google.common.io.ByteArrayDataInput;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import mekanism.api.ICableOutputter;
import mekanism.api.IUniversalCable;
import mekanism.client.IHasSound;
import mekanism.client.Sound;
import mekanism.common.CableUtils;
import mekanism.common.IActiveState;
import mekanism.common.Mekanism;
import mekanism.common.MekanismUtils;
import mekanism.common.PacketHandler;
import mekanism.common.TileEntityElectricBlock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import universalelectricity.core.electricity.ElectricityNetworkHelper;
import universalelectricity.core.electricity.IElectricityNetwork;
import universalelectricity.core.vector.Vector3;
import universalelectricity.core.vector.VectorHelper;
import universalelectricity.core.block.IConductor;
import buildcraft.api.power.IPowerProvider;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerProvider;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.IPeripheral;

public abstract class TileEntityGenerator extends TileEntityElectricBlock implements IEnergySource, IEnergyStorage, IPowerReceptor, IPeripheral, IActiveState, IHasSound, ICableOutputter
{
	/** The Sound instance for this generator. */
	@SideOnly(Side.CLIENT)
	public Sound audio;
	
	/** Output per tick this generator can transfer. */
	public int output;
	
	/** Whether or not this block is in it's active state. */
	public boolean isActive;
	
	/** The previous active state for this block. */
	public boolean prevActive;
	
	/**
	 * Generator -- a block that produces energy. It has a certain amount of fuel it can store as well as an output rate.
	 * @param name - full name of this generator
	 * @param maxEnergy - how much energy this generator can store
	 * @param maxFuel - how much fuel this generator can store
	 */
	public TileEntityGenerator(String name, int maxEnergy, int out)
	{
		super(name, maxEnergy);
		
		if(powerProvider != null)
		{
			powerProvider.configure(0, 0, 0, 0, (int)(maxEnergy*Mekanism.TO_BC));
		}
		
		output = out;
		isActive = false;
	}
	
	@Override
	public void onUpdate()
	{	
		super.onUpdate();
		
		if(worldObj.isRemote)
		{
			try {
				if(Mekanism.audioHandler != null)
				{
					synchronized(Mekanism.audioHandler.sounds)
					{
						updateSound();
					}
				}
			} catch(NoSuchMethodError e) {}
		}
		
		if(electricityStored > 0 && !worldObj.isRemote)
		{
			TileEntity tileEntity = VectorHelper.getTileEntityFromSide(worldObj, new Vector3(this), ForgeDirection.getOrientation(facing));
			
			if(tileEntity instanceof IUniversalCable)
			{
				setJoules(electricityStored - (Math.min(electricityStored, output) - CableUtils.emitEnergyToNetwork(Math.min(electricityStored, output), this, ForgeDirection.getOrientation(facing))));
			}
			else if((tileEntity instanceof IEnergyConductor || tileEntity instanceof IEnergyAcceptor) && Mekanism.hooks.IC2Loaded)
			{
				if(electricityStored >= output)
				{
					EnergyTileSourceEvent event = new EnergyTileSourceEvent(this, output);
					MinecraftForge.EVENT_BUS.post(event);
					setJoules(electricityStored - (output - event.amount));
				}
			}
			else if(isPowerReceptor(tileEntity) && Mekanism.hooks.BuildCraftLoaded)
			{
				IPowerReceptor receptor = (IPowerReceptor)tileEntity;
            	double electricityNeeded = Math.min(receptor.powerRequest(ForgeDirection.getOrientation(facing).getOpposite()), receptor.getPowerProvider().getMaxEnergyStored() - receptor.getPowerProvider().getEnergyStored())*Mekanism.FROM_BC;
            	float transferEnergy = (float)Math.min(electricityStored, Math.min(electricityNeeded, output));
            	receptor.getPowerProvider().receiveEnergy((float)(transferEnergy*Mekanism.TO_BC), ForgeDirection.getOrientation(facing).getOpposite());
            	setJoules(electricityStored - transferEnergy);
			}
			else if(tileEntity instanceof IConductor)
			{
				ForgeDirection outputDirection = ForgeDirection.getOrientation(facing);
				TileEntity outputTile = VectorHelper.getTileEntityFromSide(worldObj, new Vector3(this), outputDirection);

				IElectricityNetwork outputNetwork = ElectricityNetworkHelper.getNetworkFromTileEntity(outputTile, outputDirection);

				if(outputNetwork != null)
				{
					double outputWatts = Math.min(outputNetwork.getRequest().getWatts(), Math.min(getJoules(), 10000));

					if(getJoules() > 0 && outputWatts > 0 && getJoules()-outputWatts >= 0)
					{
						outputNetwork.startProducing(this, outputWatts / getVoltage(), getVoltage());
						setJoules(electricityStored - outputWatts);
					}
					else {
						outputNetwork.stopProducing(this);
					}
				}
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void updateSound()
	{
		if(Mekanism.audioHandler != null)
		{
			synchronized(Mekanism.audioHandler.sounds)
			{
				if(audio == null && worldObj != null && worldObj.isRemote)
				{
					if(FMLClientHandler.instance().getClient().sndManager.sndSystem != null)
					{
						audio = Mekanism.audioHandler.getSound(fullName.replace(" ", "").replace("-","") + ".ogg", worldObj, xCoord, yCoord, zCoord);
					}
				}
				
				if(worldObj != null && worldObj.isRemote && audio != null)
				{
					if(!audio.isPlaying && isActive == true)
					{
						audio.play();
					}
					else if(audio.isPlaying && isActive == false)
					{
						audio.stopLoop();
					}
				}
			}
		}
	}
	
	@Override
	protected EnumSet<ForgeDirection> getConsumingSides()
	{
		return EnumSet.noneOf(ForgeDirection.class);
	}
	
	@Override
	public boolean canConnect(ForgeDirection direction)
	{
		return direction == ForgeDirection.getOrientation(facing);
	}
	
	@Override
	public void invalidate()
	{
		super.invalidate();
		
		if(worldObj.isRemote && audio != null)
		{
			audio.remove();
		}
	}
	
	/**
	 * Gets the boost this generator can receive in it's current location.
	 * @return environmental boost
	 */
	public abstract int getEnvironmentBoost();
	
	/**
	 * Whether or not this generator can operate.
	 * @return if the generator can operate
	 */
	public abstract boolean canOperate();
	
	/**
	 * Whether or not the declared Tile Entity is an instance of a BuildCraft power receptor.
	 * @param tileEntity - tile entity to check
	 * @return if the tile entity is a power receptor
	 */
	public boolean isPowerReceptor(TileEntity tileEntity)
	{
		if(tileEntity instanceof IPowerReceptor) 
		{
			IPowerReceptor receptor = (IPowerReceptor)tileEntity;
			IPowerProvider provider = receptor.getPowerProvider();
			return provider != null && provider.getClass().getSuperclass().equals(PowerProvider.class);
		}
		return false;
	}
	
	/**
	 * Gets the scaled energy level for the GUI.
	 * @param i - multiplier
	 * @return
	 */
	public int getScaledEnergyLevel(int i)
	{
		return (int)(electricityStored*i / MAX_ELECTRICITY);
	}
	
	@Override
	public boolean getActive()
	{
		return isActive;
	}
	
	@Override
    public void setActive(boolean active)
    {
    	isActive = active;
    	
    	if(prevActive != active)
    	{
    		PacketHandler.sendTileEntityPacketToClients(this, 0, getNetworkedData(new ArrayList()));
    	}
    	
    	prevActive = active;
    }
	
	@Override
	public String getType() 
	{
		return getInvName();
	}

	@Override
	public boolean canAttachToSide(int side) 
	{
		return true;
	}

	@Override
	public void attach(IComputerAccess computer) {}

	@Override
	public void detach(IComputerAccess computer) {}
	
	@Override
	public int getMaxEnergyOutput()
	{
		return output;
	}
	
	@Override
	public void setFacing(short orientation)
	{
		super.setFacing(orientation);
		
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, MekanismGenerators.generatorID);
	}
	
	@Override
	public boolean emitsEnergyTo(TileEntity receiver, Direction direction)
	{
		return direction.toForgeDirection() == ForgeDirection.getOrientation(facing);
	}
	
	@Override
	public int getStored() 
	{
		return (int)(electricityStored*Mekanism.TO_IC2);
	}

	@Override
	public int getCapacity() 
	{
		return (int)(MAX_ELECTRICITY*Mekanism.TO_IC2);
	}

	@Override
	public int getOutput() 
	{
		return output;
	}
	
	@Override
	public boolean isTeleporterCompatible(Direction side) 
	{
		return side.toForgeDirection() == ForgeDirection.getOrientation(facing);
	}
	
	@Override
	public int addEnergy(int amount)
	{
		setJoules(electricityStored + amount*Mekanism.FROM_IC2);
		return (int)electricityStored;
	}
	
	@Override
	public void setStored(int energy)
	{
		setJoules(energy*Mekanism.FROM_IC2);
	}
	
	@Override
	public void handlePacketData(ByteArrayDataInput dataStream)
	{
		super.handlePacketData(dataStream);
		isActive = dataStream.readBoolean();
		MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
	}
	
	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		data.add(isActive);
		return data;
	}
	
	@Override
    public void readFromNBT(NBTTagCompound nbtTags)
    {
        super.readFromNBT(nbtTags);

        isActive = nbtTags.getBoolean("isActive");
    }

	@Override
    public void writeToNBT(NBTTagCompound nbtTags)
    {
        super.writeToNBT(nbtTags);
        
        nbtTags.setBoolean("isActive", isActive);
    }
	
	@Override
	public int powerRequest(ForgeDirection side) 
	{
		return 0;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox()
	{
		return INFINITE_EXTENT_AABB;
	}
	
	@Override
	public Sound getSound()
	{
		return audio;
	}
	
	@Override
	public void removeSound()
	{
		audio = null;
	}
	
	@Override
	public boolean canOutputTo(ForgeDirection side)
	{
		return side == ForgeDirection.getOrientation(facing);
	}
}

package mekanism.common;

import java.util.List;

import universalelectricity.core.electricity.ElectricityPack;

import mekanism.api.EnumColor;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class ItemAtomicDisassembler extends ItemEnergized
{
	public ItemAtomicDisassembler(int id)
	{
		super(id, 1000000, 120);
	}
	
    @Override
    public boolean canHarvestBlock(Block block)
    {
    	return block != Block.bedrock;
    }
    
    @Override
	public void addInformation(ItemStack itemstack, EntityPlayer entityplayer, List list, boolean flag)
	{
    	super.addInformation(itemstack, entityplayer, list, flag);
    	
    	list.add("Block efficiency: " + getEfficiency(itemstack));
	}
    
    @Override
    public boolean hitEntity(ItemStack itemstack, EntityLiving hitEntity, EntityLiving player)
    {
    	if(getJoules(itemstack) > 0)
    	{
			hitEntity.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer)player), 20);
			onProvide(new ElectricityPack(2000/120, 120), itemstack);
    	}
    	else {
    		hitEntity.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer)player), 4);
    	}
    	
        return false;
    }
    
    public float getStrVsBlock(ItemStack itemstack, Block block)
    {
    	return getJoules(itemstack) != 0 ? getEfficiency(itemstack) : 1F;
    }
    
    @Override
    public boolean onBlockDestroyed(ItemStack itemstack, World world, int id, int x, int y, int z, EntityLiving entityliving)
    {
        if(Block.blocksList[id].getBlockHardness(world, x, y, z) != 0.0D)
        {
            onProvide(new ElectricityPack(getEfficiency(itemstack), 120), itemstack);
        }
        else {
        	onProvide(new ElectricityPack((getEfficiency(itemstack)/2)/120, 120), itemstack);
        }

        return true;
    }
    
    @Override
    public boolean isFull3D()
    {
        return true;
    }
    
	@Override
	public ElectricityPack getProvideRequest(ItemStack itemStack)
	{
		return new ElectricityPack();
	}
    
    @Override
    public ItemStack onItemRightClick(ItemStack itemstack, World world, EntityPlayer entityplayer)
    {
		if(!world.isRemote && entityplayer.isSneaking())
		{
			incrementEfficiency(itemstack);
    		entityplayer.addChatMessage(EnumColor.DARK_BLUE + "[Mekanism] " + EnumColor.GREY + "Efficiency bumped to " + getEfficiency(itemstack));
		}
		
        return itemstack;
    }
    
    public int getEfficiency(ItemStack itemStack)
    {
		if(itemStack.stackTagCompound == null)
		{
			return 2;
		}
		
		int efficiency = 2;
		
		if(itemStack.stackTagCompound.getTag("efficiency") != null)
		{
			efficiency = itemStack.stackTagCompound.getInteger("efficiency");
		}
		
		return efficiency;
    }
    
    public void incrementEfficiency(ItemStack itemStack)
    {
		if(itemStack.stackTagCompound == null)
		{
			itemStack.setTagCompound(new NBTTagCompound());
			itemStack.stackTagCompound.setInteger("efficiency", 20);
		}
		
		itemStack.stackTagCompound.setInteger("efficiency", getIncremented(getEfficiency(itemStack)));
    }
    
    public int getIncremented(int previous)
    {
    	if(previous == 0)
    	{
    		return 2;
    	}
    	else if(previous == 2)
    	{
    		return 8;
    	}
    	else if(previous == 8)
    	{
    		return 24;
    	}
    	else if(previous == 24)
    	{
    		return 64;
    	}
    	else if(previous == 64)
    	{
    		return 100;
    	}
    	else if(previous == 100)
    	{
    		return 0;
    	}
    	
    	return 0;
    }
}

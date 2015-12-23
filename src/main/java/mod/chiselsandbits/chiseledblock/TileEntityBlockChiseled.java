package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.ChiselsAndBits;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob.CommonBlock;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.data.VoxelNeighborRenderTracker;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.render.chiseledblock.ChisledBlockSmartModel;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntityBlockChiseled extends TileEntity
{

	public static final String block_prop = "b";
	public static final String side_prop = "s";
	public static final String voxel_prop = "v";
	public static final String light_opacity_prop = "l";
	public static final String light_prop = "lv";

	private TileRenderChunk renderChunk;
	private IExtendedBlockState state;

	public TileRenderChunk getRenderChunk()
	{
		return renderChunk;
	}

	public void copyFrom(
			final TileEntityBlockChiseled src )
	{
		renderChunk = src.renderChunk;
		state = src.state;
	}

	public TileEntityBlockChiseled()
	{
	}

	public IExtendedBlockState getBasicState()
	{
		return getState( false, 0 );
	}

	public IExtendedBlockState getRenderState()
	{
		return getState( true, 1 );
	}

	protected IExtendedBlockState getState(
			final boolean updateNeightbors,
			final int updateCost )
	{
		if ( state == null )
		{
			return (IExtendedBlockState) ChiselsAndBits.instance.blocks.getChiseledDefaultState();
		}

		if ( updateNeightbors )
		{
			if ( renderChunk == null )
			{
				renderChunk = findRenderChunk();
				renderChunk.register( this );
			}

			renderChunk.update( null, updateCost );

			if ( isInvalid() )
			{
				return state;
			}

			final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.n_prop );
			vns.update( renderChunk.isDyanmic, worldObj, pos );
		}

		return state;
	}

	@Override
	public void invalidate()
	{
		if ( renderChunk != null )
		{
			renderChunk.unregister( this );
		}
	}

	private TileRenderChunk findRenderChunk()
	{
		int cp_x = getPos().getX();
		int cp_y = getPos().getY();
		int cp_z = getPos().getZ();

		final int mask = ~0xf;
		cp_x = cp_x & mask;
		cp_y = cp_y & mask;
		cp_z = cp_z & mask;

		for ( int x = 0; x < 16; ++x )
		{
			for ( int y = 0; y < 16; ++y )
			{
				for ( int z = 0; z < 16; ++z )
				{
					final TileEntity te = worldObj.getTileEntity( new BlockPos( cp_x + x, cp_y + y, cp_z + z ) );
					if ( te instanceof TileEntityBlockChiseled )
					{
						final TileRenderChunk trc = ( (TileEntityBlockChiseled) te ).renderChunk;
						if ( trc != null )
						{
							return trc;
						}
					}
				}
			}
		}

		return new TileRenderChunk();
	}

	public IBlockState getBlockState(
			final Block alternative )
	{
		final Integer stateID = getBasicState().getValue( BlockChiseled.block_prop );

		if ( stateID != null )
		{
			final IBlockState state = Block.getStateById( stateID );
			if ( state != null )
			{
				return state;
			}
		}

		return alternative.getDefaultState();
	}

	public IBlockState getParticleBlockState(
			final Block alternative )
	{
		final IBlockState state = Block.getStateById( getBasicState().getValue( BlockChiseled.block_prop ) );
		if ( state != null )
		{
			return state;
		}

		return alternative.getDefaultState();
	}

	public void setState(
			final IExtendedBlockState state )
	{
		this.state = state;
	}

	@Override
	public boolean shouldRefresh(
			final World world,
			final BlockPos pos,
			final IBlockState oldState,
			final IBlockState newState )
	{
		return oldState.getBlock() != newState.getBlock();
	}

	@SuppressWarnings( "rawtypes" )
	@Override
	public Packet getDescriptionPacket()
	{
		final NBTTagCompound nbttagcompound = new NBTTagCompound();
		writeChisleData( nbttagcompound );

		if ( nbttagcompound.hasNoTags() )
		{
			return null;
		}

		return new S35PacketUpdateTileEntity( pos, 255, nbttagcompound );
	}

	@Override
	public void onDataPacket(
			final NetworkManager net,
			final S35PacketUpdateTileEntity pkt )
	{
		readChisleData( pkt.getNbtCompound() );
		if ( worldObj != null )
		{
			worldObj.markBlockForUpdate( pos );
		}
	}

	public final void writeChisleData(
			final NBTTagCompound compound )
	{
		final Integer b = getBasicState().getValue( BlockChiseled.block_prop );
		final Integer s = getBasicState().getValue( BlockChiseled.side_prop );
		final Float l = getBasicState().getValue( BlockChiseled.opacity_prop );
		final Integer lv = getBasicState().getValue( BlockChiseled.light_prop );
		final VoxelBlobStateReference vbs = getBasicState().getValue( BlockChiseled.v_prop );

		if ( b == null || vbs == null )
		{
			return;
		}

		if ( b != null && vbs != null )
		{
			compound.setFloat( light_opacity_prop, l == null ? 1.0f : l );
			compound.setInteger( light_prop, lv == null ? 0 : lv );
			compound.setInteger( block_prop, b );
			compound.setInteger( side_prop, s );
			compound.setByteArray( voxel_prop, vbs.getByteArray() );
		}
	}

	public final void readChisleData(
			final NBTTagCompound compound )
	{
		final Integer oldLV = getBasicState().getValue( BlockChiseled.light_prop );

		final int sideFlags = compound.getInteger( side_prop );
		int b = compound.getInteger( block_prop );
		final float l = compound.getFloat( light_opacity_prop );
		final int lv = compound.getInteger( light_prop );
		final byte[] v = compound.getByteArray( voxel_prop );

		if ( b == 0 )
		{
			// if load fails default to cobble stone...
			b = Block.getStateId( Blocks.cobblestone.getDefaultState() );
		}

		setState( getBasicState()
				.withProperty( BlockChiseled.side_prop, sideFlags )
				.withProperty( BlockChiseled.block_prop, b )
				.withProperty( BlockChiseled.light_prop, lv )
				.withProperty( BlockChiseled.opacity_prop, l )
				.withProperty( BlockChiseled.n_prop, new VoxelNeighborRenderTracker() )
				.withProperty( BlockChiseled.v_prop, new VoxelBlobStateReference( v, getPositionRandom( pos ) ) ) );

		if ( oldLV == null || oldLV != lv )
		{
			if ( worldObj != null )
			{
				worldObj.checkLight( pos );
			}
		}
	}

	@Override
	public void writeToNBT(
			final NBTTagCompound compound )
	{
		super.writeToNBT( compound );
		writeChisleData( compound );
	}

	@Override
	public void readFromNBT(
			final NBTTagCompound compound )
	{
		super.readFromNBT( compound );
		readChisleData( compound );
	}

	public void fillWith(
			final IBlockState blockType )
	{
		final VoxelBlob vb = new VoxelBlob();
		vb.fill( Block.getStateId( blockType ) );

		int ref = 0;
		final CommonBlock cb = vb.mostCommonBlock();
		if ( cb != null )
		{
			ref = cb.ref;
		}

		IExtendedBlockState state = getBasicState()
				.withProperty( BlockChiseled.side_prop, 0xFF )
				.withProperty( BlockChiseled.opacity_prop, vb.getOpacity() )
				.withProperty( BlockChiseled.light_prop, blockType.getBlock().getLightValue() )
				.withProperty( BlockChiseled.n_prop, new VoxelNeighborRenderTracker() )
				.withProperty( BlockChiseled.v_prop, new VoxelBlobStateReference( vb, getPositionRandom( pos ) ) );

		// required for placing bits
		if ( ref != 0 )
		{
			state = state.withProperty( BlockChiseled.block_prop, ref );
		}

		setState( state );
		markDirty();
	}

	private long getPositionRandom(
			final BlockPos pos )
	{
		if ( FMLCommonHandler.instance().getSide() == Side.CLIENT )
		{
			return MathHelper.getPositionRandom( pos );
		}

		return 0;
	}

	public VoxelBlob getBlob()
	{
		VoxelBlob vb = null;
		final VoxelBlobStateReference vbs = getBasicState().getValue( BlockChiseled.v_prop );

		if ( vbs != null )
		{
			vb = vbs.getVoxelBlob();

			if ( vb == null )
			{
				vb = new VoxelBlob();
				vb.fill( Block.getStateId( Blocks.cobblestone.getDefaultState() ) );
			}
		}
		else
		{
			vb = new VoxelBlob();
		}

		return vb;
	}

	public void setBlob(
			final VoxelBlob vb )
	{
		// blob = new WeakReference<VoxelBlob>( vb );

		final Integer olv = getBasicState().getValue( BlockChiseled.light_prop );

		final CommonBlock common = vb.mostCommonBlock();
		final float opacity = vb.getOpacity();
		final float light = vb.getLight();
		final int lv = Math.max( 0, Math.min( 15, (int) ( light * 15 ) ) );

		// are most of the bits in the center solid?
		final int sideFlags = vb.getSideFlags( 5, 11, 4 * 4 );

		final Integer oldSideFlags = getBasicState().getValue( BlockChiseled.side_prop );

		if ( worldObj == null )
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.side_prop, sideFlags )
					.withProperty( BlockChiseled.v_prop, new VoxelBlobStateReference( vb.toByteArray(), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.light_prop, lv )
					.withProperty( BlockChiseled.opacity_prop, opacity )
					.withProperty( BlockChiseled.n_prop, new VoxelNeighborRenderTracker() )
					.withProperty( BlockChiseled.block_prop, common.ref ) );

			return;
		}

		if ( common.isFull )
		{
			worldObj.setBlockState( pos, Block.getStateById( common.ref ) );
		}
		else if ( common.ref != 0 )
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.side_prop, sideFlags )
					.withProperty( BlockChiseled.v_prop, new VoxelBlobStateReference( vb.toByteArray(), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.light_prop, lv )
					.withProperty( BlockChiseled.opacity_prop, opacity )
					.withProperty( BlockChiseled.n_prop, new VoxelNeighborRenderTracker() )
					.withProperty( BlockChiseled.block_prop, common.ref ) );

			markDirty();
			worldObj.markBlockForUpdate( pos );

			if ( oldSideFlags == null || oldSideFlags != sideFlags )
			{
				worldObj.notifyNeighborsOfStateChange( pos, worldObj.getBlockState( pos ).getBlock() );
			}
		}
		else
		{
			worldObj.setBlockToAir( pos ); // no physical matter left...
		}

		if ( olv == null || olv != lv )
		{
			worldObj.checkLight( pos );
		}
	}

	public ItemStack getItemStack(
			final Block what,
			final EntityPlayer player )
	{
		final ItemStack itemstack = new ItemStack( what, 1 );

		if ( player != null )
		{
			EnumFacing enumfacing = ModUtil.getPlaceFace( player );

			final TileEntityBlockChiseled test = new TileEntityBlockChiseled();
			VoxelBlob vb = getBlob();

			int rotations = ModUtil.getRotationIndex( enumfacing );
			while ( rotations > 0 )
			{
				rotations--;
				enumfacing = enumfacing.rotateYCCW();
				vb = vb.spin( Axis.Y );
			}

			test.setBlob( vb );

			final NBTTagCompound nbttagcompound = new NBTTagCompound();
			test.writeChisleData( nbttagcompound );
			itemstack.setTagInfo( "BlockEntityTag", nbttagcompound );

			itemstack.setTagInfo( "side", new NBTTagByte( (byte) enumfacing.ordinal() ) );
		}
		else
		{
			final NBTTagCompound nbttagcompound = new NBTTagCompound();
			writeChisleData( nbttagcompound );
			itemstack.setTagInfo( "BlockEntityTag", nbttagcompound );
		}
		return itemstack;
	}

	public boolean isSideSolid(
			final EnumFacing side )
	{
		final Integer sideFlags = getBasicState().getValue( BlockChiseled.side_prop );

		if ( sideFlags == null )
		{
			return true; // if torches or other blocks are on the block this
			// prevents a conversion from crashing.
		}

		return ( sideFlags & 1 << side.ordinal() ) != 0;
	}

	@SideOnly( Side.CLIENT )
	public boolean isSideOpaque(
			final EnumFacing side )
	{
		final Integer sideFlags = ChisledBlockSmartModel.getSides( this );
		return ( sideFlags & 1 << side.ordinal() ) != 0;
	}

	public boolean isDynamicRender()
	{
		return renderChunk == null ? false : renderChunk.isDyanmic;
	}
}

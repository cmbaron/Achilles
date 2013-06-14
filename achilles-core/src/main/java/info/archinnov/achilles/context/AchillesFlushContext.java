package info.archinnov.achilles.context;

import info.archinnov.achilles.type.ConsistencyLevel;

/**
 * AchillesFlushContext
 * 
 * @author DuyHai DOAN
 * 
 */
public abstract class AchillesFlushContext
{
	protected ConsistencyContext consistencyContext;

	public abstract void startBatch();

	public abstract void flush();

	public abstract void endBatch();

	public abstract void cleanUp();

	public abstract void setWriteConsistencyLevel(ConsistencyLevel writeLevel);

	public abstract void setReadConsistencyLevel(ConsistencyLevel readLevel);

	public abstract void reinitConsistencyLevels();

	public abstract FlushType type();

	public static enum FlushType
	{
		IMMEDIATE,
		BATCH
	}

}
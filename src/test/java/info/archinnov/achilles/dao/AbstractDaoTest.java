package info.archinnov.achilles.dao;

import static info.archinnov.achilles.entity.metadata.PropertyType.SIMPLE;
import static info.archinnov.achilles.serializer.SerializerUtils.LONG_SRZ;
import static org.mockito.Mockito.verify;
import info.archinnov.achilles.common.CassandraDaoTest;
import info.archinnov.achilles.consistency.AchillesConfigurableConsistencyLevelPolicy;
import info.archinnov.achilles.serializer.SerializerUtils;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.DynamicComposite;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * AbstractDaoTest
 * 
 * @author DuyHai DOAN
 * 
 */

@RunWith(MockitoJUnitRunner.class)
public class AbstractDaoTest
{

	private GenericEntityDao<Long> abstractDao;

	private Keyspace keyspace = CassandraDaoTest.getKeyspace();

	@Mock
	private Cluster cluster = CassandraDaoTest.getCluster();

	@Mock
	private AchillesConfigurableConsistencyLevelPolicy policy;

	private String columnFamily = "CompleteBean";

	@Before
	public void setUp()
	{
		abstractDao = new GenericEntityDao<Long>(cluster, keyspace, LONG_SRZ, columnFamily, policy);
	}

	@Test
	public void should_reinit_consistency_level() throws Exception
	{

		DynamicComposite dynamicComposite = new DynamicComposite();
		dynamicComposite.setComponent(0, SIMPLE.flag(), SerializerUtils.BYTE_SRZ);
		dynamicComposite.setComponent(1, "name", SerializerUtils.STRING_SRZ);
		abstractDao.getValue(123L, dynamicComposite);
		verify(policy).loadConsistencyLevelForRead(columnFamily);
		verify(policy).reinitDefaultConsistencyLevel();
	}

	@Test
	public void should_reinit_consistency_level_after_exception() throws Exception
	{
		Whitebox.setInternalState(abstractDao, "columnFamily", "xxx");
		try
		{
			DynamicComposite dynamicComposite = new DynamicComposite();
			dynamicComposite.setComponent(0, SIMPLE.flag(), SerializerUtils.BYTE_SRZ);
			dynamicComposite.setComponent(1, "name", SerializerUtils.STRING_SRZ);
			abstractDao.getValue(123L, dynamicComposite);
		}
		catch (RuntimeException e)
		{
			verify(policy).loadConsistencyLevelForRead("xxx");
			verify(policy).reinitDefaultConsistencyLevel();
		}

	}
}

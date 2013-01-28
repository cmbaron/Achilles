package fr.doan.achilles.holder.factory;

import static fr.doan.achilles.entity.metadata.PropertyType.JOIN_SIMPLE;
import static fr.doan.achilles.entity.metadata.PropertyType.WIDE_MAP;
import static fr.doan.achilles.serializer.SerializerUtils.BYTE_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.COMPOSITE_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.DYNA_COMP_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.INT_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.OBJECT_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.STRING_SRZ;
import static fr.doan.achilles.serializer.SerializerUtils.UUID_SRZ;
import static me.prettyprint.hector.api.beans.AbstractComposite.ComponentEquality.EQUAL;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import mapping.entity.TweetMultiKey;
import mapping.entity.UserBean;
import me.prettyprint.cassandra.model.HColumnImpl;
import me.prettyprint.cassandra.utils.TimeUUIDUtils;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.beans.DynamicComposite;
import me.prettyprint.hector.api.beans.HColumn;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.ImmutableMap;

import fr.doan.achilles.entity.PropertyHelper;
import fr.doan.achilles.entity.metadata.EntityMeta;
import fr.doan.achilles.entity.metadata.JoinProperties;
import fr.doan.achilles.entity.metadata.MultiKeyProperties;
import fr.doan.achilles.entity.metadata.PropertyMeta;
import fr.doan.achilles.entity.metadata.PropertyType;
import fr.doan.achilles.entity.operations.EntityLoader;
import fr.doan.achilles.holder.KeyValue;
import fr.doan.achilles.serializer.SerializerUtils;

/**
 * KeyValueFactoryTest
 * 
 * @author DuyHai DOAN
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyValueFactoryTest
{

	@InjectMocks
	private KeyValueFactory factory;

	@Mock
	private PropertyMeta<Integer, String> wideMapMeta;

	@Mock
	private PropertyMeta<TweetMultiKey, String> multiKeyWideMeta;

	@Mock
	private MultiKeyProperties multiKeyProperties;

	@Mock
	private PropertyMeta<Integer, UserBean> joinPropertyMeta;

	@Mock
	private EntityLoader loader;

	@Mock
	private PropertyHelper helper;

	@Before
	public void setUp()
	{
		ReflectionTestUtils.setField(factory, "loader", loader);
		when(multiKeyWideMeta.getMultiKeyProperties()).thenReturn(multiKeyProperties);
	}

	@Test
	public void should_create() throws Exception
	{
		KeyValue<Integer, String> built = factory.create(15, "test");
		assertThat(built.getKey()).isEqualTo(15);
		assertThat(built.getValue()).isEqualTo("test");
	}

	@Test
	public void should_create_with_ttl() throws Exception
	{
		KeyValue<Integer, String> built = factory.create(15, "test", 14);
		assertThat(built.getKey()).isEqualTo(15);
		assertThat(built.getValue()).isEqualTo("test");
		assertThat(built.getTtl()).isEqualTo(14);
	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_from_dynamic_composite_hcolumn() throws Exception
	{
		HColumn<DynamicComposite, Object> hColumn = new HColumnImpl<DynamicComposite, Object>(
				DYNA_COMP_SRZ, OBJECT_SRZ);
		DynamicComposite dynComp = new DynamicComposite();
		dynComp.setComponent(0, 10, INT_SRZ);
		dynComp.setComponent(1, 10, INT_SRZ);
		dynComp.setComponent(2, 1, INT_SRZ);
		hColumn.setName(dynComp);
		hColumn.setValue("test");
		hColumn.setTtl(12);

		when(wideMapMeta.getKeySerializer()).thenReturn((Serializer) INT_SRZ);
		when(wideMapMeta.getValue("test")).thenReturn("test");
		when(wideMapMeta.isSingleKey()).thenReturn(true);

		KeyValue<Integer, String> keyValue = factory
				.createKeyValueForDynamicComposite(wideMapMeta, hColumn);

		assertThat(keyValue.getKey()).isEqualTo(1);
		assertThat(keyValue.getValue()).isEqualTo("test");
		assertThat(keyValue.getTtl()).isEqualTo(12);
	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_join_entity_from_dynamic_composite_hcolumn() throws Exception
	{
		Integer joinId = 114523;
		HColumn<DynamicComposite, Object> hColumn = new HColumnImpl<DynamicComposite, Object>(
				DYNA_COMP_SRZ, OBJECT_SRZ);
		DynamicComposite dynComp = new DynamicComposite();
		dynComp.setComponent(0, 10, INT_SRZ);
		dynComp.setComponent(1, 10, INT_SRZ);
		dynComp.setComponent(2, 1, INT_SRZ);
		hColumn.setName(dynComp);
		hColumn.setValue(joinId);
		hColumn.setTtl(12);

		EntityMeta<Integer> joinEntityMeta = new EntityMeta<Integer>();
		JoinProperties joinProperties = new JoinProperties();
		joinProperties.setEntityMeta(joinEntityMeta);

		when(joinPropertyMeta.getKeySerializer()).thenReturn((Serializer) INT_SRZ);
		when(joinPropertyMeta.isSingleKey()).thenReturn(true);
		when(joinPropertyMeta.type()).thenReturn(JOIN_SIMPLE);
		when(joinPropertyMeta.getValueClass()).thenReturn(UserBean.class);
		when(joinPropertyMeta.getJoinProperties()).thenReturn((JoinProperties) joinProperties);

		UserBean userBean = new UserBean();
		when(loader.loadJoinEntities(UserBean.class, Arrays.asList(joinId), joinEntityMeta))
				.thenReturn(ImmutableMap.of(joinId, userBean));

		List<KeyValue<Integer, UserBean>> keyValues = factory.createKeyValueListForDynamicComposite(
				joinPropertyMeta, Arrays.asList(hColumn));

		assertThat(keyValues).hasSize(1);
		KeyValue<Integer, UserBean> keyValue = keyValues.get(0);
		assertThat(keyValue.getKey()).isEqualTo(1);
		assertThat(keyValue.getValue()).isSameAs(userBean);
		assertThat(keyValue.getTtl()).isEqualTo(12);
	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_join_entity_from_composite_hcolumn() throws Exception
	{
		Integer joinId = 114523;
		HColumn<Composite, Integer> hColumn = new HColumnImpl<Composite, Integer>(COMPOSITE_SRZ,
				INT_SRZ);
		Composite comp = new Composite();
		comp.setComponent(0, 10, INT_SRZ);
		hColumn.setName(comp);
		hColumn.setValue(joinId);
		hColumn.setTtl(12);

		EntityMeta<Integer> joinEntityMeta = new EntityMeta<Integer>();
		JoinProperties joinProperties = new JoinProperties();
		joinProperties.setEntityMeta(joinEntityMeta);

		when(joinPropertyMeta.getKeySerializer()).thenReturn((Serializer) INT_SRZ);
		when(joinPropertyMeta.isSingleKey()).thenReturn(true);
		when(joinPropertyMeta.type()).thenReturn(PropertyType.JOIN_SIMPLE);
		when(joinPropertyMeta.getValueClass()).thenReturn(UserBean.class);
		when(joinPropertyMeta.getJoinProperties()).thenReturn((JoinProperties) joinProperties);

		UserBean userBean = new UserBean();
		when(loader.loadJoinEntities(UserBean.class, Arrays.asList(joinId), joinEntityMeta))
				.thenReturn(ImmutableMap.of(joinId, userBean));

		List<KeyValue<Integer, UserBean>> keyValues = factory.createListForComposite(
				joinPropertyMeta, (List) Arrays.asList(hColumn));

		assertThat(keyValues).hasSize(1);
		KeyValue<Integer, UserBean> keyValue = keyValues.get(0);

		assertThat(keyValue.getKey()).isEqualTo(10);
		assertThat(keyValue.getValue()).isSameAs(userBean);
		assertThat(keyValue.getTtl()).isEqualTo(12);
	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_from_composite_column_list() throws Exception
	{
		HColumn<Composite, String> hColumn1 = new HColumnImpl<Composite, String>(COMPOSITE_SRZ,
				STRING_SRZ);
		Composite comp1 = new Composite();
		comp1.addComponent(0, 1, EQUAL);
		hColumn1.setName(comp1);
		hColumn1.setValue("test1");

		HColumn<Composite, String> hColumn2 = new HColumnImpl<Composite, String>(COMPOSITE_SRZ,
				STRING_SRZ);
		Composite comp2 = new Composite();
		comp2.addComponent(0, 2, EQUAL);
		hColumn2.setName(comp2);
		hColumn2.setValue("test2");

		HColumn<Composite, String> hColumn3 = new HColumnImpl<Composite, String>(COMPOSITE_SRZ,
				STRING_SRZ);
		Composite comp3 = new Composite();
		comp3.addComponent(0, 3, EQUAL);
		hColumn3.setName(comp3);
		hColumn3.setValue("test3");

		when(wideMapMeta.isSingleKey()).thenReturn(true);
		when(wideMapMeta.getKeySerializer()).thenReturn((Serializer) INT_SRZ);
		when(wideMapMeta.getValue("test1")).thenReturn("test1");
		when(wideMapMeta.getValue("test2")).thenReturn("test2");
		when(wideMapMeta.getValue("test3")).thenReturn("test3");
		when(wideMapMeta.type()).thenReturn(PropertyType.WIDE_MAP);

		List<KeyValue<Integer, String>> builtList = factory.createListForComposite(//
				wideMapMeta, //
				(List) Arrays.asList(hColumn1, hColumn2, hColumn3));

		assertThat(builtList).hasSize(3);

		assertThat(builtList.get(0).getKey()).isEqualTo(1);
		assertThat(builtList.get(0).getValue()).isEqualTo("test1");
		assertThat(builtList.get(0).getTtl()).isEqualTo(0);

		assertThat(builtList.get(1).getKey()).isEqualTo(2);
		assertThat(builtList.get(1).getValue()).isEqualTo("test2");
		assertThat(builtList.get(1).getTtl()).isEqualTo(0);

		assertThat(builtList.get(2).getKey()).isEqualTo(3);
		assertThat(builtList.get(2).getValue()).isEqualTo("test3");
		assertThat(builtList.get(2).getTtl()).isEqualTo(0);

	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_from_dynamic_composite_hcolumn_list() throws Exception
	{
		HColumn<DynamicComposite, Object> hColumn1 = new HColumnImpl<DynamicComposite, Object>(
				DYNA_COMP_SRZ, OBJECT_SRZ);
		DynamicComposite dynComp1 = new DynamicComposite();
		dynComp1.setComponent(0, 10, INT_SRZ);
		dynComp1.setComponent(1, 10, INT_SRZ);
		dynComp1.setComponent(2, 1, INT_SRZ);
		hColumn1.setName(dynComp1);
		hColumn1.setValue("test1");
		hColumn1.setTtl(12);

		HColumn<DynamicComposite, Object> hColumn2 = new HColumnImpl<DynamicComposite, Object>(
				DYNA_COMP_SRZ, OBJECT_SRZ);
		DynamicComposite dynComp2 = new DynamicComposite();
		dynComp2.setComponent(0, 10, INT_SRZ);
		dynComp2.setComponent(1, 10, INT_SRZ);
		dynComp2.setComponent(2, 2, INT_SRZ);
		hColumn2.setName(dynComp2);
		hColumn2.setValue("test2");
		hColumn2.setTtl(11);

		when(wideMapMeta.getValue("test1")).thenReturn("test1");
		when(wideMapMeta.getValue("test2")).thenReturn("test2");
		when(wideMapMeta.getKeySerializer()).thenReturn((Serializer) INT_SRZ);
		when(wideMapMeta.isSingleKey()).thenReturn(true);
		when(wideMapMeta.type()).thenReturn(WIDE_MAP);

		List<KeyValue<Integer, String>> list = factory.createKeyValueListForDynamicComposite(wideMapMeta,
				Arrays.asList(hColumn1, hColumn2));

		assertThat(list).hasSize(2);

		assertThat(list.get(0).getKey()).isEqualTo(1);
		assertThat(list.get(0).getValue()).isEqualTo("test1");
		assertThat(list.get(0).getTtl()).isEqualTo(12);

		assertThat(list.get(1).getKey()).isEqualTo(2);
		assertThat(list.get(1).getValue()).isEqualTo("test2");
		assertThat(list.get(1).getTtl()).isEqualTo(11);
	}

	@SuppressWarnings(
	{
			"unchecked",
			"rawtypes"
	})
	@Test
	public void should_create_multikey_from_composite_hcolumn_list() throws Exception
	{
		Method authorSetter = TweetMultiKey.class.getDeclaredMethod("setAuthor", String.class);
		Method idSetter = TweetMultiKey.class.getDeclaredMethod("setId", UUID.class);
		Method retweetCountSetter = TweetMultiKey.class.getDeclaredMethod("setRetweetCount",
				int.class);

		UUID uuid1 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();
		UUID uuid2 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();
		UUID uuid3 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();

		HColumn<Composite, String> hCol1 = buildHColumn(buildComposite("author1", uuid1, 11),
				"val1");
		HColumn<Composite, String> hCol2 = buildHColumn(buildComposite("author2", uuid2, 12),
				"val2");
		HColumn<Composite, String> hCol3 = buildHColumn(buildComposite("author3", uuid3, 13),
				"val3");

		when(multiKeyWideMeta.getKeyClass()).thenReturn(TweetMultiKey.class);
		when(multiKeyWideMeta.isSingleKey()).thenReturn(false);

		when(multiKeyWideMeta.type()).thenReturn(PropertyType.WIDE_MAP);

		when(multiKeyProperties.getComponentSerializers()).thenReturn(
				Arrays.asList((Serializer<?>) STRING_SRZ, UUID_SRZ, INT_SRZ));
		when(multiKeyProperties.getComponentSetters()).thenReturn(
				Arrays.asList(authorSetter, idSetter, retweetCountSetter));

		TweetMultiKey tweetKey1 = new TweetMultiKey();
		TweetMultiKey tweetKey2 = new TweetMultiKey();
		TweetMultiKey tweetKey3 = new TweetMultiKey();

		when(helper.buildMultiKeyForComposite(multiKeyWideMeta, hCol1.getName().getComponents()))
				.thenReturn(tweetKey1);
		when(helper.buildMultiKeyForComposite(multiKeyWideMeta, hCol2.getName().getComponents()))
				.thenReturn(tweetKey2);
		when(helper.buildMultiKeyForComposite(multiKeyWideMeta, hCol3.getName().getComponents()))
				.thenReturn(tweetKey3);

		List<KeyValue<TweetMultiKey, String>> multiKeys = factory.createListForComposite(
				multiKeyWideMeta, (List) Arrays.asList(hCol1, hCol2, hCol3));

		assertThat(multiKeys).hasSize(3);

		assertThat(multiKeys.get(0).getKey()).isSameAs(tweetKey1);
		assertThat(multiKeys.get(1).getKey()).isSameAs(tweetKey2);
		assertThat(multiKeys.get(2).getKey()).isSameAs(tweetKey3);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void should_create_multikey_from_dynamic_composite_hcolumn_list() throws Exception
	{
		Method authorSetter = TweetMultiKey.class.getDeclaredMethod("setAuthor", String.class);
		Method idSetter = TweetMultiKey.class.getDeclaredMethod("setId", UUID.class);
		Method retweetCountSetter = TweetMultiKey.class.getDeclaredMethod("setRetweetCount",
				int.class);

		UUID uuid1 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();
		UUID uuid2 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();
		UUID uuid3 = TimeUUIDUtils.getUniqueTimeUUIDinMillis();

		HColumn<DynamicComposite, Object> hCol1 = buildDynamicHColumn(
				buildDynamicComposite("author1", uuid1, 11), "val1");
		HColumn<DynamicComposite, Object> hCol2 = buildDynamicHColumn(
				buildDynamicComposite("author2", uuid2, 12), "val2");
		HColumn<DynamicComposite, Object> hCol3 = buildDynamicHColumn(
				buildDynamicComposite("author3", uuid3, 13), "val3");

		when(multiKeyWideMeta.getKeyClass()).thenReturn(TweetMultiKey.class);
		when(multiKeyWideMeta.type()).thenReturn(WIDE_MAP);

		when(multiKeyProperties.getComponentSerializers()).thenReturn(
				Arrays.asList((Serializer<?>) STRING_SRZ, UUID_SRZ, INT_SRZ));
		when(multiKeyProperties.getComponentSetters()).thenReturn(
				Arrays.asList(authorSetter, idSetter, retweetCountSetter));

		TweetMultiKey tweetKey1 = new TweetMultiKey();
		TweetMultiKey tweetKey2 = new TweetMultiKey();
		TweetMultiKey tweetKey3 = new TweetMultiKey();

		when(
				helper.buildMultiKeyForDynamicComposite(multiKeyWideMeta, hCol1.getName()
						.getComponents())).thenReturn(tweetKey1);
		when(
				helper.buildMultiKeyForDynamicComposite(multiKeyWideMeta, hCol2.getName()
						.getComponents())).thenReturn(tweetKey2);
		when(
				helper.buildMultiKeyForDynamicComposite(multiKeyWideMeta, hCol3.getName()
						.getComponents())).thenReturn(tweetKey3);

		List<KeyValue<TweetMultiKey, String>> multiKeys = factory.createKeyValueListForDynamicComposite(
				multiKeyWideMeta, Arrays.asList(hCol1, hCol2, hCol3));

		assertThat(multiKeys).hasSize(3);

		assertThat(multiKeys.get(0).getKey()).isSameAs(tweetKey1);
		assertThat(multiKeys.get(1).getKey()).isSameAs(tweetKey2);
		assertThat(multiKeys.get(2).getKey()).isSameAs(tweetKey3);
	}

	private HColumn<Composite, String> buildHColumn(Composite comp, String value)
	{
		HColumn<Composite, String> hColumn = new HColumnImpl<Composite, String>(COMPOSITE_SRZ,
				STRING_SRZ);

		hColumn.setName(comp);
		hColumn.setValue(value);
		return hColumn;
	}

	private HColumn<DynamicComposite, Object> buildDynamicHColumn(DynamicComposite comp,
			String value)
	{
		HColumn<DynamicComposite, Object> hColumn = new HColumnImpl<DynamicComposite, Object>(
				SerializerUtils.DYNA_COMP_SRZ, SerializerUtils.OBJECT_SRZ);

		hColumn.setName(comp);
		hColumn.setValue(value);
		return hColumn;
	}

	private Composite buildComposite(String author, UUID uuid, int retweetCount)
	{
		Composite composite = new Composite();
		composite.setComponent(0, author, STRING_SRZ, STRING_SRZ.getComparatorType().getTypeName());
		composite.setComponent(1, uuid, UUID_SRZ, UUID_SRZ.getComparatorType().getTypeName());
		composite.setComponent(2, retweetCount, INT_SRZ, INT_SRZ.getComparatorType().getTypeName());

		return composite;
	}

	private DynamicComposite buildDynamicComposite(String author, UUID uuid, int retweetCount)
	{
		DynamicComposite composite = new DynamicComposite();
		composite.setComponent(0, PropertyType.WIDE_MAP.flag(), BYTE_SRZ, BYTE_SRZ
				.getComparatorType().getTypeName());
		composite.setComponent(1, "multiKey1", STRING_SRZ, STRING_SRZ.getComparatorType()
				.getTypeName());
		composite.setComponent(2, author, STRING_SRZ, STRING_SRZ.getComparatorType().getTypeName());
		composite.setComponent(3, uuid, UUID_SRZ, UUID_SRZ.getComparatorType().getTypeName());
		composite.setComponent(4, retweetCount, INT_SRZ, INT_SRZ.getComparatorType().getTypeName());

		return composite;
	}
}

/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jdbc.core;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.assertj.core.api.SoftAssertions;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.testing.DatabaseProfileValueSource;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.relational.core.conversion.RelationalConverter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.annotation.ProfileValueUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link JdbcAggregateTemplate}.
 *
 * @author Jens Schauder
 * @author Thomas Lang
 * @author Mark Paluch
 */
@ContextConfiguration
@Transactional
@ProfileValueSourceConfiguration(DatabaseProfileValueSource.class)
public class JdbcAggregateTemplateWithSchemaIntegrationTests {

	@ClassRule public static final SpringClassRule classRule = new SpringClassRule();
	@Rule public SpringMethodRule methodRule = new SpringMethodRule();

	@Autowired JdbcAggregateOperations template;
	@Autowired NamedParameterJdbcOperations jdbcTemplate;
	LegoSet legoSet = createLegoSet();

	@Test // DATAJDBC-276
	public void saveAndLoadAnEntityWithListOfElementsWithoutId() {

		ListParent entity = new ListParent();
		entity.name = "name";

		ElementNoId element = new ElementNoId();
		element.content = "content";

		entity.content.add(element);

		template.save(entity);

		ListParent reloaded = template.findById(entity.id, ListParent.class);

		assertThat(reloaded.content).extracting(e -> e.content).containsExactly("content");
	}

	@Test // DATAJDBC-259
	public void saveAndLoadAnEntityWithList() {

		// MySQL and others do not support array datatypes. See
		// https://dev.mysql.com/doc/refman/8.0/en/data-type-overview.html
		assumeNot("mysql");
		assumeNot("mariadb");
		assumeNot("mssql");

		ListOwner arrayOwner = new ListOwner();
		arrayOwner.digits.addAll(Arrays.asList("one", "two", "three"));

		ListOwner saved = template.save(arrayOwner);

		assertThat(saved.id).isNotNull();

		ListOwner reloaded = template.findById(saved.id, ListOwner.class);

		assertThat(reloaded).isNotNull();
		assertThat(reloaded.id).isEqualTo(saved.id);
		assertThat(reloaded.digits).isEqualTo(Arrays.asList("one", "two", "three"));
	}

	@Test // DATAJDBC-259
	public void saveAndLoadAnEntityWithSet() {

		// MySQL and others do not support array datatypes. See
		// https://dev.mysql.com/doc/refman/8.0/en/data-type-overview.html
		assumeNot("mysql");
		assumeNot("mariadb");
		assumeNot("mssql");

		SetOwner setOwner = new SetOwner();
		setOwner.digits.addAll(Arrays.asList("one", "two", "three"));

		SetOwner saved = template.save(setOwner);

		assertThat(saved.id).isNotNull();

		SetOwner reloaded = template.findById(saved.id, SetOwner.class);

		assertThat(reloaded).isNotNull();
		assertThat(reloaded.id).isEqualTo(saved.id);
		assertThat(reloaded.digits).isEqualTo(new HashSet<>(Arrays.asList("one", "two", "three")));
	}

	@Test // DATAJDBC-340
	public void saveAndLoadLongChain() {

		Chain4 chain4 = new Chain4();
		chain4.fourValue = "omega";
		chain4.chain3 = new Chain3();
		chain4.chain3.threeValue = "delta";
		chain4.chain3.chain2 = new Chain2();
		chain4.chain3.chain2.twoValue = "gamma";
		chain4.chain3.chain2.chain1 = new Chain1();
		chain4.chain3.chain2.chain1.oneValue = "beta";
		chain4.chain3.chain2.chain1.chain0 = new Chain0();
		chain4.chain3.chain2.chain1.chain0.zeroValue = "alpha";

		template.save(chain4);

		Chain4 reloaded = template.findById(chain4.four, Chain4.class);

		assertThat(reloaded).isNotNull();

		assertThat(reloaded.four).isEqualTo(chain4.four);
		assertThat(reloaded.chain3.chain2.chain1.chain0.zeroValue).isEqualTo(chain4.chain3.chain2.chain1.chain0.zeroValue);

		template.delete(chain4, Chain4.class);

		assertThat(count("CHAIN0")).isEqualTo(0);
	}

	@Test // DATAJDBC-359
	public void saveAndLoadLongChainWithoutIds() {

		NoIdChain4 chain4 = new NoIdChain4();
		chain4.fourValue = "omega";
		chain4.chain3 = new NoIdChain3();
		chain4.chain3.threeValue = "delta";
		chain4.chain3.chain2 = new NoIdChain2();
		chain4.chain3.chain2.twoValue = "gamma";
		chain4.chain3.chain2.chain1 = new NoIdChain1();
		chain4.chain3.chain2.chain1.oneValue = "beta";
		chain4.chain3.chain2.chain1.chain0 = new NoIdChain0();
		chain4.chain3.chain2.chain1.chain0.zeroValue = "alpha";

		template.save(chain4);

		assertThat(chain4.four).isNotNull();

		NoIdChain4 reloaded = template.findById(chain4.four, NoIdChain4.class);

		assertThat(reloaded).isNotNull();

		assertThat(reloaded.four).isEqualTo(chain4.four);
		assertThat(reloaded.chain3.chain2.chain1.chain0.zeroValue).isEqualTo(chain4.chain3.chain2.chain1.chain0.zeroValue);

		template.delete(chain4, NoIdChain4.class);

		assertThat(count("CHAIN0")).isEqualTo(0);
	}

	@Test // DATAJDBC-223
	public void saveAndLoadLongChainOfListsWithoutIds() {

		NoIdListChain4 saved = template.save(createNoIdTree());

		assertThat(saved.four).describedAs("Something went wrong during saving").isNotNull();

		NoIdListChain4 reloaded = template.findById(saved.four, NoIdListChain4.class);
		assertThat(reloaded).isEqualTo(saved);
	}

	@Test // DATAJDBC-223
	public void shouldDeleteChainOfListsWithoutIds() {

		NoIdListChain4 saved = template.save(createNoIdTree());
		template.deleteById(saved.four, NoIdListChain4.class);

		SoftAssertions.assertSoftly(softly -> {

			softly.assertThat(count("NO_ID_LIST_CHAIN4")).describedAs("Chain4 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_LIST_CHAIN3")).describedAs("Chain3 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_LIST_CHAIN2")).describedAs("Chain2 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_LIST_CHAIN1")).describedAs("Chain1 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_LIST_CHAIN0")).describedAs("Chain0 elements got deleted").isEqualTo(0);
		});
	}

	/**
	 * creates an instance of {@link NoIdListChain4} with the following properties:
	 * <ul>
	 * <li>Each element has two children with indices 0 and 1.</li>
	 * <li>the xxxValue of each element is a {@literal v} followed by the indices used to navigate to the given instance.
	 * </li>
	 * </ul>
	 */
	private static NoIdListChain4 createNoIdTree() {

		NoIdListChain4 chain4 = new NoIdListChain4();
		chain4.fourValue = "v";

		IntStream.of(0, 1).forEach(i -> {

			NoIdListChain3 c3 = new NoIdListChain3();
			c3.threeValue = chain4.fourValue + i;
			chain4.chain3.add(c3);

			IntStream.of(0, 1).forEach(j -> {

				NoIdListChain2 c2 = new NoIdListChain2();
				c2.twoValue = c3.threeValue + j;
				c3.chain2.add(c2);

				IntStream.of(0, 1).forEach(k -> {

					NoIdListChain1 c1 = new NoIdListChain1();
					c1.oneValue = c2.twoValue + k;
					c2.chain1.add(c1);

					IntStream.of(0, 1).forEach(m -> {

						NoIdListChain0 c0 = new NoIdListChain0();
						c0.zeroValue = c1.oneValue + m;
						c1.chain0.add(c0);
					});
				});
			});
		});

		return chain4;
	}

	@Test // DATAJDBC-223
	public void saveAndLoadLongChainOfMapsWithoutIds() {

		NoIdMapChain4 saved = template.save(createNoIdMapTree());

		assertThat(saved.four).isNotNull();

		NoIdMapChain4 reloaded = template.findById(saved.four, NoIdMapChain4.class);
		assertThat(reloaded).isEqualTo(saved);
	}

	@Test // DATAJDBC-223
	public void shouldDeleteChainOfMapsWithoutIds() {

		NoIdMapChain4 saved = template.save(createNoIdMapTree());
		template.deleteById(saved.four, NoIdMapChain4.class);

		SoftAssertions.assertSoftly(softly -> {

			softly.assertThat(count("NO_ID_MAP_CHAIN4")).describedAs("Chain4 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_MAP_CHAIN3")).describedAs("Chain3 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_MAP_CHAIN2")).describedAs("Chain2 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_MAP_CHAIN1")).describedAs("Chain1 elements got deleted").isEqualTo(0);
			softly.assertThat(count("NO_ID_MAP_CHAIN0")).describedAs("Chain0 elements got deleted").isEqualTo(0);
		});
	}

	private static NoIdMapChain4 createNoIdMapTree() {

		NoIdMapChain4 chain4 = new NoIdMapChain4();
		chain4.fourValue = "v";

		IntStream.of(0, 1).forEach(i -> {

			NoIdMapChain3 c3 = new NoIdMapChain3();
			c3.threeValue = chain4.fourValue + i;
			chain4.chain3.put(asString(i), c3);

			IntStream.of(0, 1).forEach(j -> {

				NoIdMapChain2 c2 = new NoIdMapChain2();
				c2.twoValue = c3.threeValue + j;
				c3.chain2.put(asString(j), c2);

				IntStream.of(0, 1).forEach(k -> {

					NoIdMapChain1 c1 = new NoIdMapChain1();
					c1.oneValue = c2.twoValue + k;
					c2.chain1.put(asString(k), c1);

					IntStream.of(0, 1).forEach(it -> {

						NoIdMapChain0 c0 = new NoIdMapChain0();
						c0.zeroValue = c1.oneValue + it;
						c1.chain0.put(asString(it), c0);
					});
				});
			});
		});

		return chain4;
	}

	private static String asString(int i) {
		return "_" + i;
	}

	private Long count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, emptyMap(), Long.class);
	}

	private static void assumeNot(String dbProfileName) {

		Assume.assumeTrue("true".equalsIgnoreCase(
				ProfileValueUtils.retrieveProfileValueSource(JdbcAggregateTemplateWithSchemaIntegrationTests.class)
						.get("current.database.is.not." + dbProfileName)));
	}

	private static class ArrayOwner {
		@Id Long id;

		String[] digits;
		String[][] multidimensional;
	}

	private static class ByteArrayOwner {
		@Id Long id;

		byte[] binaryData;
	}

	@Table("ARRAY_OWNER")
	private static class ListOwner {
		@Id Long id;

		List<String> digits = new ArrayList<>();
	}

	@Table("ARRAY_OWNER")
	private static class SetOwner {
		@Id Long id;

		Set<String> digits = new HashSet<>();
	}

	private static LegoSet createLegoSet() {

		LegoSet entity = new LegoSet();
		entity.setName("Star Destroyer");

		Manual manual = new Manual();
		manual.setContent("Accelerates to 99% of light speed. Destroys almost everything. See https://what-if.xkcd.com/1/");
		entity.setManual(manual);

		return entity;
	}

	@Data
	static class LegoSet {

		@Column("id1") @Id private Long id;

		private String name;

		private Manual manual;
		@Column("alternative") private Manual alternativeInstructions;
	}

	@Data
	static class Manual {

		@Column("id2") @Id private Long id;
		private String content;

	}

	static class OneToOneParent {

		@Column("id3") @Id private Long id;
		private String content;

		private ChildNoId child;
	}

	static class ChildNoId {
		private String content;
	}

	static class ListParent {

		@Column("id4") @Id private Long id;
		String name;
		List<ElementNoId> content = new ArrayList<>();
	}

	static class ElementNoId {
		private String content;
	}

	/**
	 * One may think of ChainN as a chain with N further elements
	 */
	static class Chain0 {
		@Id Long zero;
		String zeroValue;
	}

	static class Chain1 {
		@Id Long one;
		String oneValue;
		Chain0 chain0;
	}

	static class Chain2 {
		@Id Long two;
		String twoValue;
		Chain1 chain1;
	}

	static class Chain3 {
		@Id Long three;
		String threeValue;
		Chain2 chain2;
	}

	static class Chain4 {
		@Id Long four;
		String fourValue;
		Chain3 chain3;
	}

	/**
	 * One may think of ChainN as a chain with N further elements
	 */
	static class NoIdChain0 {
		String zeroValue;
	}

	static class NoIdChain1 {
		String oneValue;
		NoIdChain0 chain0;
	}

	static class NoIdChain2 {
		String twoValue;
		NoIdChain1 chain1;
	}

	static class NoIdChain3 {
		String threeValue;
		NoIdChain2 chain2;
	}

	static class NoIdChain4 {
		@Id Long four;
		String fourValue;
		NoIdChain3 chain3;
	}

	/**
	 * One may think of ChainN as a chain with N further elements
	 */
	@EqualsAndHashCode
	static class NoIdListChain0 {
		String zeroValue;
	}

	@EqualsAndHashCode
	static class NoIdListChain1 {
		String oneValue;
		List<NoIdListChain0> chain0 = new ArrayList<>();
	}

	@EqualsAndHashCode
	static class NoIdListChain2 {
		String twoValue;
		List<NoIdListChain1> chain1 = new ArrayList<>();
	}

	@EqualsAndHashCode
	static class NoIdListChain3 {
		String threeValue;
		List<NoIdListChain2> chain2 = new ArrayList<>();
	}

	@EqualsAndHashCode
	static class NoIdListChain4 {
		@Id Long four;
		String fourValue;
		List<NoIdListChain3> chain3 = new ArrayList<>();
	}

	/**
	 * One may think of ChainN as a chain with N further elements
	 */
	@EqualsAndHashCode
	static class NoIdMapChain0 {
		String zeroValue;
	}

	@EqualsAndHashCode
	static class NoIdMapChain1 {
		String oneValue;
		Map<String, NoIdMapChain0> chain0 = new HashMap<>();
	}

	@EqualsAndHashCode
	static class NoIdMapChain2 {
		String twoValue;
		Map<String, NoIdMapChain1> chain1 = new HashMap<>();
	}

	@EqualsAndHashCode
	static class NoIdMapChain3 {
		String threeValue;
		Map<String, NoIdMapChain2> chain2 = new HashMap<>();
	}

	@EqualsAndHashCode
	static class NoIdMapChain4 {
		@Id Long four;
		String fourValue;
		Map<String, NoIdMapChain3> chain3 = new HashMap<>();
	}

	@Configuration
	@Import(TestConfiguration.class)
	static class Config {

		@Bean
		Class<?> testClass() {
			return JdbcAggregateTemplateWithSchemaIntegrationTests.class;
		}

		@Bean
		NamingStrategy namingStrategy() {
			return new NamingStrategy() {
				@Override
				public String getSchema() {
					return "demo";
				}
			};
		}

		@Bean
		JdbcAggregateOperations operations(ApplicationEventPublisher publisher, RelationalMappingContext context,
				DataAccessStrategy dataAccessStrategy, RelationalConverter converter) {
			return new JdbcAggregateTemplate(publisher, context, converter, dataAccessStrategy);
		}
	}
}

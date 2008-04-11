/*
 * Copyright (c) 2005-2008 Grameen Foundation USA
 * All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */
package org.mifos.framework.util.helpers;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;
import org.mifos.framework.security.activity.DynamicLookUpValueCreationTypes;

public class StringUtilsTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(StringUtilsTest.class);
	}

	@Test
	public void testLpad() {
		assertEquals("___blah", StringUtils.lpad("blah", '_', 7));
	}

	@Test
	public void testCamelCase() {
		assertEquals("AbcDef_ghIjKL", StringUtils
				.camelCase("aBc dEF_gh-iJ  k.l"));
	}
	
	@Test
	public void testGenerateLookupName() {
		String newElementText = "OfficeLevels";
		String lookupName = StringUtils.generateLookupName(DynamicLookUpValueCreationTypes.DBUpgrade.name(), 
				newElementText);
		assertEquals(0, lookupName.indexOf("DBUpgrade"));
		String tooLong = 
			  "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
			+ "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
			+ "0";
		lookupName = StringUtils.generateLookupName(DynamicLookUpValueCreationTypes.DBUpgrade.name(), 
				tooLong);
		assertEquals(100, lookupName.length());
		
	}

	@Test
	public void testCreateCsv() throws Exception {
		ArrayList<String> list = new ArrayList<String>();
		list.add("1");
		list.add("2");
		list.add("3");
		String csv = StringUtils.createCsv(list);
		assertEquals("1,2,3", csv);
	}
	
//	@Test
//	public void testTemp() throws Exception {
//		String csv = StringUtils.createCsv(new RolesPermissionsPersistence().getRoles(),
//				new Transformer() {
//					public Object transform(Object input) {
//						return ((RoleBO) input).getId();
//					}
//				});
//		assertEquals("1,2", csv);
//	}
}

/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */

package org.mifos.customers.business;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mifos.application.master.business.ValueListElement;
import org.mifos.customers.persistence.CustomerDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/integration-test-context.xml",
                                    "/org/mifos/config/resources/hibernate-daos.xml"})
public class MasterDataRetrievalForCustomersIntegrationTest {

    @Autowired
    private CustomerDao customerDao;

    @Test
    public void shouldRetrieveSalutations() {

        List<ValueListElement> salutations = customerDao.retrieveSalutations();

        assertThat(salutations, is(notNullValue()));
    }

    @Test
    public void shouldRetrieveMaritalStatuses() {

        List<ValueListElement> salutations = customerDao.retrieveMaritalStatuses();

        assertThat(salutations, is(notNullValue()));
        assertThat(salutations.size(), is(3));
    }

    @Test
    public void shouldRetrieveCitizenship() {

        List<ValueListElement> salutations = customerDao.retrieveCitizenship();

        assertThat(salutations, is(notNullValue()));
        assertThat(salutations.size(), is(3));
    }

    @Test
    public void shouldRetrieveBusinessActivities() {

        List<ValueListElement> businessActivities = customerDao.retrieveBusinessActivities();

        assertThat(businessActivities, is(notNullValue()));
        assertThat(businessActivities.size(), is(6));
    }

    @Test
    public void shouldRetrieveEducationLevels() {

        List<ValueListElement> educationLevels = customerDao.retrieveEducationLevels();

        assertThat(educationLevels, is(notNullValue()));
        assertThat(educationLevels.size(), is(4));
    }

    @Test
    public void shouldRetrieveGenders() {

        List<ValueListElement> genders = customerDao.retrieveGenders();

        assertThat(genders, is(notNullValue()));
        assertThat(genders.size(), is(2));
    }

    @Test
    public void shouldRetrieveEthinicity() {

        List<ValueListElement> ethinicity = customerDao.retrieveEthinicity();

        assertThat(ethinicity, is(notNullValue()));
        assertThat(ethinicity.size(), is(5));
    }

    @Test
    public void shouldRetrieveHandicapped() {

        List<ValueListElement> handicaped = customerDao.retrieveHandicapped();

        assertThat(handicaped, is(notNullValue()));
        assertThat(handicaped.size(), is(2));
    }

    @Test
    public void shouldRetrievePoverty() {

        List<ValueListElement> poverty = customerDao.retrievePoverty();

        assertThat(poverty, is(notNullValue()));
        assertThat(poverty.size(), is(3));
    }

    @Test
    public void shouldRetrieveLivingStatus() {

        List<ValueListElement> livingStatus = customerDao.retrieveLivingStatus();

        assertThat(livingStatus, is(notNullValue()));
        assertThat(livingStatus.size(), is(2));
    }
}
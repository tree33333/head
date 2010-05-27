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

package org.mifos.framework.components.batchjobs.helpers;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.Date;
import java.util.Set;

import static org.mifos.framework.util.helpers.IntegrationTestObjectMother.sampleBranchOffice;
import static org.mifos.framework.util.helpers.IntegrationTestObjectMother.testUser;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mifos.accounts.business.AccountActionDateEntity;
import org.mifos.application.collectionsheet.persistence.CenterBuilder;
import org.mifos.application.collectionsheet.persistence.ClientBuilder;
import org.mifos.application.collectionsheet.persistence.GroupBuilder;
import org.mifos.application.collectionsheet.persistence.MeetingBuilder;
import org.mifos.application.master.business.MifosCurrency;
import org.mifos.application.meeting.business.MeetingBO;
import org.mifos.customers.business.CustomerScheduleEntity;
import org.mifos.customers.center.business.CenterBO;
import org.mifos.customers.client.business.ClientBO;
import org.mifos.customers.group.business.GroupBO;
import org.mifos.customers.persistence.CustomerDao;
import org.mifos.framework.TestUtils;
import org.mifos.framework.components.batchjobs.MifosTask;
import org.mifos.framework.util.StandardTestingService;
import org.mifos.framework.util.helpers.DatabaseSetup;
import org.mifos.framework.util.helpers.IntegrationTestObjectMother;
import org.mifos.framework.util.helpers.Money;
import org.mifos.service.test.TestMode;
import org.mifos.test.framework.util.DatabaseCleaner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/integration-test-context.xml",
                                    "/org/mifos/config/resources/hibernate-daos.xml"})
public class RegenerateScheduleBatchJobIntegrationTest {

    // class under test
    private RegenerateScheduleHelper regenerateScheduleHelper;

    // collaborators
    private CenterBO center;
    private GroupBO group;
    private ClientBO client;
    private MeetingBO weeklyMeeting;
    private MeetingBO updatedMeeting;
    private DateTime fiveWeeksInPast;

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private static MifosCurrency oldDefaultCurrency;

    @BeforeClass
    public static void initialiseHibernateUtil() {

        oldDefaultCurrency = Money.getDefaultCurrency();
        Money.setDefaultCurrency(TestUtils.RUPEE);
        new StandardTestingService().setTestMode(TestMode.INTEGRATION);
        DatabaseSetup.initializeHibernate();
    }

    @AfterClass
    public static void resetCurrency() {
        Money.setDefaultCurrency(oldDefaultCurrency);
    }

    @After
    public void cleanDatabaseTablesAfterTest() {
        // NOTE: - only added to stop older integration tests failing due to brittleness
        databaseCleaner.clean();
    }

    @Before
    public void cleanDatabaseTables() {
        databaseCleaner.clean();

        fiveWeeksInPast = new DateTime().minusWeeks(5);
        weeklyMeeting = new MeetingBuilder().customerMeeting().weekly().every(1).withStartDate(fiveWeeksInPast).build();
        IntegrationTestObjectMother.saveMeeting(weeklyMeeting);

        updatedMeeting = new MeetingBuilder().customerMeeting().weekly().every(1).withStartDate(fiveWeeksInPast.plusDays(2)).build();
        IntegrationTestObjectMother.saveMeeting(updatedMeeting);

        MifosTask mifosTask = null;
        regenerateScheduleHelper = new RegenerateScheduleHelper(mifosTask);
    }

    @Test
    public void shouldRegenerateFutureSchedulesWhenMeetingIsChanged() throws Exception {

        // setup
        center = new CenterBuilder().withName("Center1")
                                    .with(weeklyMeeting)
                                    .with(sampleBranchOffice())
                                    .withLoanOfficer(testUser())
                                    .build();
        IntegrationTestObjectMother.createCenter(center, weeklyMeeting);

        group = new GroupBuilder().withName("group1").withParentCustomer(center).formedBy(testUser()).build();
        IntegrationTestObjectMother.createGroup(group, weeklyMeeting);

        client = new ClientBuilder().withName("client1").withParentCustomer(group).buildForIntegrationTests();
        IntegrationTestObjectMother.createClient(client, weeklyMeeting);

        IntegrationTestObjectMother.updateCustomerMeeting(center, updatedMeeting);

        long timeInMillis = new Date().getTime();

        // pre-verification
        center = customerDao.findCenterBySystemId(center.getGlobalCustNum());
        group = customerDao.findGroupBySystemId(group.getGlobalCustNum());
        client = customerDao.findClientBySystemId(client.getGlobalCustNum());

        assertThatFutureSchedulesOccurOnA(center.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.getDayOfWeek());
        assertThatFutureSchedulesOccurOnA(group.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.getDayOfWeek());
        assertThatFutureSchedulesOccurOnA(client.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.getDayOfWeek());

        // exercise test
        regenerateScheduleHelper.execute(timeInMillis);

        // verification
        center = customerDao.findCenterBySystemId(center.getGlobalCustNum());
        group = customerDao.findGroupBySystemId(group.getGlobalCustNum());
        client = customerDao.findClientBySystemId(client.getGlobalCustNum());

        assertThatFutureSchedulesOccurOnA(center.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.plusDays(2).getDayOfWeek());
        assertThatFutureSchedulesOccurOnA(group.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.plusDays(2).getDayOfWeek());
        assertThatFutureSchedulesOccurOnA(client.getCustomerAccount().getAccountActionDates(), fiveWeeksInPast.plusDays(2).getDayOfWeek());
    }

    private void assertThatFutureSchedulesOccurOnA(Set<AccountActionDateEntity> customerSchedules, int expectedDayOfWeek) {
        for (AccountActionDateEntity accountActionDateEntity : customerSchedules) {
            CustomerScheduleEntity customerSchedule = (CustomerScheduleEntity) center.getCustomerAccount().getAccountActionDate(accountActionDateEntity.getInstallmentId());

            DateTime scheduledDate = new DateTime(customerSchedule.getActionDate());

            if (scheduledDate.isAfter(new DateTime())) {
                assertThat(scheduledDate.getDayOfWeek(), is(expectedDayOfWeek));
            }
        }
    }
}
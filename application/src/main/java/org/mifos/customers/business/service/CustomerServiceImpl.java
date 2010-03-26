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

package org.mifos.customers.business.service;

import java.io.InputStream;
import java.sql.Blob;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.mifos.accounts.business.AccountFeesEntity;
import org.mifos.accounts.exceptions.AccountException;
import org.mifos.accounts.productdefinition.business.SavingsOfferingBO;
import org.mifos.accounts.productdefinition.util.helpers.RecommendedAmountUnit;
import org.mifos.accounts.savings.business.SavingsBO;
import org.mifos.accounts.savings.persistence.SavingsPersistence;
import org.mifos.accounts.util.helpers.AccountState;
import org.mifos.application.holiday.business.Holiday;
import org.mifos.application.holiday.persistence.HolidayDao;
import org.mifos.application.master.MessageLookup;
import org.mifos.application.master.business.CustomFieldDefinitionEntity;
import org.mifos.application.master.business.CustomFieldView;
import org.mifos.application.meeting.business.MeetingBO;
import org.mifos.application.servicefacade.CenterUpdate;
import org.mifos.application.servicefacade.ClientPersonalInfoUpdate;
import org.mifos.application.servicefacade.DependencyInjectedServiceLocator;
import org.mifos.application.servicefacade.GroupUpdate;
import org.mifos.application.util.helpers.EntityType;
import org.mifos.calendar.CalendarUtils;
import org.mifos.config.FiscalCalendarRules;
import org.mifos.config.util.helpers.ConfigurationConstants;
import org.mifos.core.MifosRuntimeException;
import org.mifos.customers.business.CustomerAccountBO;
import org.mifos.customers.business.CustomerBO;
import org.mifos.customers.business.CustomerHierarchyEntity;
import org.mifos.customers.business.CustomerMeetingEntity;
import org.mifos.customers.business.CustomerStatusEntity;
import org.mifos.customers.business.CustomerView;
import org.mifos.customers.center.business.CenterBO;
import org.mifos.customers.client.business.ClientBO;
import org.mifos.customers.client.business.ClientInitialSavingsOfferingEntity;
import org.mifos.customers.client.persistence.ClientPersistence;
import org.mifos.customers.client.util.helpers.ClientConstants;
import org.mifos.customers.exceptions.CustomerException;
import org.mifos.customers.group.business.GroupBO;
import org.mifos.customers.group.util.helpers.GroupConstants;
import org.mifos.customers.office.business.OfficeBO;
import org.mifos.customers.office.persistence.OfficeDao;
import org.mifos.customers.persistence.CustomerDao;
import org.mifos.customers.persistence.CustomerPersistence;
import org.mifos.customers.personnel.business.PersonnelBO;
import org.mifos.customers.personnel.persistence.PersonnelDao;
import org.mifos.customers.util.helpers.CustomerConstants;
import org.mifos.customers.util.helpers.CustomerLevel;
import org.mifos.customers.util.helpers.CustomerStatus;
import org.mifos.framework.exceptions.InvalidDateException;
import org.mifos.framework.exceptions.PersistenceException;
import org.mifos.framework.hibernate.helper.StaticHibernateUtil;
import org.mifos.framework.util.DateTimeService;
import org.mifos.schedule.ScheduledDateGeneration;
import org.mifos.schedule.ScheduledEvent;
import org.mifos.schedule.ScheduledEventFactory;
import org.mifos.schedule.internal.HolidayAndWorkingDaysScheduledDateGeneration;
import org.mifos.security.util.UserContext;

public class CustomerServiceImpl implements CustomerService {

    private final CustomerDao customerDao;
    private final PersonnelDao personnelDao;
    private final OfficeDao officeDao;

    public CustomerServiceImpl(CustomerDao customerDao, PersonnelDao personnelDao, OfficeDao officeDao) {
        this.customerDao = customerDao;
        this.personnelDao = personnelDao;
        this.officeDao = officeDao;
    }

    @Override
    public void updateCenter(UserContext userContext, CenterUpdate centerUpdate, CenterBO center) {

        Short loanOfficerId = centerUpdate.getLoanOfficerId();
        PersonnelBO loanOfficer = personnelDao.findPersonnelById(loanOfficerId);

        try {
            if (center.isActive()) {
                center.validateLO(loanOfficer);
            }

            center.setMfiJoiningDate(centerUpdate.getMfiJoiningDateTime().toDate());

            if (center.isLOChanged(loanOfficerId)) {
                // If a new loan officer has been assigned, then propagate this
                // change to the customer's children and to their associated
                // accounts.
                new CustomerPersistence().updateLOsForAllChildren(loanOfficerId, center.getSearchId(), center
                        .getOffice().getOfficeId());

                new CustomerPersistence().updateLOsForAllChildrenAccounts(loanOfficerId, center.getSearchId(), center
                        .getOffice().getOfficeId());

                center.setLoanOfficer(loanOfficer);
            }

            center.setExternalId(centerUpdate.getExternalId());
            center.setUserContext(userContext);
            center.updateAddress(centerUpdate.getAddress());
            center.updateCustomFields(centerUpdate.getCustomFields());
            center.updateCustomerPositions(centerUpdate.getCustomerPositions());
            center.setUpdatedBy(userContext.getId());
            center.setUpdatedDate(new DateTime().toDate());

            StaticHibernateUtil.startTransaction();

            customerDao.save(center);

            StaticHibernateUtil.commitTransaction();

        } catch (CustomerException e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } catch (InvalidDateException e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public void create(CustomerBO customer, MeetingBO meeting, List<AccountFeesEntity> accountFees) {

        try {
            StaticHibernateUtil.startTransaction();
            this.customerDao.save(customer);

            // CREATE CUSTOMER FEE SCHEDULE AND MEETING SCHEDULE FROM CUSTOMER
            // generate meeting schedule based on customer meeting!!!

            List<Days> workingDays = new FiscalCalendarRules().getWorkingDaysAsJodaTimeDays();
            HolidayDao holidayDao = DependencyInjectedServiceLocator.locateHolidayDao();
            List<Holiday> thisAndNextYearsHolidays = holidayDao.findAllHolidaysThisYearAndNext();

            DateTime startFromMeetingDate = new DateTime(meeting.getMeetingStartDate());
            ScheduledEvent scheduledEvent = ScheduledEventFactory.createScheduledEventFrom(meeting);

            ScheduledDateGeneration dateGeneration = new HolidayAndWorkingDaysScheduledDateGeneration(workingDays,
                    thisAndNextYearsHolidays);
            List<DateTime> installmentDates = dateGeneration.generateScheduledDates(10, startFromMeetingDate,
                    scheduledEvent);

            CustomerAccountBO customerAccount = CustomerAccountBO.createNew(customer, accountFees, installmentDates);
            customer.addAccount(customerAccount);
            this.customerDao.save(customer);
            StaticHibernateUtil.commitTransaction();

            StaticHibernateUtil.startTransaction();
            customer.generateGlobalCustomerNumber();
            this.customerDao.save(customer);
            StaticHibernateUtil.commitTransaction();
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public void createGroup(GroupBO group, MeetingBO meeting, List<AccountFeesEntity> accountFees)
            throws CustomerException {

        group.validate();

        customerDao.validateGroupNameIsNotTakenForOffice(group.getDisplayName(), group.getOffice().getOfficeId());

        try {
            StaticHibernateUtil.startTransaction();

            this.customerDao.save(group);

            // CREATE CUSTOMER FEE SCHEDULE AND MEETING SCHEDULE FROM CUSTOMER
            // generate meeting schedule based on customer meeting!!!

            List<Days> workingDays = new FiscalCalendarRules().getWorkingDaysAsJodaTimeDays();
            HolidayDao holidayDao = DependencyInjectedServiceLocator.locateHolidayDao();
            List<Holiday> thisAndNextYearsHolidays = holidayDao.findAllHolidaysThisYearAndNext();

            DateTime startFromMeetingDate = new DateTime(meeting.getMeetingStartDate());
            ScheduledEvent scheduledEvent = ScheduledEventFactory.createScheduledEventFrom(meeting);

            ScheduledDateGeneration dateGeneration = new HolidayAndWorkingDaysScheduledDateGeneration(workingDays,
                    thisAndNextYearsHolidays);
            List<DateTime> installmentDates = dateGeneration.generateScheduledDates(10, startFromMeetingDate,
                    scheduledEvent);

            CustomerAccountBO customerAccount = CustomerAccountBO.createNew(group, accountFees, installmentDates);
            group.addAccount(customerAccount);
            this.customerDao.save(group);
            StaticHibernateUtil.commitTransaction();

            StaticHibernateUtil.startTransaction();
            group.generateGlobalCustomerNumber();
            this.customerDao.save(group);
            StaticHibernateUtil.commitTransaction();
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public void updateGroup(UserContext userContext, GroupUpdate groupUpdate, GroupBO group) throws CustomerException {

        group.validate();

        if (!group.getDisplayName().equals(groupUpdate.getDisplayName())) {
            // customerDao.validateGroupNameIsNotTakenForOffice(groupUpdate.getDisplayName(),
            // group.getOffice().getOfficeId());
        }

        Short loanOfficerId = groupUpdate.getLoanOfficerId();
        PersonnelBO loanOfficer = personnelDao.findPersonnelById(loanOfficerId);

        try {
            DateTime trainedDate = null;

            if (groupUpdate.getTrainedDateAsString() != null) {
                trainedDate = CalendarUtils.getDateFromString(groupUpdate.getTrainedDateAsString(), userContext
                        .getPreferredLocale());
            }

            if (groupUpdate.isTrained()) {
                group.setTrained(groupUpdate.isTrained());
                group.setTrainedDate(trainedDate.toDate());
            } else {
                group.setTrained(false);
            }

            if (group.isActive()) {
                group.validateLO(loanOfficer);
            }

            if (group.isLOChanged(loanOfficerId)) {
                // If a new loan officer has been assigned, then propagate this
                // change to the customer's children and to their associated
                // accounts.
                new CustomerPersistence().updateLOsForAllChildren(loanOfficerId, group.getSearchId(), group.getOffice()
                        .getOfficeId());

                new CustomerPersistence().updateLOsForAllChildrenAccounts(loanOfficerId, group.getSearchId(), group
                        .getOffice().getOfficeId());

                group.setLoanOfficer(loanOfficer);
            }

            group.setExternalId(groupUpdate.getExternalId());
            group.setUserContext(userContext);
            group.updateAddress(groupUpdate.getAddress());
            group.updateCustomFields(groupUpdate.getCustomFields());
            group.updateCustomerPositions(groupUpdate.getCustomerPositions());
            group.setUpdatedBy(userContext.getId());
            group.setUpdatedDate(new DateTime().toDate());
            group.setDisplayName(groupUpdate.getDisplayName());

            StaticHibernateUtil.startTransaction();

            customerDao.save(group);

            StaticHibernateUtil.commitTransaction();

        } catch (CustomerException e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } catch (InvalidDateException e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public GroupBO transferGroupTo(GroupBO group, CenterBO transferToCenter) throws CustomerException {

        // FIXME - keithw - moved out transfer logic into service as first step in cleaning up.
        group.validateNewCenter(transferToCenter);
        group.validateForActiveAccounts();

        OfficeBO centerOffice = transferToCenter.getOffice();
        if (group.isDifferentBranch(centerOffice)) {
            group.makeCustomerMovementEntries(centerOffice);
            if (group.isActive()) {
                group.setCustomerStatus(new CustomerStatusEntity(CustomerStatus.GROUP_HOLD));
            }
        }

        CustomerBO oldParent = group.getParentCustomer();
        group.setParentCustomer(transferToCenter);

        CustomerHierarchyEntity currentHierarchy = group.getActiveCustomerHierarchy();
        if (null != currentHierarchy) {
            currentHierarchy.makeInactive(group.getUserContext().getId());
        }
        group.addCustomerHierarchy(new CustomerHierarchyEntity(group, transferToCenter));

        // handle parent
        group.setPersonnel(transferToCenter.getPersonnel());

        MeetingBO centerMeeting = transferToCenter.getCustomerMeetingValue();
        MeetingBO groupMeeting = group.getCustomerMeetingValue();
        if (centerMeeting != null) {
            if (groupMeeting != null) {
                if (!groupMeeting.getMeetingId().equals(centerMeeting.getMeetingId())) {
                    group.setUpdatedMeeting(centerMeeting);
                }
            } else {
                CustomerMeetingEntity customerMeeting = group.createCustomerMeeting(centerMeeting);
                group.setCustomerMeeting(customerMeeting);
            }
        } else if (groupMeeting != null) {
            // try {
            // new CustomerPersistence().deleteCustomerMeeting(group);
            group.setCustomerMeeting(null);
            // } catch (PersistenceException e) {
            // throw new MifosRuntimeException(e);
            // }
        }

        if (oldParent != null) {
            oldParent.decrementChildCount();
            oldParent.setUserContext(group.getUserContext());
        }

        transferToCenter.incrementChildCount();
        group.setSearchId(transferToCenter.getSearchId() + "." + String.valueOf(transferToCenter.getMaxChildCount()));

        transferToCenter.setUserContext(group.getUserContext());

        // CustomerHierarchyEntity currentHierarchy1 = getActiveCustomerHierarchy();
        // currentHierarchy1.makeInactive(userContext.getId());
        // this.addCustomerHierarchy(new CustomerHierarchyEntity(this, newParent));
        //
        // addCustomerHierarchy(new CustomerHierarchyEntity(this, newParent));

        try {
            StaticHibernateUtil.startTransaction();

            // group.transferToCenter(transferToCenter);
            group.setUpdateDetails();

            if (oldParent != null) {
                customerDao.save(oldParent);
            }
            customerDao.save(transferToCenter);

            customerDao.save(group);

            Set<CustomerBO> clients = group.getChildren();

            if (clients != null) {
                for (CustomerBO client : clients) {
                    client.setUserContext(group.getUserContext());
                    ((ClientBO) client).handleGroupTransfer();
                    client.setUpdateDetails();
                    customerDao.save(client);
                }
            }
            StaticHibernateUtil.commitTransaction();

            return group;
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }

    }

    @Override
    public GroupBO transferGroupTo(GroupBO group, OfficeBO transferToOffice) throws CustomerException {

        group.validateNewOffice(transferToOffice);

        if (group.isActive()) {
            group.setCustomerStatus(new CustomerStatusEntity(CustomerStatus.GROUP_HOLD));
        }

        group.makeCustomerMovementEntries(transferToOffice);
        group.setPersonnel(null);

        CustomerBO oldParentOfGroup = group.getParentCustomer();

        String searchId = null;
        if (oldParentOfGroup != null) {
            oldParentOfGroup.incrementChildCount();
            searchId = oldParentOfGroup.getSearchId() + "." + oldParentOfGroup.getMaxChildCount();
        } else {
            try {
                int customerCount = new CustomerPersistence().getCustomerCountForOffice(CustomerLevel.GROUP, group
                        .getOffice().getOfficeId());
                searchId = GroupConstants.PREFIX_SEARCH_STRING + String.valueOf(customerCount + 1);
            } catch (PersistenceException pe) {
                throw new CustomerException(pe);
            }
        }
        group.setSearchId(searchId);

        try {
            StaticHibernateUtil.startTransaction();

            group.setUpdateDetails();

            if (oldParentOfGroup != null) {
                customerDao.save(oldParentOfGroup);
            }

            customerDao.save(group);

            Set<CustomerBO> clients = group.getChildren();

            if (clients != null) {
                for (CustomerBO client : clients) {
                    client.setUserContext(group.getUserContext());
                    ((ClientBO) client).handleGroupTransfer();
                    client.setUpdateDetails();
                    customerDao.save(client);
                }
            }
            StaticHibernateUtil.commitTransaction();

            return group;
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public void updateCenterStatus(CenterBO center, CustomerStatus newStatus) throws CustomerException {

        if (newStatus.isCenterInActive()) {

            center.validateChangeToInActive();

            List<CustomerView> groupsThatAreNotClosedOrCanceled = this.customerDao
                    .findClientsThatAreNotCancelledOrClosed(center.getSearchId(), center.getOffice().getOfficeId());

            if (groupsThatAreNotClosedOrCanceled.size() > 0) {
                throw new CustomerException(CustomerConstants.ERROR_STATE_CHANGE_EXCEPTION,
                        new Object[] { MessageLookup.getInstance().lookupLabel(ConfigurationConstants.GROUP,
                                center.getUserContext()) });
            }

        } else if (newStatus.isCenterActive()) {
            center.validateChangeToActive();
        }

        try {
            StaticHibernateUtil.startTransaction();

            customerDao.save(center);
            StaticHibernateUtil.commitTransaction();

        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    @Override
    public void updateGroupStatus(GroupBO group, CustomerStatus oldStatus, CustomerStatus newStatus)
            throws CustomerException {

        validateChangeOfStatusForGroup(group, oldStatus, newStatus);

        try {
            StaticHibernateUtil.startTransaction();

            if (group.isActiveForFirstTime(oldStatus.getValue(), newStatus.getValue())) {
                group.setCustomerActivationDate(new DateTime().toDate());

                group.getCustomerAccount().generateCustomerFeeSchedule();
            }

            Set<CustomerBO> groupChildren = group.getChildren();

            if (oldStatus.isGroupPending() && newStatus.isGroupCancelled() && groupChildren != null) {

                for (CustomerBO child : groupChildren) {

                    ClientBO client = (ClientBO) child;

                    if (client.isPending()) {
                        client.setUserContext(group.getUserContext());
                        changeClientStatus(client, CustomerStatus.CLIENT_PARTIAL, group.getUserContext());
                        customerDao.save(client);
                    }
                }
            }

            customerDao.save(group);
            StaticHibernateUtil.commitTransaction();

        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    private void validateChangeOfStatusForGroup(GroupBO group, CustomerStatus oldStatus, CustomerStatus newStatus)
            throws CustomerException {

        if (newStatus.isGroupClosed()) {
            group.validateNoActiveAccountExist();

            List<CustomerView> clientsThatAreNotClosedOrCanceled = this.customerDao
                    .findClientsThatAreNotCancelledOrClosed(group.getSearchId(), group.getOffice().getOfficeId());

            if (clientsThatAreNotClosedOrCanceled.size() > 0) {
                throw new CustomerException(CustomerConstants.ERROR_STATE_CHANGE_EXCEPTION,
                        new Object[] { MessageLookup.getInstance().lookupLabel(ConfigurationConstants.CLIENT,
                                group.getUserContext()) });
            }
        }

        if (newStatus.isGroupActive()) {
            group.validateGroupCanBeActive();
        }

        if (oldStatus.isGroupCancelled() && newStatus.isGroupPartial()) {

            // handleValidationsForCancelToPartial
            if (group.getParentCustomer() != null && group.getParentCustomer().getCustomerId() != null) {
                group.validateTransitionFromCancelledToPartialIsAllowedBasedOnCenter();
            } else {
                this.officeDao.validateBranchIsActiveWithNoActivePersonnel(group.getOffice().getOfficeId(), group
                        .getUserContext());
            }
        }
    }

    @Override
    public void updateClientStatus(ClientBO client, CustomerStatus oldStatus, CustomerStatus newStatus, UserContext userContext, Short flagId, String notes) throws CustomerException {

        handeClientChangeOfStatus(client, newStatus);
        if (newStatus.isClientActive()) {
            this.officeDao.validateBranchIsActiveWithNoActivePersonnel(client.getOffice().getOfficeId(), userContext);
        }

        try {
            StaticHibernateUtil.startTransaction();

            changeStatus(client, newStatus.getValue(), userContext);

            this.changeClientStatus(client, userContext, flagId, notes);

            customerDao.save(client);
            StaticHibernateUtil.commitTransaction();

        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    private void changeClientStatus(ClientBO client, CustomerStatus transitionToStatus, UserContext userContext) throws AccountException {
        client.setCustomerStatus(new CustomerStatusEntity(transitionToStatus));
        changeClientStatus(client, userContext, null, "");
    }

    private void changeClientStatus(ClientBO client, UserContext userContext, Short flagId, String notes) throws AccountException {
        if (client.isClosedOrCancelled()) {

            if (client.isClientUnderGroup()) {

                CustomerBO parentCustomer = client.getParentCustomer();

                client.resetPositions(parentCustomer);
                parentCustomer.setUserContext(userContext);
                CustomerBO center = parentCustomer.getParentCustomer();
                if (center != null) {
                    parentCustomer.resetPositions(center);
                    center.setUserContext(userContext);
                }
            }

            // close customer account - #MIFOS-1504

            CustomerAccountBO customerAccount = client.getCustomerAccount();
            if (customerAccount.isOpen()) {
                customerAccount.setUserContext(userContext);

                customerAccount.changeStatus(AccountState.CUSTOMER_ACCOUNT_INACTIVE, flagId, notes);
                customerAccount.update();
            }
        }
    }

    private void changeStatus(CustomerBO customer, Short newStatusId, UserContext userContext) throws CustomerException {
        Short oldStatusId = customer.getCustomerStatus().getId();

        if (customer.isClient()) {

            ClientBO client = (ClientBO) customer;

            if (client.isActiveForFirstTime(oldStatusId, newStatusId)) {
                try {
                    client.getCustomerAccount().generateCustomerFeeSchedule();
                } catch (AccountException ae1) {
                    throw new CustomerException(ae1);
                }
            }
            if (client.isActiveForFirstTime(oldStatusId, newStatusId)) {
                client.setCustomerActivationDate(new DateTimeService().getCurrentJavaDateTime());

                if (client.getOfferingsAssociatedInCreate() != null) {
                    for (ClientInitialSavingsOfferingEntity clientOffering : client.getOfferingsAssociatedInCreate()) {
                        try {
                            SavingsOfferingBO savingsOffering = client.getSavingsPrdPersistence().getSavingsProduct(
                                    clientOffering.getSavingsOffering().getPrdOfferingId());

                            if (savingsOffering.isActive()) {

                                List<CustomFieldDefinitionEntity> customFieldDefs = client.getSavingsPersistence()
                                        .retrieveCustomFieldsDefinition(EntityType.SAVINGS.getValue());

                                List<CustomFieldView> customerFieldsForSavings = CustomFieldDefinitionEntity.toDto(
                                        customFieldDefs, userContext.getPreferredLocale());

                                client.addAccount(new SavingsBO(client.getUserContext(), savingsOffering, client,
                                        AccountState.SAVINGS_ACTIVE, savingsOffering.getRecommendedAmount(),
                                        customerFieldsForSavings));
                            }
                        } catch (PersistenceException pe) {
                            throw new CustomerException(pe);
                        } catch (AccountException pe) {
                            throw new CustomerException(pe);
                        }
                    }
                }

                new SavingsPersistence().persistSavingAccounts(client);

                List<Days> workingDays = new FiscalCalendarRules().getWorkingDaysAsJodaTimeDays();
                List<Holiday> holidays = DependencyInjectedServiceLocator.locateHolidayDao()
                        .findAllHolidaysThisYearAndNext();

                // createDepositSchedule
                try {
                    if (client.getParentCustomer() != null) {
                        List<SavingsBO> savingsList = new CustomerPersistence()
                                .retrieveSavingsAccountForCustomer(client.getParentCustomer().getCustomerId());

                        if (client.getParentCustomer().getParentCustomer() != null) {
                            savingsList.addAll(new CustomerPersistence().retrieveSavingsAccountForCustomer(client
                                    .getParentCustomer().getParentCustomer().getCustomerId()));
                        }
                        for (SavingsBO savings : savingsList) {
                            savings.setUserContext(client.getUserContext());

                            // generateAndUpdateDepositActionsForClient
                            if (client.getCustomerMeeting().getMeeting() != null) {
                                if (!(savings.getCustomer().getLevel() == CustomerLevel.GROUP && savings
                                        .getRecommendedAmntUnit().getId().equals(
                                                RecommendedAmountUnit.COMPLETE_GROUP.getValue()))) {

                                    savings.generateDepositAccountActions(client, client.getCustomerMeeting()
                                            .getMeeting(), workingDays, holidays);

                                    savings.update();
                                }
                            }
                        }
                    }

                } catch (PersistenceException pe) {
                    throw new CustomerException(pe);
                } catch (AccountException ae) {
                    throw new CustomerException(ae);
                }
            }
        }
    }

    private void handeClientChangeOfStatus(ClientBO client, CustomerStatus newStatus) throws CustomerException {
        if (client.getParentCustomer() != null) {

            CustomerStatus groupStatus = client.getParentCustomer().getStatus();

            if ((newStatus.isClientActive() || newStatus.isClientPending()) && client.isClientUnderGroup()) {

                if (groupStatus.isGroupCancelled()) {
                    throw new CustomerException(ClientConstants.ERRORS_GROUP_CANCELLED,
                            new Object[] { MessageLookup.getInstance().lookupLabel(ConfigurationConstants.GROUP,
                                    client.getUserContext()) });
                }

                if (client.isGroupStatusLower(newStatus.getValue(), groupStatus.getValue())) {

                    throw new CustomerException(ClientConstants.INVALID_CLIENT_STATUS_EXCEPTION, new Object[] {
                            MessageLookup.getInstance().lookupLabel(ConfigurationConstants.GROUP,
                                    client.getUserContext()),
                            MessageLookup.getInstance().lookupLabel(ConfigurationConstants.CLIENT,
                                    client.getUserContext()) });
                }
            }
        }

        if (newStatus.isClientClosed()) {
            if (client.isAnyLoanAccountOpen() || client.isAnySavingsAccountOpen()) {
                throw new CustomerException(CustomerConstants.CUSTOMER_HAS_ACTIVE_ACCOUNTS_EXCEPTION);
            }
        }

        if (newStatus.isClientActive()) {
            if (client.getPersonnel() == null || client.getPersonnel().getPersonnelId() == null) {
                throw new CustomerException(ClientConstants.CLIENT_LOANOFFICER_NOT_ASSIGNED);
            }

            if (client.getCustomerMeeting() == null || client.getCustomerMeeting().getMeeting() == null) {
                throw new CustomerException(GroupConstants.MEETING_NOT_ASSIGNED);
            }
        }
    }

    @Override
    public void updateClientPersonalInfo(ClientBO client, ClientPersonalInfoUpdate personalInfo) throws InvalidDateException {

        setInitialObjectForAuditLogging(client);
        client.updatePersonalInfo(personalInfo);

        try {
            StaticHibernateUtil.startTransaction();
            InputStream pictureSteam = personalInfo.getPicture();

            if (pictureSteam != null) {
                Blob pictureAsBlob = new ClientPersistence().createBlob(pictureSteam);
                client.createOrUpdatePicture(pictureAsBlob);
            }

            customerDao.save(client);
            StaticHibernateUtil.commitTransaction();
        } catch (Exception e) {
            StaticHibernateUtil.rollbackTransaction();
            throw new MifosRuntimeException(e);
        } finally {
            StaticHibernateUtil.closeSession();
        }
    }

    private void setInitialObjectForAuditLogging(Object object) {
        StaticHibernateUtil.getSessionTL();
        StaticHibernateUtil.getInterceptor().createInitialValueMap(object);
    }
}
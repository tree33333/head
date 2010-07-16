/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 *  explanation of the license and how it is applied.
 */

package org.mifos.ui.core.controller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mifos.platform.questionnaire.contract.QuestionDetail;
import org.mifos.platform.questionnaire.contract.QuestionType;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.testng.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class QuestionTest {

    @Test
    public void testAddAnswerChoice() {
        Question question = new Question(new QuestionDetail());
        question.setChoice("choice1");
        question.addAnswerChoice();
        question.setChoice("choice2");
        question.addAnswerChoice();
        question.setChoice("choice1");
        question.addAnswerChoice();
        question.setChoice("choice3");
        question.addAnswerChoice();
        assertThat(question.getChoices().size(), is(4));
        assertEquals(question.getChoices(), asList("choice1", "choice2", "choice1", "choice3"));
    }

    @Test
    public void testRemoveAnswerChoice() {
        Question question = new Question(new QuestionDetail());
        question.setChoice("choice1");
        question.addAnswerChoice();
        question.setChoice("choice2");
        question.addAnswerChoice();
        question.setChoice("choice1");
        question.addAnswerChoice();
        assertEquals(question.getChoices(), asList("choice1", "choice2", "choice1"));
        question.removeChoice(0);
        assertEquals(question.getChoices(), asList("choice2", "choice1"));
        question.removeChoice(1);
        assertEquals(question.getChoices(), asList("choice2"));
    }

    @Test
    public void shouldGetTitleAndType() {
        assertQuestion("Question Title1", QuestionType.NUMERIC, "Number", new LinkedList<String>());
        assertQuestion("Question Title2", QuestionType.FREETEXT, "Free Text", new LinkedList<String>());
        assertQuestion("Question Title3", QuestionType.DATE, "Date", new LinkedList<String>());
        assertQuestion("Question Title4", QuestionType.SINGLE_SELECT, "Single Select", asList("choice-1", "choice-2"));
        assertQuestion("Question Title5", QuestionType.MULTI_SELECT, "Multi Select", asList("choice1", "choice2"));
    }

    @Test
    public void testQuestionTypeConversion() {
        Question question = new Question(new QuestionDetail());
        question.setType("Number");
        assertThat(question.getType(), is("Number"));
        question.setType("Free Text");
        assertThat(question.getType(), is("Free Text"));
        question.setType("Date");
        assertThat(question.getType(), is("Date"));
        question.setType("Single Select");
        assertThat(question.getType(), is("Single Select"));
        question.setType("Number");
        assertThat(question.getType(), is("Number"));
        question.setType("Multi Select");
        assertThat(question.getType(), is("Multi Select"));
        question.setType("Multi Selects");
        assertNull(question.getType());
    }

    private void assertQuestion(String shortName, QuestionType questionType, String questionTypeString, List<String> choices) {
        Question question = new Question(new QuestionDetail(123, "Question Text", shortName, questionType, choices));
        assertThat(question.getTitle(), is(shortName));
        assertThat(question.getType(), is(questionTypeString));
        assertEquals(question.getChoices(), choices);
    }
}
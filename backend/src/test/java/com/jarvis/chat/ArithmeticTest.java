package com.jarvis.chat;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArithmeticTest {
    private final Arithmetic calculator = new Arithmetic();
    @Test void precedenceAndParentheses() { assertThat(calculator.answer("calculate: (25 * 4) / 2")).contains("Calculator result: 50"); }
    @Test void decimalAccuracy() { assertThat(calculator.answer("calculate: 0.1 + 0.2")).contains("Calculator result: 0.3"); }
    @Test void rejectsInjectionAndDivisionByZero() {
        assertThatThrownBy(() -> calculator.answer("calculate: System.exit(0)" )).hasMessageContaining("Use calculate:");
        assertThatThrownBy(() -> calculator.answer("calculate: 1/0")).hasMessageContaining("division by zero");
    }
    @Test void refusesMalformedAndDeepExpressions() {
        assertThatThrownBy(() -> calculator.answer("calculate: 2 3")).isInstanceOf(com.jarvis.common.ApiException.class);
        assertThatThrownBy(() -> calculator.answer("calculate: " + "(".repeat(30)+"1"+")".repeat(30))).isInstanceOf(com.jarvis.common.ApiException.class);
    }
    @Test void ordinaryQuestionsUseModel() { assertThat(calculator.answer("Explain quadratic equations")).isEmpty(); }
    @Test void calculatorDoesNotInvokeModel() {
        var fake=new ChatServiceTest.FakeProvider();
        var settings=ChatServiceTest.settings(java.time.Duration.ofSeconds(1));
        var service=new ChatService(fake,new PromptFactory(settings),settings);
        assertThat(service.complete(new ChatRequest("calculate: 6*7",null)).block()).isEqualTo("Calculator result: 42");
        assertThat(fake.calls).isZero();
    }
}

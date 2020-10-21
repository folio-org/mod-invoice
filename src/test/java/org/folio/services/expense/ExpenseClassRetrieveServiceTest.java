package org.folio.services.expense;

import static org.folio.invoices.utils.ErrorCodes.EXPENSE_CLASS_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExpenseClassRetrieveServiceTest {

    @InjectMocks
    private ExpenseClassRetrieveService expenseClassRetrieveService;

    @Mock
    private RestClient restClient;

    @Mock
    private RequestContext requestContext;

    @BeforeEach
    public void initMocks() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getExpenseClassByIdShouldReturnExpenseClassNotFoundWhenGetRestClientReturn404() {
        CompletableFuture<ExpenseClass> future = new CompletableFuture<>();
        future.completeExceptionally(new HttpException(404, "Not found"));
        when(restClient.getById(anyString(), any(), eq(ExpenseClass.class))).thenReturn(future);
        String expenseClassId = UUID.randomUUID().toString();
        CompletableFuture<ExpenseClass> resultFuture = expenseClassRetrieveService.getExpenseClassById(expenseClassId, requestContext);

        ExecutionException executionException = assertThrows(ExecutionException.class, resultFuture::get);

        assertThat(executionException.getCause(), instanceOf(HttpException.class));

        HttpException exception = (HttpException) executionException.getCause();

        assertEquals(404, exception.getCode());
        Errors errors = exception.getErrors();

        Error error = errors.getErrors().get(0);
        assertEquals(EXPENSE_CLASS_NOT_FOUND.getCode(), error.getCode());
        assertEquals(expenseClassId, error.getParameters().get(0).getValue());

    }
}

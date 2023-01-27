package org.folio.services.finance.expense;

import static org.folio.invoices.utils.ErrorCodes.EXPENSE_CLASS_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
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
    void getExpenseClassByIdShouldReturnExpenseClassNotFoundWhenGetRestClientReturn404(VertxTestContext vertxTestContext) {
        Promise<ExpenseClass> promise = Promise.promise();
        promise.fail(new HttpException(404, "Not found"));
        when(restClient.get(any(RequestEntry.class), eq(ExpenseClass.class), any(RequestContext.class))).thenReturn(promise.future());
        String expenseClassId = UUID.randomUUID().toString();
        Future<ExpenseClass> future = expenseClassRetrieveService.getExpenseClassById(expenseClassId, requestContext);

        vertxTestContext.assertFailure(future)
          .onComplete(result -> {
            assertThat(result.cause(), instanceOf(HttpException.class));

            HttpException exception = (HttpException) result.cause();

            assertEquals(404, exception.getCode());
            Errors errors = exception.getErrors();

            Error error = errors.getErrors().get(0);
            assertEquals(EXPENSE_CLASS_NOT_FOUND.getCode(), error.getCode());
            assertEquals(expenseClassId, error.getParameters().get(0).getValue());
            vertxTestContext.completeNow();
          });
    }
}

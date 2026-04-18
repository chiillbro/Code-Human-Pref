package com.piggymetrics.notification.service;

import com.piggymetrics.notification.client.AccountServiceClient;
import com.piggymetrics.notification.domain.BudgetAlertState;
import com.piggymetrics.notification.domain.BudgetEvaluation;
import com.piggymetrics.notification.domain.BudgetPeriod;
import com.piggymetrics.notification.domain.BudgetStatus;
import com.piggymetrics.notification.domain.NotificationType;
import com.piggymetrics.notification.domain.Recipient;
import com.piggymetrics.notification.repository.BudgetAlertStateRepository;
import com.piggymetrics.notification.repository.RecipientRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Unit tests for the per-budget cooldown and status-transition
 * detection in {@link BudgetAlertServiceImpl}.
 *
 * <p>The service dispatches work with {@code CompletableFuture.runAsync},
 * so assertions use Mockito's {@code timeout()} to wait for the
 * async tasks, matching the pattern in {@link NotificationServiceImplTest}.
 */
public class BudgetAlertServiceImplTestA {

    @InjectMocks
    private BudgetAlertServiceImpl alertService;

    @Mock
    private AccountServiceClient accountClient;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private BudgetAlertStateRepository alertStateRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private Environment env;

    private static final String ACCOUNT = "testuser";
    private static final String BUDGET_ID = "budget-1";

    private Recipient recipient;

    @Before
    public void setup() {
        initMocks(this);

        // @Value is not processed by initMocks, set it explicitly.
        ReflectionTestUtils.setField(alertService, "cooldownHours", 24L);

        recipient = new Recipient();
        recipient.setAccountName(ACCOUNT);
        recipient.setEmail("test@example.com");

        when(recipientRepository.findWithBudgetAlertsActive())
                .thenReturn(Collections.singletonList(recipient));

        // Provide non-null email templates so MessageFormat doesn't NPE.
        when(env.getProperty(eq(NotificationType.BUDGET_ALERT.getSubject()), anyString()))
                .thenReturn("Budget alert: {0}");
        when(env.getProperty(eq(NotificationType.BUDGET_ALERT.getText()), anyString()))
                .thenReturn("Alert for {0}: {1} at {2,number,percent}");
    }

    // -----------------------------------------------------------------
    // 1. First alert triggers email
    // -----------------------------------------------------------------

    @Test
    public void shouldSendEmailOnFirstAlertForBudget() throws Exception {
        BudgetEvaluation eval = evaluation(BUDGET_ID, BudgetStatus.WARNING);
        when(accountClient.getBudgetStatus(ACCOUNT))
                .thenReturn(Collections.singletonList(eval));

        // No prior state — this is the very first evaluation.
        when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
                .thenReturn(Optional.empty());

        alertService.sendBudgetAlerts();

        verify(emailService, timeout(500))
                .sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());
        verify(alertStateRepository, timeout(500)).save(any(BudgetAlertState.class));
    }

    // -----------------------------------------------------------------
    // 2. Same-status repeat within cooldown → no email
    // -----------------------------------------------------------------

    @Test
    public void shouldNotSendEmailWhenSameStatusWithinCooldownWindow() throws Exception {
        BudgetEvaluation eval = evaluation(BUDGET_ID, BudgetStatus.WARNING);
        when(accountClient.getBudgetStatus(ACCOUNT))
                .thenReturn(Collections.singletonList(eval));

        // Prior alert was 1 hour ago, same status — well within 24h cooldown.
        BudgetAlertState priorState = alertState(BudgetStatus.WARNING, hoursAgo(1));
        when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
                .thenReturn(Optional.of(priorState));

        alertService.sendBudgetAlerts();

        // Give the async task time to complete, then assert no email.
        Thread.sleep(200);
        verify(emailService, never())
                .sendFormatted(any(NotificationType.class), any(Recipient.class), anyString(), anyString());
    }

    // -----------------------------------------------------------------
    // 3. Status transition triggers email even within cooldown
    // -----------------------------------------------------------------

    @Test
    public void shouldSendEmailOnStatusTransitionEvenWithinCooldown() throws Exception {
        // Evaluation now says EXCEEDED…
        BudgetEvaluation eval = evaluation(BUDGET_ID, BudgetStatus.EXCEEDED);
        when(accountClient.getBudgetStatus(ACCOUNT))
                .thenReturn(Collections.singletonList(eval));

        // …but the prior alert (only 1h ago, within cooldown) was for WARNING.
        BudgetAlertState priorState = alertState(BudgetStatus.WARNING, hoursAgo(1));
        when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
                .thenReturn(Optional.of(priorState));

        alertService.sendBudgetAlerts();

        verify(emailService, timeout(500))
                .sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());
        verify(alertStateRepository, timeout(500)).save(any(BudgetAlertState.class));
    }

    // -----------------------------------------------------------------
    // 4. Same-status repeat after cooldown expires → email
    // -----------------------------------------------------------------

    @Test
    public void shouldSendEmailWhenCooldownHasExpired() throws Exception {
        BudgetEvaluation eval = evaluation(BUDGET_ID, BudgetStatus.WARNING);
        when(accountClient.getBudgetStatus(ACCOUNT))
                .thenReturn(Collections.singletonList(eval));

        // Prior alert was 25 hours ago — past the 24h cooldown.
        BudgetAlertState priorState = alertState(BudgetStatus.WARNING, hoursAgo(25));
        when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
                .thenReturn(Optional.of(priorState));

        alertService.sendBudgetAlerts();

        verify(emailService, timeout(500))
                .sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());
        verify(alertStateRepository, timeout(500)).save(any(BudgetAlertState.class));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static BudgetEvaluation evaluation(String budgetId, BudgetStatus status) {
        BudgetEvaluation eval = new BudgetEvaluation();
        eval.setBudgetId(budgetId);
        eval.setCategory("Rent");
        eval.setPeriod(BudgetPeriod.MONTH);
        eval.setCurrency("USD");
        eval.setLimitAmount(new BigDecimal("1000"));
        eval.setActualAmount(new BigDecimal("900"));
        eval.setPercentUsed(0.90);
        eval.setStatus(status);
        return eval;
    }

    private static BudgetAlertState alertState(BudgetStatus lastStatus, Date lastAlertedAt) {
        BudgetAlertState state = new BudgetAlertState();
        state.setAccountName(ACCOUNT);
        state.setBudgetId(BUDGET_ID);
        state.setLastStatus(lastStatus);
        state.setLastAlertedAt(lastAlertedAt);
        return state;
    }

    private static Date hoursAgo(int hours) {
        return new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours));
    }
}

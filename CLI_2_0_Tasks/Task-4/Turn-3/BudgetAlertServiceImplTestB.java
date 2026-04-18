package com.piggymetrics.notification.service;

import com.google.common.collect.ImmutableList;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for the budget alert cooldown + status-transition logic in
 * {@link BudgetAlertServiceImpl}.
 *
 * <p>The service dispatches each recipient on {@link java.util.concurrent.CompletableFuture#runAsync}
 * so positive cases use {@code verify(..., timeout(...))} and negative
 * cases use {@code verify(..., after(...).never())} to give the async
 * task a chance to complete before asserting.
 */
public class BudgetAlertServiceImplTestB {

	private static final String ACCOUNT = "vijay";
	private static final String BUDGET_ID = "b-rent-month";
	private static final long COOLDOWN_HOURS = 24L;
	private static final int ASYNC_WAIT_MS = 500;

	@InjectMocks
	private BudgetAlertServiceImpl service;

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

	private Recipient recipient;

	@Before
	public void setup() {
		initMocks(this);

		// cooldownHours is set via @Value so @InjectMocks leaves it at 0.
		// Pin it explicitly for deterministic windowing.
		ReflectionTestUtils.setField(service, "cooldownHours", COOLDOWN_HOURS);

		// env.getProperty(key, default) on a bare mock returns null, which
		// would blow up MessageFormat.format inside sendAlert. Return the
		// caller-supplied default so the service uses its built-in templates.
		when(env.getProperty(anyString(), anyString()))
				.thenAnswer(i -> i.getArgument(1));

		recipient = new Recipient();
		recipient.setAccountName(ACCOUNT);
		recipient.setEmail("vijay@example.com");

		when(recipientRepository.findWithBudgetAlertsActive())
				.thenReturn(ImmutableList.of(recipient));
	}

	// ---------------------------------------------------------------------
	// 1. First alert for a budget triggers email send
	// ---------------------------------------------------------------------

	@Test
	public void shouldSendEmailForFirstWarningWhenNoPriorStateExists() throws Exception {
		BudgetEvaluation warning = evaluation(BudgetStatus.WARNING);
		when(accountClient.getBudgetStatus(ACCOUNT)).thenReturn(ImmutableList.of(warning));
		when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
				.thenReturn(Optional.empty());

		service.sendBudgetAlerts();

		verify(emailService, timeout(ASYNC_WAIT_MS))
				.sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());

		// Cooldown state must be persisted so future runs respect the window.
		ArgumentCaptor<BudgetAlertState> saved = ArgumentCaptor.forClass(BudgetAlertState.class);
		verify(alertStateRepository, timeout(ASYNC_WAIT_MS)).save(saved.capture());
		assertEquals(ACCOUNT, saved.getValue().getAccountName());
		assertEquals(BUDGET_ID, saved.getValue().getBudgetId());
		assertEquals(BudgetStatus.WARNING, saved.getValue().getLastStatus());
		assertNotNull(saved.getValue().getLastAlertedAt());
	}

	// ---------------------------------------------------------------------
	// 2. Same-status repeat inside the cooldown window is suppressed
	// ---------------------------------------------------------------------

	@Test
	public void shouldNotSendEmailForSameStatusInsideCooldownWindow() throws Exception {
		BudgetEvaluation warning = evaluation(BudgetStatus.WARNING);
		when(accountClient.getBudgetStatus(ACCOUNT)).thenReturn(ImmutableList.of(warning));

		// Prior WARNING alert sent 1 hour ago; cooldown is 24h -> still inside.
		BudgetAlertState recent = alertState(BudgetStatus.WARNING, hoursAgo(1));
		when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
				.thenReturn(Optional.of(recent));

		service.sendBudgetAlerts();

		// Ensure the async task actually executed before we assert a negative.
		verify(alertStateRepository, timeout(ASYNC_WAIT_MS))
				.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID);

		verify(emailService, after(ASYNC_WAIT_MS).never())
				.sendFormatted(any(), any(), any(), any());
		verify(alertStateRepository, never()).save(any(BudgetAlertState.class));
	}

	// ---------------------------------------------------------------------
	// 3. Status transition triggers a new email even inside cooldown
	// ---------------------------------------------------------------------

	@Test
	public void shouldSendEmailOnStatusTransitionEvenInsideCooldownWindow() throws Exception {
		// Evaluation has escalated from WARNING to EXCEEDED since last sweep.
		BudgetEvaluation exceeded = evaluation(BudgetStatus.EXCEEDED);
		when(accountClient.getBudgetStatus(ACCOUNT)).thenReturn(ImmutableList.of(exceeded));

		// Last alert was a WARNING 30 minutes ago — well inside the 24h cooldown.
		BudgetAlertState recentWarning = alertState(BudgetStatus.WARNING, minutesAgo(30));
		when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
				.thenReturn(Optional.of(recentWarning));

		service.sendBudgetAlerts();

		verify(emailService, timeout(ASYNC_WAIT_MS))
				.sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());

		// The persisted state should reflect the NEW status.
		ArgumentCaptor<BudgetAlertState> saved = ArgumentCaptor.forClass(BudgetAlertState.class);
		verify(alertStateRepository, timeout(ASYNC_WAIT_MS)).save(saved.capture());
		assertEquals(BudgetStatus.EXCEEDED, saved.getValue().getLastStatus());
	}

	// ---------------------------------------------------------------------
	// 4. Same-status repeat after cooldown expires does alert again
	// ---------------------------------------------------------------------

	@Test
	public void shouldSendEmailForSameStatusAfterCooldownExpires() throws Exception {
		BudgetEvaluation warning = evaluation(BudgetStatus.WARNING);
		when(accountClient.getBudgetStatus(ACCOUNT)).thenReturn(ImmutableList.of(warning));

		// Last alert was 25h ago; cooldown is 24h -> just past the window.
		BudgetAlertState stale = alertState(BudgetStatus.WARNING, hoursAgo(25));
		when(alertStateRepository.findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID))
				.thenReturn(Optional.of(stale));

		service.sendBudgetAlerts();

		verify(emailService, timeout(ASYNC_WAIT_MS))
				.sendFormatted(eq(NotificationType.BUDGET_ALERT), eq(recipient), anyString(), anyString());
		verify(alertStateRepository, timeout(ASYNC_WAIT_MS)).save(any(BudgetAlertState.class));
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private static BudgetEvaluation evaluation(BudgetStatus status) {
		BudgetEvaluation e = new BudgetEvaluation();
		e.setBudgetId(BUDGET_ID);
		e.setCategory("Rent");
		e.setPeriod(BudgetPeriod.MONTH);
		e.setCurrency("USD");
		e.setLimitAmount(new BigDecimal("1000"));
		e.setActualAmount(new BigDecimal("900"));
		e.setPercentUsed(0.9);
		e.setStatus(status);
		return e;
	}

	private static BudgetAlertState alertState(BudgetStatus status, Date lastAlertedAt) {
		BudgetAlertState s = new BudgetAlertState();
		s.setAccountName(ACCOUNT);
		s.setBudgetId(BUDGET_ID);
		s.setLastStatus(status);
		s.setLastAlertedAt(lastAlertedAt);
		return s;
	}

	private static Date hoursAgo(long hours) {
		return new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours));
	}

	private static Date minutesAgo(long minutes) {
		return new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes));
	}
}

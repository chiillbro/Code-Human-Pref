package com.piggymetrics.statistics.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.piggymetrics.statistics.domain.Budget;
import com.piggymetrics.statistics.domain.BudgetEvaluation;
import com.piggymetrics.statistics.domain.BudgetPeriod;
import com.piggymetrics.statistics.domain.BudgetStatus;
import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.timeseries.DataPoint;
import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import com.piggymetrics.statistics.domain.timeseries.ItemMetric;
import com.piggymetrics.statistics.repository.DataPointRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class BudgetEvaluationServiceImplTest {

	@InjectMocks
	private BudgetEvaluationServiceImpl evaluationService;

	@Mock
	private ExchangeRatesServiceImpl ratesService;

	@Mock
	private DataPointRepository repository;

	private Map<Currency, BigDecimal> rates;

	@Before
	public void setup() {
		initMocks(this);

		rates = ImmutableMap.of(
				Currency.EUR, new BigDecimal("0.8"),
				Currency.RUB, new BigDecimal("80"),
				Currency.USD, BigDecimal.ONE
		);

		// Mirror real ExchangeRatesServiceImpl.convert:
		//   ratio = rates[to] / rates[from]
		//   result = amount * ratio
		when(ratesService.convert(any(Currency.class), any(Currency.class), any(BigDecimal.class)))
				.then(invocation -> {
					Currency from = invocation.getArgument(0);
					Currency to = invocation.getArgument(1);
					BigDecimal amount = invocation.getArgument(2);
					BigDecimal ratio = rates.get(to).divide(rates.get(from), 4, RoundingMode.HALF_UP);
					return amount.multiply(ratio);
				});
	}

	/**
	 * Expense data is stored in USD base. Budget limit is in EUR.
	 * Verifies that the actual spending is converted from USD → EUR
	 * so both actual and limit share the same currency.
	 *
	 * Setup:
	 *   - 1 DataPoint with Rent expense = 30 USD/day
	 *   - Budget: Rent, limit = 20 EUR, period = MONTH
	 *
	 * Expected:
	 *   - actualInBase = 30 USD
	 *   - convert(USD → EUR, 30) = 30 × 0.8 = 24 EUR
	 *   - percent = 24 / 20 = 1.2 → EXCEEDED
	 */
	@Test
	public void shouldConvertActualToEurWhenBudgetCurrencyDiffersFromBase() {

		List<DataPoint> points = ImmutableList.of(
				dataPoint("test", daysAgo(5), ImmutableSet.of(
						new ItemMetric("Rent", new BigDecimal("30.0000"))
				))
		);
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b1", "Rent", new BigDecimal("20"), Currency.EUR, BudgetPeriod.MONTH, null);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);

		// 30 USD × 0.8 (EUR/USD rate) = 24.00 EUR
		assertTrue("actual should be 24.00 EUR",
				new BigDecimal("24.00").compareTo(eval.getActual()) == 0);
		assertTrue("limit should be 20 EUR",
				new BigDecimal("20").compareTo(eval.getLimit()) == 0);
		assertEquals(Currency.EUR, eval.getCurrency());

		// 24 / 20 = 1.2 → EXCEEDED
		assertEquals(BudgetStatus.EXCEEDED, eval.getStatus());
		assertEquals(1.2, eval.getPercentUsed(), 0.001);
	}

	/**
	 * An expense entered with YEAR recurrence is normalised to per-day
	 * amounts when stored in DataPoints. The budget has a MONTH period.
	 * Verifies that summing the per-day amounts over the month-window
	 * DataPoints yields the correct monthly share of the annual expense.
	 *
	 * Setup:
	 *   - Original annual expense: $3652.4250/year → $10.0000/day
	 *   - 30 DataPoints in the current month (one per day)
	 *   - Budget: Insurance, limit = 250 USD, period = MONTH
	 *
	 * Expected:
	 *   - sum = 30 × 10 = 300 USD
	 *   - convert(USD → USD) = 300 USD (identity)
	 *   - percent = 300 / 250 = 1.2 → EXCEEDED
	 */
	@Test
	public void shouldCorrectlyAggregateYearExpenseOverMonthBudgetPeriod() {

		// 30 DataPoints, each representing one day with $10/day insurance expense
		List<DataPoint> points = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			points.add(dataPoint("test", daysAgo(i), ImmutableSet.of(
					new ItemMetric("Insurance", new BigDecimal("10.0000"))
			)));
		}
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b2", "Insurance", new BigDecimal("250"), Currency.USD, BudgetPeriod.MONTH, null);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);

		// 30 days × $10/day = $300
		assertTrue("actual should be 300.00 USD",
				new BigDecimal("300.00").compareTo(eval.getActual()) == 0);
		assertTrue("limit should be 250 USD",
				new BigDecimal("250").compareTo(eval.getLimit()) == 0);

		// 300 / 250 = 1.2 → EXCEEDED
		assertEquals(BudgetStatus.EXCEEDED, eval.getStatus());
		assertEquals(1.2, eval.getPercentUsed(), 0.001);
	}

	/**
	 * Budget category _TOTAL should sum ALL expense ItemMetrics
	 * regardless of their individual titles.
	 *
	 * Setup:
	 *   - 3 DataPoints, each with two expenses:
	 *       Rent  = 20 USD/day
	 *       Food  = 10 USD/day
	 *   - Budget: _TOTAL, limit = 100 USD, period = MONTH
	 *
	 * Expected:
	 *   - sum = 3 × (20 + 10) = 90 USD
	 *   - percent = 90 / 100 = 0.90 → WARNING (≥ default threshold 0.80)
	 */
	@Test
	public void shouldSumAllCategoriesForTotalBudget() {

		List<DataPoint> points = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			points.add(dataPoint("test", daysAgo(i), ImmutableSet.of(
					new ItemMetric("Rent", new BigDecimal("20.0000")),
					new ItemMetric("Food", new BigDecimal("10.0000"))
			)));
		}
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b3", "_TOTAL", new BigDecimal("100"), Currency.USD, BudgetPeriod.MONTH, null);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);

		// 3 × (20 + 10) = 90
		assertTrue("actual should be 90.00 USD",
				new BigDecimal("90.00").compareTo(eval.getActual()) == 0);

		// 90 / 100 = 0.90 → WARNING (>= 0.80 threshold, < 1.0)
		assertEquals(BudgetStatus.WARNING, eval.getStatus());
		assertEquals(0.9, eval.getPercentUsed(), 0.001);
	}

	/**
	 * Verifies that the _TOTAL budget also converts currencies correctly
	 * when the budget is in a different currency than the base.
	 *
	 * Setup:
	 *   - 2 DataPoints, each with:
	 *       Rent = 15 USD/day, Groceries = 5 USD/day  (total 20 USD/day)
	 *   - Budget: _TOTAL, limit = 50 EUR, period = MONTH
	 *
	 * Expected:
	 *   - sumInBase = 2 × (15 + 5) = 40 USD
	 *   - convert(USD → EUR, 40) = 40 × 0.8 = 32 EUR
	 *   - percent = 32 / 50 = 0.64 → UNDER_BUDGET
	 */
	@Test
	public void shouldConvertTotalCategoryWithMultipleCurrencies() {

		List<DataPoint> points = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			points.add(dataPoint("test", daysAgo(i), ImmutableSet.of(
					new ItemMetric("Rent", new BigDecimal("15.0000")),
					new ItemMetric("Groceries", new BigDecimal("5.0000"))
			)));
		}
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b4", "_TOTAL", new BigDecimal("50"), Currency.EUR, BudgetPeriod.MONTH, null);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);

		// 40 USD × 0.8 = 32.00 EUR
		assertTrue("actual should be 32.00 EUR",
				new BigDecimal("32.00").compareTo(eval.getActual()) == 0);
		assertEquals(Currency.EUR, eval.getCurrency());

		// 32 / 50 = 0.64 → UNDER_BUDGET
		assertEquals(BudgetStatus.UNDER_BUDGET, eval.getStatus());
		assertEquals(0.64, eval.getPercentUsed(), 0.001);
	}

	/**
	 * Verifies that the warning threshold is respected: spending at exactly
	 * the threshold triggers WARNING, not UNDER_BUDGET.
	 */
	@Test
	public void shouldReturnWarningWhenPercentEqualsThreshold() {

		// Single DataPoint: 80 USD/day
		List<DataPoint> points = ImmutableList.of(
				dataPoint("test", daysAgo(1), ImmutableSet.of(
						new ItemMetric("Rent", new BigDecimal("80.0000"))
				))
		);
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b5", "Rent", new BigDecimal("100"), Currency.USD, BudgetPeriod.MONTH, 0.80);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);
		assertEquals(BudgetStatus.WARNING, eval.getStatus());
		assertEquals(0.8, eval.getPercentUsed(), 0.001);
	}

	/**
	 * Spending below the warning threshold should be UNDER_BUDGET.
	 */
	@Test
	public void shouldReturnUnderBudgetWhenBelowThreshold() {

		List<DataPoint> points = ImmutableList.of(
				dataPoint("test", daysAgo(1), ImmutableSet.of(
						new ItemMetric("Rent", new BigDecimal("10.0000"))
				))
		);
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b6", "Rent", new BigDecimal("100"), Currency.USD, BudgetPeriod.MONTH, 0.80);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		assertEquals(BudgetStatus.UNDER_BUDGET, results.get(0).getStatus());
		assertEquals(0.1, results.get(0).getPercentUsed(), 0.001);
	}

	/**
	 * When there are no DataPoints for the period, actual should be zero.
	 */
	@Test
	public void shouldReturnZeroActualWhenNoDataPoints() {

		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("test"), any(Date.class)))
				.thenReturn(new ArrayList<>());

		Budget budget = budget("b7", "Rent", new BigDecimal("100"), Currency.USD, BudgetPeriod.MONTH, null);

		List<BudgetEvaluation> results = evaluationService.evaluate("test", ImmutableList.of(budget));

		assertEquals(1, results.size());
		BudgetEvaluation eval = results.get(0);
		assertTrue(BigDecimal.ZERO.compareTo(eval.getActual()) == 0);
		assertEquals(BudgetStatus.UNDER_BUDGET, eval.getStatus());
	}

	// ---- helpers ----------------------------------------------------------------

	private static DataPoint dataPoint(String account, Date date,
									   java.util.Set<ItemMetric> expenses) {
		DataPoint dp = new DataPoint();
		dp.setId(new DataPointId(account, date));
		dp.setExpenses(expenses);
		return dp;
	}

	private static Date daysAgo(int days) {
		return Date.from(LocalDate.now().minusDays(days)
				.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static Budget budget(String id, String category, BigDecimal limit,
								 Currency currency, BudgetPeriod period,
								 Double warningThreshold) {
		Budget b = new Budget();
		b.setId(id);
		b.setCategory(category);
		b.setLimit(limit);
		b.setCurrency(currency);
		b.setPeriod(period);
		b.setWarningThreshold(warningThreshold);
		return b;
	}
}

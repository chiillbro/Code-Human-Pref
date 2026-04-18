package com.piggymetrics.statistics.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.piggymetrics.statistics.domain.Budget;
import com.piggymetrics.statistics.domain.BudgetEvaluation;
import com.piggymetrics.statistics.domain.BudgetPeriod;
import com.piggymetrics.statistics.domain.BudgetStatus;
import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.TimePeriod;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Unit tests for the budget evaluation normalization math.
 *
 * <p>Covers the three critical normalization cases:
 * <ul>
 *   <li>expense currency differs from the budget currency,</li>
 *   <li>expense recurrence period differs from the budget period
 *       (e.g. {@link TimePeriod#YEAR} item vs {@link BudgetPeriod#MONTH}
 *       budget),</li>
 *   <li>the {@link Budget#TOTAL_CATEGORY} aggregate across multiple
 *       distinct expense categories.</li>
 * </ul>
 *
 * <p>By the time data reaches this service, {@link ItemMetric#getAmount()}
 * is already in base currency (USD) per day (see
 * {@link StatisticsServiceImpl#createItemMetric}). Tests therefore
 * construct per-day USD values that correspond to the scenario being
 * described in the test name, which keeps the assertions legible.
 */
public class BudgetEvaluationServiceImplTest {

	@InjectMocks
	private BudgetEvaluationServiceImpl evaluationService;

	@Mock
	private DataPointRepository repository;

	@Mock
	private ExchangeRatesService ratesService;

	/** Mirrors the 1 USD = 0.8 EUR / 80 RUB fixtures used elsewhere. */
	private static final Map<Currency, BigDecimal> RATES = ImmutableMap.of(
			Currency.USD, BigDecimal.ONE,
			Currency.EUR, new BigDecimal("0.8"),
			Currency.RUB, new BigDecimal("80")
	);

	@Before
	public void setup() {
		initMocks(this);

		// Faithful re-implementation of ExchangeRatesServiceImpl.convert so
		// the tests exercise the real formula rather than a simplification.
		when(ratesService.convert(any(Currency.class), any(Currency.class), any(BigDecimal.class)))
				.thenAnswer(i -> {
					Currency from = i.getArgument(0);
					Currency to = i.getArgument(1);
					BigDecimal amount = i.getArgument(2);
					BigDecimal ratio = RATES.get(to).divide(RATES.get(from), 4, RoundingMode.HALF_UP);
					return amount.multiply(ratio);
				});
	}

	// ---------------------------------------------------------------------
	// Currency normalization
	// ---------------------------------------------------------------------

	@Test
	public void shouldConvertActualFromBaseCurrencyIntoBudgetCurrency() {
		// Given: budget defined in EUR, spending stored in USD base.
		Budget budget = budget("b-eur", "Rent", new BigDecimal("1000"), Currency.EUR,
				BudgetPeriod.MONTH, 0.80);

		// 5 days of "Rent" expenses at $200/day (per-day USD is the on-disk form)
		// => $1000 USD total for the period.
		List<DataPoint> points = daysOf(5, expense("Rent", "200"));
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		// When
		List<BudgetEvaluation> result = evaluationService.evaluate("vijay", Collections.singletonList(budget));

		// Then: $1000 USD * (rate EUR / rate USD) = 1000 * 0.8 = 800 EUR.
		assertEquals(1, result.size());
		BudgetEvaluation eval = result.get(0);

		assertEquals(Currency.EUR, eval.getCurrency());
		assertEquals(0, new BigDecimal("1000").compareTo(eval.getLimitAmount()));
		assertEquals(0, new BigDecimal("800.00").compareTo(eval.getActualAmount()));
		// percentUsed = 800 / 1000 = 0.80 -> exactly at threshold => WARNING.
		assertEquals(0.80, eval.getPercentUsed(), 0.0001);
		assertEquals(BudgetStatus.WARNING, eval.getStatus());
	}

	// ---------------------------------------------------------------------
	// Period normalization
	// ---------------------------------------------------------------------

	@Test
	public void shouldAggregateYearlyItemAcrossMonthBudgetPeriod() {
		// Given: user has an item with period=YEAR and amount $12,000/yr.
		// StatisticsServiceImpl.createItemMetric normalises that to
		//   $12,000 / TimePeriod.YEAR.baseRatio = 12000 / 365.2425 USD/day
		// which is what gets persisted on each day's DataPoint.
		BigDecimal dailyFromYearly = new BigDecimal("12000")
				.divide(TimePeriod.YEAR.getBaseRatio(), 4, RoundingMode.HALF_UP);

		// A full month's worth of datapoints, 30 days, each holding that
		// same daily amount for "Insurance".
		List<DataPoint> points = daysOf(30, new ItemMetric("Insurance", dailyFromYearly));
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		// Budget: $1,000 / MONTH for the same category.
		Budget budget = budget("b-ins", "Insurance", new BigDecimal("1000"), Currency.USD,
				BudgetPeriod.MONTH, 0.80);

		// When
		List<BudgetEvaluation> result = evaluationService.evaluate("vijay", Collections.singletonList(budget));

		// Then: sum ≈ $12,000 / 365.2425 * 30 ≈ $985.55 — which is roughly
		// 1/12 of the annual figure, matching what a MONTH budget expects.
		BudgetEvaluation eval = result.get(0);
		BigDecimal expectedActual = dailyFromYearly.multiply(new BigDecimal("30"))
				.setScale(2, RoundingMode.HALF_UP);

		assertEquals(0, expectedActual.compareTo(eval.getActualAmount()));
		// percent ≈ 0.9856 => above the 0.80 threshold but below 1.0 => WARNING.
		assertTrue("expected WARNING, got " + eval.getStatus() + " at " + eval.getPercentUsed(),
				eval.getPercentUsed() >= 0.80 && eval.getPercentUsed() < 1.0);
		assertEquals(BudgetStatus.WARNING, eval.getStatus());
	}

	// ---------------------------------------------------------------------
	// _TOTAL aggregation
	// ---------------------------------------------------------------------

	@Test
	public void shouldAggregateAllCategoriesForTotalCategory() {
		// Given: three distinct expense categories on each day of the period.
		// Per-day cost: 10 + 5 + 2 = $17/day.
		ItemMetric rent = expense("Rent", "10");
		ItemMetric food = expense("Food", "5");
		ItemMetric transport = expense("Transport", "2");

		// 10 days of history => total spend = 10 * 17 = $170 USD.
		List<DataPoint> points = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			points.add(dataPoint(ImmutableSet.of(rent, food, transport)));
		}
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		Budget totalBudget = budget("b-total", Budget.TOTAL_CATEGORY, new BigDecimal("500"),
				Currency.USD, BudgetPeriod.MONTH, 0.80);

		// When
		List<BudgetEvaluation> result = evaluationService.evaluate("vijay",
				Collections.singletonList(totalBudget));

		// Then: _TOTAL sums every expense across every day.
		BudgetEvaluation eval = result.get(0);
		assertEquals(0, new BigDecimal("170.00").compareTo(eval.getActualAmount()));
		// 170 / 500 = 0.34 => well under 0.80 => UNDER_BUDGET.
		assertEquals(0.34, eval.getPercentUsed(), 0.0001);
		assertEquals(BudgetStatus.UNDER_BUDGET, eval.getStatus());
	}

	@Test
	public void shouldIgnoreNonMatchingCategoriesWhenBudgetIsCategorySpecific() {
		// Sanity check that category filtering actually filters: a
		// category-specific budget must NOT pick up unrelated expenses.
		List<DataPoint> points = daysOf(3,
				expense("Rent", "100"),
				expense("Food", "50"));
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		Budget rentBudget = budget("b-rent", "Rent", new BigDecimal("500"), Currency.USD,
				BudgetPeriod.MONTH, 0.80);

		BudgetEvaluation eval = evaluationService.evaluate("vijay",
				Collections.singletonList(rentBudget)).get(0);

		// Only "Rent" counted: 3 * 100 = 300, not 3 * 150.
		assertEquals(0, new BigDecimal("300.00").compareTo(eval.getActualAmount()));
	}

	// ---------------------------------------------------------------------
	// Status thresholds
	// ---------------------------------------------------------------------

	@Test
	public void shouldMarkBudgetExceededAtOrAboveHundredPercent() {
		List<DataPoint> points = daysOf(6, expense("Rent", "200")); // $1200
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b-over", "Rent", new BigDecimal("1000"), Currency.USD,
				BudgetPeriod.MONTH, 0.80);

		BudgetEvaluation eval = evaluationService.evaluate("vijay",
				Collections.singletonList(budget)).get(0);

		assertEquals(BudgetStatus.EXCEEDED, eval.getStatus());
		assertTrue(eval.getPercentUsed() >= 1.0);
	}

	@Test
	public void shouldStayUnderBudgetBelowWarningThreshold() {
		List<DataPoint> points = daysOf(3, expense("Rent", "100")); // $300
		when(repository.findByIdAccountAndIdDateGreaterThanEqual(eq("vijay"), any(Date.class)))
				.thenReturn(points);

		Budget budget = budget("b-under", "Rent", new BigDecimal("1000"), Currency.USD,
				BudgetPeriod.MONTH, 0.80);

		BudgetEvaluation eval = evaluationService.evaluate("vijay",
				Collections.singletonList(budget)).get(0);

		assertEquals(BudgetStatus.UNDER_BUDGET, eval.getStatus());
		assertEquals(0.30, eval.getPercentUsed(), 0.0001);
	}

	// ---------------------------------------------------------------------
	// Test helpers
	// ---------------------------------------------------------------------

	private static Budget budget(String id, String category, BigDecimal limit, Currency currency,
								 BudgetPeriod period, double warningThreshold) {
		Budget b = new Budget();
		b.setId(id);
		b.setCategory(category);
		b.setLimit(limit);
		b.setCurrency(currency);
		b.setPeriod(period);
		b.setWarningThreshold(warningThreshold);
		return b;
	}

	private static ItemMetric expense(String title, String perDayUsd) {
		return new ItemMetric(title, new BigDecimal(perDayUsd));
	}

	private static DataPoint dataPoint(Set<ItemMetric> expenses) {
		DataPoint dp = new DataPoint();
		dp.setId(new DataPointId("vijay", new Date()));
		dp.setExpenses(expenses);
		return dp;
	}

	private static List<DataPoint> daysOf(int days, ItemMetric... expenses) {
		List<DataPoint> out = new ArrayList<>(days);
		Set<ItemMetric> perDay = new java.util.HashSet<>(Arrays.asList(expenses));
		for (int i = 0; i < days; i++) {
			out.add(dataPoint(perDay));
		}
		return out;
	}
}

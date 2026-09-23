package org.l2jmobius.tests.phantoms;

/** One focused production-knowledge and historical-planner generated route proof. */
public final class PhantomGeneratedHistoricalRouteSuite extends PhantomHistoricalBackgroundGoal033ASuite
{
	@Override
	public String id()
	{
		return "generated-historical-route";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-generated-farming-route", this::testGeneratedRoute);
	}
}

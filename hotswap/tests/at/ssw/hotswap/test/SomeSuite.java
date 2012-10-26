package at.ssw.hotswap.test;

import at.ssw.hotswap.test.eval.EvalTestSuite;
import at.ssw.hotswap.test.methods.DeleteActiveMethodTest;
import at.ssw.hotswap.test.methods.MethodsTestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Summarizes all available test suites.
 *
 * @author Thomas Wuerthinger
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        EvalTestSuite.class,
        DeleteActiveMethodTest.class
})
public class SomeSuite {
}

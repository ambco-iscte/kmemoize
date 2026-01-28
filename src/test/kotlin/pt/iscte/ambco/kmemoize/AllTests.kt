package pt.iscte.ambco.kmemoize

import com.tschuchort.compiletesting.SourceFile
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import pt.iscte.ambco.kmemoize.test.TestAnonymousFunctions
import pt.iscte.ambco.kmemoize.test.TestCheckPureFunctionVisitor
import pt.iscte.ambco.kmemoize.test.TestClassFunctions
import pt.iscte.ambco.kmemoize.test.TestExpectedFailures
import pt.iscte.ambco.kmemoize.test.TestLocalFunctions
import pt.iscte.ambco.kmemoize.test.TestTopLevelFunctions
import java.io.File

@Suite
@SelectClasses(
    TestExpectedFailures::class,
    TestClassFunctions::class,
    TestTopLevelFunctions::class,
    TestLocalFunctions::class,
    TestAnonymousFunctions::class,

    TestCheckPureFunctionVisitor::class
)
class AllTests
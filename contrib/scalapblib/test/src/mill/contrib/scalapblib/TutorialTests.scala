package mill.contrib.scalapblib

import mill._
import mill.api.{PathRef, Result}
import mill.util.{TestEvaluator, TestUtil}
import utest.framework.TestPath
import utest.{TestSuite, Tests, assert, _}

object TutorialTests extends TestSuite {
  val testScalaPbVersion = "0.11.7"

  trait TutorialBase extends TestUtil.BaseModule {
    override def millSourcePath: os.Path =
      TestUtil.getSrcPathBase() / millOuterCtx.enclosing.split('.')
  }

  trait TutorialModule extends ScalaPBModule {
    def scalaVersion = sys.props.getOrElse("TEST_SCALA_2_12_VERSION", ???)
    def scalaPBVersion = testScalaPbVersion
    def scalaPBFlatPackage = true
    def scalaPBIncludePath = Seq(scalaPBUnpackProto())
  }

  object Tutorial extends TutorialBase {

    object core extends TutorialModule {
      override def scalaPBVersion = testScalaPbVersion
    }
  }

  object TutorialWithProtoc extends TutorialBase {
    object core extends TutorialModule {
      override def scalaPBProtocPath = Some("/dev/null")
    }
  }

  object TutorialWithAdditionalArgs extends TutorialBase {
    object core extends TutorialModule {
      override def scalaPBAdditionalArgs = T {
        Seq(
          "--additional-test=..."
        )
      }
    }
  }

  object TutorialWithSpecificSources extends TutorialBase {
    object core extends TutorialModule {
      override def scalaPBSources: T[Seq[PathRef]] = T.sources {
        millSourcePath / "protobuf" / "tutorial" / "Tutorial.proto"
      }

      override def scalaPBSearchDeps = true
      override def scalaPBIncludePath = Seq(
        PathRef(millSourcePath / "protobuf" / "tutorial")
      )
    }
  }

  val resourcePath: os.Path = os.pwd / "contrib" / "scalapblib" / "test" / "protobuf" / "tutorial"

  def protobufOutPath(eval: TestEvaluator): os.Path =
    eval.outPath / "core" / "compileScalaPB.dest" / "com" / "example" / "tutorial"

  def workspaceTest[T](m: TestUtil.BaseModule)(t: TestEvaluator => T)(implicit tp: TestPath): T = {
    val eval = new TestEvaluator(m)
    os.remove.all(m.millSourcePath)
    println(m.millSourcePath)
    os.remove.all(eval.outPath)
    println(eval.outPath)
    os.makeDir.all(m.millSourcePath / "core" / "protobuf")
    os.copy(resourcePath, m.millSourcePath / "core" / "protobuf" / "tutorial")
    t(eval)
  }

  def compiledSourcefiles: Seq[os.RelPath] = Seq[os.RelPath](
    os.rel / "AddressBook.scala",
    os.rel / "Person.scala",
    os.rel / "TutorialProto.scala",
    os.rel / "Include.scala",
    os.rel / "IncludeProto.scala"
  )

  def tests: Tests = Tests {
    "scalapbVersion" - {

      "fromBuild" - workspaceTest(Tutorial) { eval =>
        val Right((result, evalCount)) = eval.apply(Tutorial.core.scalaPBVersion)

        assert(
          result == testScalaPbVersion,
          evalCount > 0
        )
      }
    }

    "compileScalaPB" - {
      "calledDirectly" - workspaceTest(Tutorial) { eval =>
        val Right((result, evalCount)) = eval.apply(Tutorial.core.compileScalaPB)

        val outPath = protobufOutPath(eval)

        val outputFiles = os.walk(result.path).filter(os.isFile)

        val expectedSourcefiles = compiledSourcefiles.map(outPath / _)

        assert(
          result.path == eval.outPath / "core" / "compileScalaPB.dest",
          outputFiles.nonEmpty,
          outputFiles.forall(expectedSourcefiles.contains),
          outputFiles.size == 5,
          evalCount > 0
        )

        // don't recompile if nothing changed
        val Right((_, unchangedEvalCount)) = eval.apply(Tutorial.core.compileScalaPB)

        assert(unchangedEvalCount == 0)
      }

      "calledWithSpecificFile" - workspaceTest(TutorialWithSpecificSources) { eval =>
        val Right((result, evalCount)) = eval.apply(TutorialWithSpecificSources.core.compileScalaPB)

        val outPath = protobufOutPath(eval)

        val outputFiles = os.walk(result.path).filter(os.isFile)

        val expectedSourcefiles = Seq[os.RelPath](
          os.rel / "AddressBook.scala",
          os.rel / "Person.scala",
          os.rel / "TutorialProto.scala",
          os.rel / "IncludeProto.scala"
        ).map(outPath / _)

        assert(
          result.path == eval.outPath / "core" / "compileScalaPB.dest",
          outputFiles.nonEmpty,
          outputFiles.forall(expectedSourcefiles.contains),
          outputFiles.size == 3,
          evalCount > 0
        )

        // don't recompile if nothing changed
        val Right((_, unchangedEvalCount)) = eval.apply(Tutorial.core.compileScalaPB)

        assert(unchangedEvalCount == 0)
      }

//      // This throws a NullPointerException in coursier somewhere
//      //
//      // "triggeredByScalaCompile" - workspaceTest(Tutorial) { eval =>
//      //   val Right((_, evalCount)) = eval.apply(Tutorial.core.compile)
//
//      //   val outPath = protobufOutPath(eval)
//
//      //   val outputFiles = os.walk(outPath).filter(_.isFile)
//
//      //   val expectedSourcefiles = compiledSourcefiles.map(outPath / _)
//
//      //   assert(
//      //     outputFiles.nonEmpty,
//      //     outputFiles.forall(expectedSourcefiles.contains),
//      //     outputFiles.size == 3,
//      //     evalCount > 0
//      //   )
//
//      //   // don't recompile if nothing changed
//      //   val Right((_, unchangedEvalCount)) = eval.apply(Tutorial.core.compile)
//
//      //   assert(unchangedEvalCount == 0)
//      // }
//     }
    }

    "useExternalProtocCompiler" - {
      /* This ensure that the `scalaPBProtocPath` is properly used.
       * As the given path is incorrect, the compilation should fail.
       */
      "calledWithWrongProtocFile" - workspaceTest(TutorialWithProtoc) { eval =>
        val result = eval.apply(TutorialWithProtoc.core.compileScalaPB)
        assert(result.isLeft)
      }
    }

    "compilationArgs" - {
      "calledWithAdditionalArgs" - workspaceTest(TutorialWithAdditionalArgs) {
        eval =>
          val result =
            eval.apply(TutorialWithAdditionalArgs.core.scalaPBCompileOptions)
          result match {
            case Right((args, _)) =>
              assert(args.exists(_.contains("--additional-test=...")))
            case _ => assert(false)
          }
      }
    }
  }
}

package mill.eval

import scala.collection.mutable

// Adapted from
// https://github.com/indy256/codelibrary/blob/c52247216258e84aac442a23273b7d8306ef757b/java/src/SCCTarjan.java
private object Tarjans {
  def apply(graph0: IterableOnce[IterableOnce[Int]]): Seq[Seq[Int]] = {
    val graph = graph0.iterator.map(_.iterator.toArray).toArray
    val n = graph.length
    val visited = new Array[Boolean](n)
    val stack = mutable.ArrayBuffer.empty[Integer]
    var time = 0
    val lowlink = new Array[Int](n)
    val components = mutable.ArrayBuffer.empty[Seq[Int]]

    for (u <- 0 until n) {
      if (!visited(u)) dfs(u)
    }

    def dfs(u: Int): Unit = {
      lowlink(u) = time
      time += 1
      visited(u) = true
      stack.append(u)
      var isComponentRoot = true
      for (v <- graph(u)) {
        if (!visited(v)) dfs(v)
        if (lowlink(u) > lowlink(v)) {
          lowlink(u) = lowlink(v)
          isComponentRoot = false
        }
      }
      if (isComponentRoot) {
        val component = mutable.Buffer.empty[Int]

        var done = false
        while (!done) {
          val x = stack.last
          stack.remove(stack.length - 1)
          component.append(x)
          lowlink(x) = Integer.MAX_VALUE
          if (x == u) done = true
        }
        components.append(component.toSeq)
      }
    }
    components.toSeq
  }
}

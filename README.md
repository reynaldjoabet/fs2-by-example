# fs2-by-example

## What is a `Pull`?
A `Pull` is a free monad-like structure representing a program that can pull output values of type `O` while computing a terminal result of type `R` using an effect of type `F` (`Pull[F, O, R]`). Internally, it is one of:

- **Terminal**: End states (Succeeded, Fail, Interrupted). The `R` type represents the final result available after all pulling finishes.
- **Bind(step, cont)**: Sequencing — binds a pull step with a continuation function `cont`.
- **Action**: A single instruction to be interpreted by the compiler:
  - `Output(chunk)`: Emits a non-empty `Chunk[O]` downstream.
  - `Eval(value)`: Lifts `F[R]` into the pull (effectful computation).
  - `Uncons(stream)`: Steps through a stream to get the head and tail.
  - `Acquire(resource, release)`: Acquires a resource and registers cleanup.

### `uncons`
`uncons` is the fundamental operation for consuming a stream in a pull. It is how you "pull" the next chunk of data from a stream source.
- `Some((chunk, tail))`: A non-empty chunk was found, along with the remaining stream.
- `None`: The stream is finished; no more data.

*(Note: This is inspired by functional programming concepts like Haskell's `uncons`, which decomposes a list into its head and tail, returning `Nothing` if empty, or `Just(head, tail)` if non-empty).*

## Streams & The Pull Model
The FS2 stream model is a "pull" model, meaning that downstream functions call upstream functions to obtain data only when needed. The source loads data if and only if data is requested further down in the processing pipeline. 

```scala
final class Stream[+F[_], +O] private[fs2] (private[fs2] val underlying: Pull[F, O, Unit]) {
  // Appends two streams
  def ++[F2[x] >: F[x], O2 >: O](s2: => Stream[F2, O2]): Stream[F2, O2] =
    (underlying >> s2.underlying).streamNoScope

  // Recovers from errors
  def attempt: Stream[F, Either[Throwable, O]] =
    map(Right(_): Either[Throwable, O]).handleErrorWith(e => Stream.emit(Left(e)))

  // Evaluates effectful operations
  def evalMap[F2[x] >: F[x], O2](f: O => F2[O2]): Stream[F2, O2] = {
    def evalOut(o: O) = Pull.eval(f(o)).flatMap(Pull.output1)
    underlying.flatMapOutput(evalOut).streamNoScope
  }
}
```

## Chunks & Execution Model
Batching is the process of grouping elements and emitting them downstream for processing. In FS2, these groups are called **Chunks**. A Chunk is a finite sequence of values used internally for efficiency.

Each chunk is emitted **atomically** — errors or interruptions can only happen *between* chunks, not within a chunk.

```scala
// If mapping element "3" throws, the ENTIRE chunk is discarded.
Stream.chunk(Chunk(1, 2, 3)).map(x => if (x == 3) throw new Exception else x)
```

During compilation (`Pull.scala`), when the interpreter encounters an `Output(chunk)` action, it evaluates the `interruptGuard`. If an interruption has occurred, the whole chunk is skipped. The emission of each chunk downstream is atomic.

### Operations and Chunks
- `.map(_ * 10)`: Preserves chunk sizes exactly.
- `.filter(_ % 2 == 0)`: Chunks can shrink or become empty.
- `.take(3)`: Splits a chunk if needed at the boundary.
- `.compile.toList`: Collects all chunks into a final List, ignoring boundary sizes.

### Performance Implications
Chunk sizes dictate the performance profile:
- **Smaller chunks**: More overhead per element (more interpreter hops, more interruption checks).
- **Larger chunks**: Higher throughput but more latency before the first element appears. Ideal for CPU-bound transforms (`.map`).
For I/O-bound work, chunk size is typically dictated by the external system (e.g., 4KB vs 64KB file reads).

## Recursion, Left-Nested Binds, & Trampolining
When chaining `flatMap` calls (`pull1.flatMap(f).flatMap(g)`), developers naturally build left-leaning trees (`Bind(Bind(pull1, f), g)`). Evaluating this naively on the JVM (which has a fixed-size stack of ~1MB) would lead to deep recursion and a `StackOverflowError`.

### Reassociation & `viewL`
To solve the Stack Overflow issue, FS2 uses a function called `viewL`. Its job is to dynamically "peel off" the leftmost action and accumulate the continuations, effectively transforming the left-leaning tree into a right-leaning one (`pull1 → f → g`) at runtime.

### Trampolining
FS2 (and Cats Effect) operates over a "trampoline". Instead of executing functions via standard JVM method calls (which adds stack frames), each step returns the *next* instruction as a data structure to a central loop (`OuterRun`). 
- A step returns "run B next"
- The loop runs B, which returns "run C next"

You trade call-stack recursion for a heap-allocated loop. This makes mutually recursive or deeply nested stream interactions perfectly stack-safe indefinitely.

## Other Stream Operations
- **Unordered operations** (`parEvalMapUnordered`): We don't care about the order of results from the evalMap action. It allows for higher throughput because it never waits to start a new job. It uses N job permits, and the moment one job finishes, it requests the next element.

## Contramap
The opposite of `map`. While `map` operates on the "output" or "result" of a computation, `contramap` works on the "input" or "parameter" of a computation.

```scala
trait Printable[A] {
  def format(value: A): String
  def contramap[B](f: B => A): Printable[B] =
    (value: B) => format(f(value))
}
```

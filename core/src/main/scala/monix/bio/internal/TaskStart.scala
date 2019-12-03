/*
 * Copyright (c) 2019-2019 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.bio.internal

import monix.bio.{Fiber, UIO, WRYYY}
import monix.bio.WRYYY.{Async, Context}
import monix.execution.Callback

import scala.concurrent.Promise

private[bio] object TaskStart {

  /**
    * Implementation for `Task.fork`.
    */
  def forked[E, A](fa: WRYYY[E, A]): UIO[Fiber[E, A]] =
    fa match {
      // There's no point in evaluating strict stuff
      case WRYYY.Now(_) | WRYYY.Error(_) =>
        WRYYY.Now(Fiber(fa, WRYYY.unit))
      case _ =>
        Async[Nothing, Fiber[E, A]](
          new StartForked(fa),
          trampolineBefore = false,
          trampolineAfter = true,
          restoreLocals = false
        )
    }

  private class StartForked[E, A](fa: WRYYY[E, A])
      extends ((Context[Nothing], Callback[Nothing, Fiber[E, A]]) => Unit) {

    final def apply(ctx: Context[Nothing], cb: Callback[Nothing, Fiber[E, A]]): Unit = {
      implicit val sc = ctx.scheduler
      // Cancelable Promise gets used for storing or waiting
      // for the final result
      val p = Promise[Either[E, A]]()
      // Building the Task to signal, linked to the above Promise.
      // It needs its own context, its own cancelable
      val ctx2 = WRYYY.Context[E](ctx.scheduler, ctx.options)
      // Starting actual execution of our newly created task;
      WRYYY.unsafeStartEnsureAsync(fa, ctx2, Callback.fromAttempt(p.success))
      // Signal the created fiber
      cb.onSuccess(Fiber.fromPromise(p, ctx2.connection))
    }
  }
}

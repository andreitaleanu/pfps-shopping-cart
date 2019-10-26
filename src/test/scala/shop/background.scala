package shop

import cats.effect._
import cats.effect.concurrent.Ref
import shop.effects.Background
import scala.concurrent.duration.FiniteDuration

object background {

  val NoOp: Background[IO] = new Background[IO] {
    def schedule[A](duration: FiniteDuration, fa: IO[A]): IO[Unit] = IO.unit
  }

  def counter(ref: Ref[IO, Int]): Background[IO] =
    new Background[IO] {
      def schedule[A](duration: FiniteDuration, fa: IO[A]): IO[Unit] =
        ref.update(_ + 1)
    }

}

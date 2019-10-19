package shop.algebras

import cats.Monad
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import io.estatico.newtype.ops._
import shop.domain.auth._
import shop.domain.brand._
import shop.domain.category._
import shop.domain.cart._
import shop.domain.item._
import LiveShoppingCart._
import java.{ util => ju }

trait ShoppingCart[F[_]] {
  def add(userId: UserId, item: ItemId, quantity: Quantity): F[Unit]
  def get(userId: UserId): F[List[CartItem]]
  def delete(userId: UserId): F[Unit]
  def removeItem(userId: UserId, itemId: ItemId): F[Unit]
  def update(userId: UserId, cart: Cart): F[Unit]
}

object LiveShoppingCart {
  type ItemsInCart = Map[ItemId, CartItem]
  type Carts[F[_]] = Map[UserId, Ref[F, ItemsInCart]]

  def make[F[_]: Sync]: F[ShoppingCart[F]] =
    Ref.of[F, Carts[F]](Map.empty).map(new LiveShoppingCart(_))
}

class LiveShoppingCart[F[_]: Sync] private (
    ref: Ref[F, Carts[F]]
) extends ShoppingCart[F] {

  private val unit = ().pure[F]

  private def createNewCart(userId: UserId): F[Ref[F, ItemsInCart]] =
    Ref.of[F, ItemsInCart](Map.empty).flatMap { newCart =>
      ref.update(_.updated(userId, newCart)).as(newCart)
    }

  private def getOrCreateCart(userId: UserId): F[Ref[F, ItemsInCart]] =
    ref.get.flatMap(_.get(userId).fold(createNewCart(userId))(_.pure[F]))

  def add(userId: UserId, itemId: ItemId, quantity: Quantity): F[Unit] =
    getOrCreateCart(userId).flatMap { cart =>
      // TODO: Hard-coded for now. Should retrieve from items table in PSQL.
      val brand = Brand(ju.UUID.randomUUID().coerce[BrandId], BrandName("some"))
      val cat   = Category(ju.UUID.randomUUID().coerce[CategoryId], CategoryName("foo"))
      val item  = Item(itemId, ItemName("foo"), ItemDescription("bar"), USD(100), brand, cat)
      cart.update(_.updated(item.uuid, CartItem(item, quantity)))
    }

  def get(userId: UserId): F[List[CartItem]] =
    ref.get.flatMap { carts =>
      carts.get(userId).fold(List.empty[CartItem].pure[F]) { cart =>
        cart.get.map(_.values.toList)
      }
    }

  def delete(userId: UserId): F[Unit] =
    ref.get.flatMap { carts =>
      carts.get(userId).fold(unit) { cart =>
        cart.update(_ => Map.empty)
      }
    }

  def removeItem(userId: UserId, itemId: ItemId): F[Unit] =
    ref.get.flatMap { carts =>
      carts.get(userId).fold(unit) { cart =>
        cart.update(_.removed(itemId))
      }
    }

  def update(userId: UserId, cart: Cart): F[Unit] =
    ref.get.flatMap { carts =>
      carts.get(userId).fold(unit) { st =>
        cart.items
          .map {
            case (id, q) =>
              st.update(_.updatedWith(id)(_.map(_.copy(quantity = q))))
          }
          .toList
          .sequence_
      }
    }
}

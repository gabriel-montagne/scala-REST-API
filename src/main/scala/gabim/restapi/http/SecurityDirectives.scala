package gabim.restapi.http

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.{BasicDirectives, FutureDirectives, HeaderDirectives, RouteDirectives}
import gabim.restapi.models.{UserEntity, UserResponseEntity}
import gabim.restapi.services.AuthService

trait SecurityDirectives {

  import BasicDirectives._
  import FutureDirectives._
  import HeaderDirectives._
  import RouteDirectives._

  def authenticate: Directive1[UserEntity] = {
    headerValueByName("Authorization").flatMap { key =>
      onSuccess(authService.getAuthenticatedUser(key.replace("Bearer ", ""))).flatMap {
        case Some(user) => {
          provide(user)
        }
        case None       => reject
      }
    }
  }

  protected val authService: AuthService

}

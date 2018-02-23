package gabim.restapi.services

import gabim.restapi.models._
import gabim.restapi.models.db.{TokenEntityTable, UserEntityTable, UserOAuthEntityTable, UsersProfileEntityTable}
import gabim.restapi.utilities.DatabaseService

import scala.concurrent.{Await, ExecutionContext, Future}
import com.github.t3hnar.bcrypt._
import io.swagger.annotations.{Api, ApiOperation}
import javax.ws.rs.Path
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt


@Path("/users")
@Api(value = "/users", produces = "application/json")
class UsersService(val databaseService: DatabaseService)(implicit executionContext: ExecutionContext) extends UserEntityTable with UsersProfileEntityTable with UserOAuthEntityTable with TokenEntityTable {

  import databaseService._
  import databaseService.driver.api._

  @ApiOperation(value = "Get list of all users", nickname = "getAllUsers", httpMethod = "GET",
    response = classOf[UserResponseEntity], responseContainer = "Set")
  def getUsers(): Future[Seq[UserResponseEntity]] = {
    val q = for {
      (user, profile) <- users joinLeft usersProfiles on (_.id === _.userId)
    } yield (user, profile)
    db.run(q
      .result
      .map( result => {
        result.map( rec =>
          UserResponseEntity(rec._1.id.get, rec._1.username, rec._1.role.get, None, rec._2))
      }))
  }
  def getUserById(id: Long): Future[Option[UserEntity]] = db.run(users.filter(_.id === id).result.headOption)

  def getUserByLogin(login: String): Future[Option[UserEntity]] = db.run(users.filter(_.username === login).result.headOption)

  def getUserByOAuth(userId: String, oauthType: String): Future[Option[UserEntity]] = {
    val q = for {
      userO <- usersOauth filter (_.oauthId === userId) filter (_.oauthType === oauthType)
      user <- users if user.id === userO.userId
    } yield (user)
    db.run(q.result.headOption)
  }

  def getUserProfileByToken(token: String): Future[Option[UserResponseEntity]] = {
    val q = for {
      tk <- tokens if tk.token === token
      (user, profile) <- users joinLeft usersProfiles on (_.id === _.userId) if user.id === tk.userId
    } yield (user, profile)
    db.run(q.result.headOption)
      .map {
        case Some((user, profile)) =>
          Option(UserResponseEntity(user.id.get, user.username, user.role.get, Option(token), profile))
        case None =>
          Option(UserResponseEntity(0, "anonymus", "", Option(""), None))
      }
  }

  def createUserProfile(userProfile: UserProfileEntity): Future[UserProfileEntity] = db.run(usersProfiles returning usersProfiles += userProfile)

  def isAvailable(username: String): Future[String] = db.run(users.filter(_.username === username).result.headOption).map {
    case Some(user) => "false"
    case None => "true"
  }

  def createUser(user: UserEntity): Future[UserEntity] = {
    val hashPass = BCrypt.hashpw(user.password.get, generateSalt)
    val dbUser: UserEntity = UserEntity(None, user.username, Option(hashPass), user.role.orElse(Option("user")), user.last_login,
      user.attempts.orElse(Option(0)), user.lockoutdate, user.twofactor.orElse(Option(false)),
      user.email, user.emailconfirmed.orElse(Option(false)), user.phone, user.phoneconfirmed.orElse(Option(false)),
      user.active.orElse(Option(true)), user.created.orElse(Option(new DateTime())), user.rev.orElse(Option(0)))
    db.run(users returning users += dbUser)
        .map( user => {
          val newUserProfile: UserProfileEntity = UserProfileEntity(user.id.get, Option(user.username), None, None)
          createUserProfile(newUserProfile)
          user
        })
  }

  def updateUser(id: Long, userUpdate: UserEntityUpdate): Future[Option[UserViewEntity]] = getUserById(id).flatMap {
    case Some(user) => {
      val updatedUser = userUpdate.merge(user)
      db.run(users
              .filter(_.id === id)
              .update(updatedUser))
        .map(_ =>
          Some(UserViewEntity(updatedUser.id, updatedUser.username, updatedUser.role, updatedUser.email, updatedUser.phone, updatedUser.active)))
    }
    case None => Future.successful(None)
  }

  def deleteUser(id: Long): Future[Int] = db.run(users.filter(_.id === id).delete)

  def canUpdateUsers(user: UserResponseEntity) = user.role == "admin"

  def canViewUsers(user: UserResponseEntity) = Seq("admin", "manager").contains(user.role)
}
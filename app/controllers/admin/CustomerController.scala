package controllers.admin

import scala.concurrent.Future
import scala.concurrent.Future.{successful => future}

import controllers.auth.AuthConfigAdminImpl
import forms._
import javax.inject.Inject
import jp.t2v.lab.play2.auth.AuthActionBuilders
import models.TablesExtend._
import play.api._
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.cache._
import play.filters.csrf._
import play.filters.csrf.CSRF.Token
import services.UserAccountServiceLike
import services.dao._
import skinny.util.JSONStringOps._
import views._
import utilities.auth.Role
import utilities.auth.Role._
import utilities.config._

class CustomerController @Inject()(addToken: CSRFAddToken, checkToken: CSRFCheck, cache: CacheApi, cached: Cached, val userAccountService: UserAccountServiceLike, customerDao: CustomerDAO, CustomerForm:CustomerForm, val messagesApi: MessagesApi) extends Controller with AuthActionBuilders with AuthConfigAdminImpl with I18nSupport  {
  
  val UserAccountSv = userAccountService
  
  
  /** This result directly redirect to the application home.*/
  val Home = Redirect(controllers.admin.routes.CustomerController.index())

  def getToken = addToken(Action { implicit request =>
    val Token(name, value) = CSRF.getToken.get
    Ok(s"$name=$value")
  })
  
  def index = addToken{
    AuthorizationAction(NormalUser).async { 
      customerDao.all().map(customers => Ok(views.html.customer.list("", customers)))
    }
  }
  def add = addToken{
    AuthorizationAction(NormalUser) {implicit request =>
      Ok(views.html.customer.regist("", CustomerForm.form))
    }
  }
  
  def create = checkToken{
    AuthorizationAction(NormalUser).async { implicit request =>
      CustomerForm.form.bindFromRequest.fold(
          formWithErrors => {
            Logger.debug(formWithErrors.toString())
            Future(BadRequest(views.html.customer.regist("エラー", formWithErrors)))
          },
          customer => {
            customerDao.create(customer).flatMap(cnt =>
                //if (cnt != 0) customerDao.all().map(customers => Ok(views.html.customer.list("登録しました", customers)))
                if (cnt != 0) Future.successful(Redirect(controllers.admin.routes.CustomerController.index))
                else customerDao.all().map(notifications => BadRequest(views.html.customer.edit("エラー", CustomerForm.form.fill(customer))))
             )
          }
      )
    }
  }
   
  def edit(customerId: Long) = addToken{
    AuthorizationAction(NormalUser).async {implicit request =>
      customerDao.findById(customerId).flatMap(option =>
        option match {
          case Some(customer) => Future(Ok(views.html.customer.edit("GET", CustomerForm.form.fill(customer))))
          case None => customerDao.all().map(customers => BadRequest(views.html.customer.list("エラー", customers)))
        }
      )
    }
  }

  def update = checkToken {
    AuthorizationAction(NormalUser).async { implicit request =>
      CustomerForm.form.bindFromRequest.fold(
          formWithErrors => {
            Future(BadRequest(views.html.customer.edit("ERROR", formWithErrors)))
          },
          customer => {
            customerDao.update_mappinged(customer).flatMap(cnt =>
              if (cnt != 0) customerDao.all().map(customers => Ok(views.html.customer.list("更新しました", customers)))
              //if (cnt != 0) Future.successful(Redirect(routes.CustomerController.index))
              else customerDao.all().map(notifications => BadRequest(views.html.customer.edit("エラー", CustomerForm.form.fill(customer))))
            )
          }
      )
    }
  }
  
  def delete(id: Long) = checkToken{
    AuthorizationAction(NormalUser).async {
      cache.remove(FormConfig.FormCacheKey)
      customerDao.delete(id).flatMap(cnt =>
        if (cnt != 0) customerDao.all().map(customers => Ok(views.html.customer.list("削除しました", customers)))
        else customerDao.all().map(customers => BadRequest(views.html.customer.list("エラー", customers)))
      )
    }
  }
  
  def editadvance(customerId: Long) = addToken{
    AuthorizationAction(NormalUser).async {implicit request =>
      customerDao.findById(customerId).flatMap(option =>
        option match {
          case Some(customer) => Future(Ok(views.html.customer.editadvance("GET", CustomerForm.form.fill(customer))))
          case None => customerDao.all().map(customers => BadRequest(views.html.customer.list("エラー", customers)))
        }
      )
    }
  }
  def editadvanceback = addToken{
    AuthorizationAction(NormalUser).async {implicit request =>
      val formdata: Option[CustomerRow] = cache.get[CustomerRow](FormConfig.FormCacheKey)
      cache.get[CustomerRow](FormConfig.FormCacheKey) match {
          case Some(customer) => Future(Ok(views.html.customer.editadvance("GET", CustomerForm.form.fill(customer))))
          case None => customerDao.all().map(customers => BadRequest(views.html.customer.list("エラー", customers)))
      }
    }
  }
  def editadvanceconfirm = addToken{
    AuthorizationAction(NormalUser).async {implicit request =>
      CustomerForm.form.bindFromRequest.fold(
          formWithErrors => {
            Future(BadRequest(views.html.customer.editadvance("ERROR", formWithErrors)))
          },
          customer => {
            cache.set(FormConfig.FormCacheKey, customer, FormConfig.FormCacheTime)
            Future((Ok(views.html.customer.editadvanceconfirm("", cache))))
          }
      )
    }
  }
  def updateadvance = checkToken {
    AuthorizationAction(NormalUser).async { implicit request =>
      val postdata: Option[CustomerRow] = cache.get[CustomerRow](FormConfig.FormCacheKey)
      cache.remove(FormConfig.FormCacheKey)
      postdata match {
          case Some(customer) => 
             customerDao.update_mappinged(customer).flatMap(cnt =>
              if (cnt != 0) Future.successful(Redirect(controllers.admin.routes.CustomerController.editadvancesuccess))
              //if (cnt != 0) Future.successful(Redirect(routes.CustomerController.index))
              else customerDao.all().map(notifications => BadRequest(views.html.customer.editadvance("エラー", CustomerForm.form.fill(customer))))
            )
          case None => Future.successful(Status(500)(views.html.errors.error500internalerror("no formdata")))
      }
    }
  }
  def editadvancesuccess = AuthorizationAction(NormalUser).async {
        Future(Ok(views.html.customer.editadvancesuccess("更新しました")))
  }
}
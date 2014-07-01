package org.http4s
package rho
package swagger

/** See also https://github.com/scalatra/scalatra/blob/2.3.x_2.10/swagger/src/main/scala/org/scalatra/swagger/SwaggerBase.scala
  * for the rendering side of this
  *
  * -- Api specification
  *   https://github.com/wordnik/swagger-spec/blob/master/versions/1.2.md
  */

import bits.PathAST._
import bits.HeaderAST._

import java.util.{Date => JDate}
import org.json4s._
import org.joda.time._
import format.ISODateTimeFormat
import collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import reflect._
import com.typesafe.scalalogging.slf4j.LazyLogging

import shapeless.HList
import java.lang.reflect.{Field, TypeVariable}
import org.http4s.rho.swagger.annotations.{ApiModelProperty, ApiModel}

trait SwaggerEngine[T <: SwaggerApi[_]] {
  def swaggerVersion: String
  def apiVersion: String
  def apiInfo: ApiInfo

  private[swagger] val _docs = new ConcurrentHashMap[String, T]().asScala

//  private[this] var _authorizations = List.empty[AuthorizationType]
//  def authorizations = _authorizations
//  def addAuthorization(auth: AuthorizationType) { _authorizations ::= auth }

  def docs = _docs.values

  /**
   * Returns the documentation for the given path.
   */
  def doc(path: String): Option[T] = _docs.get(path)

  /**
   * Registers the documentation for an API with the given path.
   */
//  def register(name: String,
//               resourcePath: String,
//               description: Option[String],
//               s: SwaggerSupportSyntax with SwaggerSupportBase,
//               consumes: List[String],
//               produces: List[String],
//               protocols: List[String],
//               authorizations: List[String])

  def register(action: RhoAction[_ <: HList,_,_])

}

object Swagger {

  val baseTypes = Set("byte", "boolean", "int", "long", "float", "double", "string", "date", "void", "Date", "DateTime", "DateMidnight", "Duration", "FiniteDuration", "Chronology")
  val excludes: Set[java.lang.reflect.Type] = Set(classOf[java.util.TimeZone] ,classOf[java.util.Date], classOf[DateTime], classOf[DateMidnight], classOf[ReadableInstant], classOf[Chronology], classOf[DateTimeZone])
  val containerTypes = Set("Array", "List", "Set")
  val SpecVersion = "1.2"
  val Iso8601Date = ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC)

//  def collectModels[T: Manifest](alreadyKnown: Set[Model]): Set[Model] = collectModels(Reflector.scalaTypeOf[T], alreadyKnown)
//
//  private[swagger] def collectModels(tpe: ScalaType, alreadyKnown: Set[Model], known: Set[ScalaType] = Set.empty): Set[Model] = {
//    if (tpe.isMap) collectModels(tpe.typeArgs.head, alreadyKnown, tpe.typeArgs.toSet) ++ collectModels(tpe.typeArgs.last, alreadyKnown, tpe.typeArgs.toSet)
//    else if (tpe.isCollection || tpe.isOption) {
//      val ntpe = tpe.typeArgs.head
//      if (! known.contains(ntpe)) collectModels(ntpe, alreadyKnown, known + ntpe)
//      else Set.empty
//    }
//    else {
//      if (alreadyKnown.map(_.id).contains(tpe.simpleName)) Set.empty
//      else {
//        val descr = Reflector.describe(tpe)
//        descr match {
//          case descriptor: ClassDescriptor =>
//            val ctorModels = descriptor.mostComprehensive.filterNot(_.isPrimitive).toVector
//            val propModels = descriptor.properties.filterNot(p => p.isPrimitive || ctorModels.exists(_.name == p.name))
//            val subModels = (ctorModels.map(_.argType) ++ propModels.map(_.returnType)).toSet -- known
//            val topLevel = for {
//              tl <- subModels + descriptor.erasure
//              if  !(tl.isCollection || tl.isMap || tl.isOption)
//              m <- modelToSwagger(tl)
//            } yield m
//
//            val nested = subModels.foldLeft((topLevel, known + descriptor.erasure)){ (acc, b) =>
//              val m = collectModels(b, alreadyKnown, acc._2)
//              (acc._1 ++ m, acc._2 + b)
//            }
//            nested._1
//          case _ => Set.empty
//        }
//      }
//    }
//  }
//
//  def modelToSwagger[T](implicit mf: Manifest[T]): Option[Model] = modelToSwagger(Reflector.scalaTypeOf[T])

  private def blankOpt(s: String) = if (s.length == 0) None else Some(s)

  private[this] def toModelProperty(descr: ClassDescriptor, position: Option[Int] = None, required: Boolean = true, description: Option[String] = None, allowableValues: String = "")(prop: PropertyDescriptor) = {
    val ctorParam = if (!prop.returnType.isOption) descr.mostComprehensive.find(_.name == prop.name) else None
    //    if (descr.simpleName == "Pet") println("converting property: " + prop)
    val mp = ModelProperty(
      DataType.fromScalaType(if (prop.returnType.isOption) prop.returnType.typeArgs.head else prop.returnType),
      if (position.isDefined && position.forall(_ >= 0)) position.get else ctorParam.map(_.argIndex).getOrElse(position.getOrElse(0)),
      required = required && !prop.returnType.isOption,
      description = description.flatMap(blankOpt(_)),
      allowableValues = convertToAllowableValues(allowableValues))
    //    if (descr.simpleName == "Pet") println("The property is: " + mp)
    prop.name -> mp
  }

//  def modelToSwagger(klass: ScalaType): Option[Model] = {
//    if (Reflector.isPrimitive(klass.erasure) || Reflector.isExcluded(klass.erasure, excludes.toSeq)) None
//    else {
//      val name = klass.simpleName
//
//      val descr = Reflector.describe(klass).asInstanceOf[ClassDescriptor]
//      val apiModel = Option(klass.erasure.getAnnotation(classOf[ApiModel]))
//
//      val fields = klass.erasure.getDeclaredFields.toList collect {
//        case f: Field if f.getAnnotation(classOf[ApiModelProperty]) != null =>
//          val annot = f.getAnnotation(classOf[ApiModelProperty])
//          val asModelProperty = toModelProperty(descr, Some(annot.position()), annot.required(), blankOpt(annot.description), annot.allowableValues())_
//          descr.properties.find(_.mangledName == f.getName) map asModelProperty
//
//        case f: Field =>
//          val asModelProperty = toModelProperty(descr)_
//          descr.properties.find(_.mangledName == f.getName) map asModelProperty
//
//      }
//
//      val result = apiModel map { am =>
//        Model(name, name, blankOpt(klass.fullName), properties = fields.flatten, baseModel = blankOpt(am.parent.getName), discriminator = blankOpt(am.discriminator) )
//      } orElse Some(Model(name, name, blankOpt(klass.fullName), properties = fields.flatten))
//      //      if (descr.simpleName == "Pet") println("The collected fields:\n" + result)
//      result
//    }
//  }

  private def convertToAllowableValues(csvString: String, paramType: String = null): AllowableValues = {
    if (csvString.toLowerCase.startsWith("range[")) {
      val ranges = csvString.substring(6, csvString.length() - 1).split(",")
      buildAllowableRangeValues(ranges, csvString, inclusive = true)
    } else if (csvString.toLowerCase.startsWith("rangeexclusive[")) {
      val ranges = csvString.substring(15, csvString.length() - 1).split(",")
      buildAllowableRangeValues(ranges, csvString, inclusive = false)
    } else {
      if (csvString.length == 0) {
        AllowableValues.AnyValue
      } else {
        val params = csvString.split(",").toList
        implicit val format = DefaultJsonFormats.GenericFormat(DefaultReaders.StringReader, DefaultWriters.StringWriter)
        paramType match {
          case null => AllowableValues.AllowableValuesList(params)
          case "string" => AllowableValues.AllowableValuesList(params)
        }
      }
    }
  }

  private def buildAllowableRangeValues(ranges: Array[String], inputStr: String, inclusive: Boolean = true): AllowableValues.AllowableRangeValues = {
    var min: java.lang.Float = 0
    var max: java.lang.Float = 0
    if (ranges.size < 2) {
      throw new RuntimeException("Allowable values format " + inputStr + "is incorrect")
    }
    if (ranges(0).equalsIgnoreCase("Infinity")) {
      min = Float.PositiveInfinity
    } else if (ranges(0).equalsIgnoreCase("-Infinity")) {
      min = Float.NegativeInfinity
    } else {
      min = ranges(0).toFloat
    }
    if (ranges(1).equalsIgnoreCase("Infinity")) {
      max = Float.PositiveInfinity
    } else if (ranges(1).equalsIgnoreCase("-Infinity")) {
      max = Float.NegativeInfinity
    } else {
      max = ranges(1).toFloat
    }
    val allowableValues =
      AllowableValues.AllowableRangeValues(if (inclusive) Range.inclusive(min.toInt, max.toInt) else Range(min.toInt, max.toInt))
    allowableValues
  }

}

/**
 * An instance of this class is used to hold the API documentation.
 */
class Swagger(val swaggerVersion: String,
              val apiVersion: String,
              val apiInfo: ApiInfo) extends SwaggerEngine[Api] with LazyLogging {


  /**
   * Registers the documentation for an API with the given path.
   */
//  def register(name: String,
//               resourcePath: String,
//               description: Option[String],
//               s: SwaggerSupportSyntax with SwaggerSupportBase,
//               consumes: List[String],
//               produces: List[String],
//               protocols: List[String],
//               authorizations: List[String]) = {

  def register(action: RhoAction[_ <: HList,_,_]) {
//    logger.debug("registering swagger api with: { name: %s, resourcePath: %s, description: %s, servlet: %s }" format (name, resourcePath, description, s.getClass))
//
//    val endpoints: List[Endpoint] = s.endpoints(resourcePath) collect { case m: Endpoint => m }
//    _docs += name -> Api(
//      apiVersion,
//      swaggerVersion,
//      resourcePath,
//      description,
//      (produces ::: endpoints.flatMap(_.operations.flatMap(_.produces))).distinct,
//      (consumes ::: endpoints.flatMap(_.operations.flatMap(_.consumes))).distinct,
//      (protocols ::: endpoints.flatMap(_.operations.flatMap(_.protocols))).distinct,
//      endpoints,
//      s.models.toMap,
//      (authorizations ::: endpoints.flatMap(_.operations.flatMap(_.authorizations))).distinct,
//      0)

    /** What will be considered an 'API'? Will it be the last PathMatch?
      * TODO:
      * * The path needs to be broken down to the API endpoint and a
      *    basic Operation should be passed to the getHeaderRules method
      *    These could also be rethough altogether.
      *
      * * Rest path params belong in the Operation parameters list
      *
     *
     */

    println(action.hf.encodings)

    val router = action.router

    val api = Api(apiVersion, swaggerVersion, "")
    val respClass = action.responseType.map(DataType.fromManifest(_)).getOrElse(DataType.Void)

    getPaths(api, router.method, respClass, router.path).foreach{ api =>
      val _endpoints = if (api.apis.isEmpty) {
        val op = Operation(action.method, respClass, "", nickname = "nick")
        List(Endpoint(api.resourcePath, api.description, getHeaderRules(router.validators::Nil, op)))
      } else api.apis.map { endpoint =>
        endpoint.copy(operations = endpoint.operations.flatMap(getHeaderRules(router.validators::Nil, _)))
      }

      val api2 = api.copy(produces = _endpoints.flatMap(_.operations.flatMap(_.produces)),
                          consumes = _endpoints.flatMap(_.operations.flatMap(_.consumes)),
                          apis = _endpoints)

      _docs += api2.resourcePath -> api2
    }
  }

  private def getHeaderRules(rules: List[HeaderRule[_ <: HList]], op: Operation): List[Operation] = rules match {
    case head::rules => head match {
      case HeaderAnd(a, b) => getHeaderRules(a::b::rules, op)

      case HeaderOr(a, b) => getHeaderRules(a::rules, op):::getHeaderRules(b::rules, op)

      case HeaderCapture(key) =>
        val p = Parameter(key.name.toString, DataType.Void, paramType = ParamType.Header)
        getHeaderRules(rules, op.copy(parameters = op.parameters:+ p))

      case HeaderRequire(key, _) => getHeaderRules(HeaderCapture(key)::rules, op)

      case HeaderMapper(key, _) => getHeaderRules(HeaderCapture(key)::rules, op)

//      case r@ QueryRule(name, parser) =>
//        val p = Parameter(name, DataType.fromManifest(r.m), paramType = ParamType.Query)
//        getHeaderRules(rules, op.copy(parameters = op.parameters :+ p))

      case EmptyHeaderRule => getHeaderRules(rules, op)
    }

    case Nil => op::Nil // finished
  }

  private def getPaths(api: Api, method: Method, responseType: DataType, path: PathRule[_ <: HList]): Seq[Api] = {

    def mergeDescriptions(doc1: Option[String], doc2: Option[String]) = (doc1, doc2) match {
      case (Some(d1), Some(d2)) => Some(d1 + "; " + d2)
      case (s@ Some(_), None)   => s
      case (None, s@ Some(_))   => s
      case _                    => None
    }

    def mergeApis(apis: List[Api], api: Api): List[Api] = ???

    def goPath(stack: List[PathRule[_ <: HList]], api: Api, end: Option[Endpoint]): List[Api] =  stack match {
      case head::stack => head match {
        case PathAnd(a, b) => goPath(a::b::stack, api, end)
        case PathOr(a, b) => // need to split and then merge the APIs

          val apis1 = goPath(a::stack, api, end)
          val apis2 = goPath(b::stack, api, end)

          apis1.foldLeft(apis2)((apis, api) => mergeApis(apis, api))

        case PathMatch(name, docs) =>
          val _path = "/" + name
          end match {
            case Some(end) =>
              val e2 = end.copy(end.path + _path, description = mergeDescriptions(end.description, docs))
              goPath(stack, api, Some(e2))

            case None =>
              goPath(stack, api.copy(resourcePath = api.resourcePath + "/" + name,
                description = mergeDescriptions(api.description, docs)), end)
          }


        case c@ PathCapture(p, docs) =>
          val dtype = p.manifest.map(DataType.fromManifest(_)).getOrElse(DataType.Void)
          val docstr = docs.getOrElse(dtype.name)
          end match {
          case Some(end) => // Already making endpoints
            val newpath = s"${end.path}/{$docstr}"
            val param = Parameter(s"$docstr", dtype, paramType = ParamType.Path)
            val op = end.operations.head
            val newop = op.copy(parameters = op.parameters:+ param)
            val newEnd = end.copy(path = newpath, operations = newop::Nil)
            goPath(stack, api, Some(newEnd))

          case None =>  // We now are at the point to make endpoints
            val newpath = s"${api.resourcePath}/{${docs.getOrElse(dtype.name)}}"
            val param = Parameter(s"${docs.getOrElse(dtype.name)}", dtype, paramType = ParamType.Path)
            val op = Operation(method, responseType, "", parameters = param::Nil, nickname = "nick")
            val newEnd = Endpoint(path = newpath, operations = op::Nil)

            goPath(stack, api, Some(newEnd))
        }

        case CaptureTail(doc) =>

          val param = Parameter("...", DataType.GenList(DataType.String), paramType = ParamType.Path)
          end match {
          case Some(end) =>
            val newpath = s"${end.path}/..."
            val op = end.operations.head
            val newop = op.copy(parameters = op.parameters:+ param)
            val newEnd = end.copy(path = newpath, operations = newop::Nil)
            goPath(stack, api, Some(newEnd))

          case None =>
            val newpath = s"${api.resourcePath}/..."
            val op = Operation(method, responseType, "", parameters = param::Nil, nickname = "nick")
            val newEnd = Endpoint(path = newpath, operations = op::Nil)
            goPath(stack, api, Some(newEnd))
        }

        case PathEmpty => goPath(stack, api, end)

        case PathDescription(s) =>
          goPath(stack, api.copy(description = mergeDescriptions(api.description, Some(s))), end)

        case _: MetaData => goPath(stack, api, end)
      }

      case Nil => end match { // Finished
        case Some(end) => api.copy(apis = end::Nil)::Nil
        case None => api::Nil
      }
    }

    goPath(path::Nil, api, None)
  }
}

trait SwaggerApi[T <: SwaggerEndpoint[_]] {

  def apiVersion: String
  def swaggerVersion: String
  def resourcePath: String
  def description: Option[String]
  def produces: List[String]
  def consumes: List[String]
  def protocols: List[String]
  def authorizations: List[String]
  def position: Int
  def apis: List[T]
  def models: Map[String, Model]

  def model(name: String) = models.get(name)
}

case class ResourceListing(
                            apiVersion: String,
                            swaggerVersion: String = Swagger.SpecVersion,
                            apis: List[ApiListingReference] = Nil,
                            authorizations: List[AuthorizationType] = Nil,
                            info: Option[ApiInfo] = None)

case class ApiListingReference(path: String, description: Option[String] = None, position: Int = 0)

case class Api(apiVersion: String,
               swaggerVersion: String,
               resourcePath: String,
               description: Option[String] = None,
               produces: List[String] = Nil,
               consumes: List[String] = Nil,
               protocols: List[String] = Nil,
               apis: List[Endpoint] = Nil,
               models: Map[String, Model] = Map.empty,
               authorizations: List[String] = Nil,
               position: Int = 0) extends SwaggerApi[Endpoint] {
}

object ParamType extends Enumeration {
  type ParamType = Value

  /** A parameter carried in a POST body. **/
  val Body = Value("body")

  /** A parameter carried on the query string.
    *
    * E.g. http://example.com/foo?param=2
    */
  val Query = Value("query")

  /** A path parameter mapped to a Scalatra route.
    *
    * E.g. http://example.com/foo/2 where there's a route like
    * get("/foo/:id").
    */
  val Path = Value("path")

  /** A parameter carried in an HTTP header. **/
  val Header = Value("header")

  val File = Value("file")

  val Form = Value("form")
}

sealed trait DataType {
  def name: String
}
object DataType {

  case class ValueDataType(name: String, format: Option[String] = None, qualifiedName: Option[String] = None) extends DataType
  case class ContainerDataType(name: String, typeArg: Option[DataType] = None, uniqueItems: Boolean = false) extends DataType

  val Void = DataType("void")
  val String = DataType("string")
  val Byte = DataType("string", Some("byte"))
  val Int = DataType("integer", Some("int32"))
  val Long = DataType("integer", Some("int64"))
  val Float = DataType("number", Some("float"))
  val Double = DataType("number", Some("double"))
  val Boolean = DataType("boolean")
  val Date = DataType("string", Some("date"))
  val DateTime = DataType("string", Some("date-time"))

  object GenList {
    def apply(): DataType = ContainerDataType("List")
    def apply(v: DataType): DataType = new ContainerDataType("List", Some(v))
  }

  object GenSet {
    def apply(): DataType = ContainerDataType("Set", uniqueItems = true)
    def apply(v: DataType): DataType = new ContainerDataType("Set", Some(v), uniqueItems = true)
  }

  object GenArray {
    def apply(): DataType = ContainerDataType("Array")
    def apply(v: DataType): DataType = new ContainerDataType("Array", Some(v))
  }

  def apply(name: String, format: Option[String] = None, qualifiedName: Option[String] = None) =
    new ValueDataType(name, format, qualifiedName)
  def apply[T](implicit mf: Manifest[T]): DataType = fromManifest[T](mf)

  private[this] val StringTypes = Set[Class[_]](classOf[String],classOf[java.lang.String])
  private[this] def isString(klass: Class[_]) = StringTypes contains klass
  private[this] val BoolTypes = Set[Class[_]](classOf[Boolean],classOf[java.lang.Boolean])
  private[this] def isBool(klass: Class[_]) = BoolTypes contains klass

  private[swagger] def fromManifest[T](implicit mf: Manifest[T]): DataType = fromScalaType(Reflector.scalaTypeOf[T])

  private[swagger] def fromClass(klass: Class[_]): DataType = fromScalaType(Reflector.scalaTypeOf(klass))
  private[swagger] def fromScalaType(st: ScalaType): DataType = {
    val klass = if (st.isOption && st.typeArgs.size > 0) st.typeArgs.head.erasure else st.erasure
    if (classOf[Unit].isAssignableFrom(klass) || classOf[Void].isAssignableFrom(klass)) this.Void
    else if (isString(klass)) this.String
    else if (classOf[Byte].isAssignableFrom(klass) || classOf[java.lang.Byte].isAssignableFrom(klass)) this.Byte
    else if (classOf[Long].isAssignableFrom(klass) || classOf[java.lang.Long].isAssignableFrom(klass)) this.Long
    else if (isInt(klass)) this.Int
    else if (classOf[Float].isAssignableFrom(klass) || classOf[java.lang.Float].isAssignableFrom(klass)) this.Float
    else if (isDecimal(klass)) this.Double
    else if (isDate(klass)) this.Date
    else if (isDateTime(klass)) this.DateTime
    else if (isBool(klass)) this.Boolean
    else if (classOf[scala.collection.Set[_]].isAssignableFrom(klass) || classOf[java.util.Set[_]].isAssignableFrom(klass)) {
      if (st.typeArgs.nonEmpty) GenSet(fromScalaType(st.typeArgs.head))
      else GenSet()
    }
    else if (classOf[collection.Seq[_]].isAssignableFrom(klass) || classOf[java.util.List[_]].isAssignableFrom(klass)) {
      if (st.typeArgs.nonEmpty) GenList(fromScalaType(st.typeArgs.head))
      else GenList()
    }
    else if (st.isArray || isCollection(klass)) {
      if (st.typeArgs.nonEmpty) GenArray(fromScalaType(st.typeArgs.head))
      else GenArray()
    }
    else {
      val stt = if (st.isOption) st.typeArgs.head else st
      new ValueDataType(stt.simpleName, qualifiedName = Option(stt.fullName))
    }
  }

  private[this] val IntTypes =
    Set[Class[_]](classOf[Int], classOf[java.lang.Integer], classOf[Short], classOf[java.lang.Short], classOf[BigInt], classOf[java.math.BigInteger])
  private[this] def isInt(klass: Class[_]) = IntTypes.contains(klass)

  private[this] val DecimalTypes =
    Set[Class[_]](classOf[Double], classOf[java.lang.Double], classOf[BigDecimal], classOf[java.math.BigDecimal])
  private[this] def isDecimal(klass: Class[_]) = DecimalTypes contains klass

  private[this] val DateTypes =
    Set[Class[_]](classOf[DateMidnight])
  private[this] def isDate(klass: Class[_]) = DateTypes.exists(_.isAssignableFrom(klass))
  private[this] val DateTimeTypes =
    Set[Class[_]](classOf[JDate], classOf[DateTime])
  private[this] def isDateTime(klass: Class[_]) = DateTimeTypes.exists(_.isAssignableFrom(klass))

  private[this] def isCollection(klass: Class[_]) =
    classOf[collection.Traversable[_]].isAssignableFrom(klass) ||
      classOf[java.util.Collection[_]].isAssignableFrom(klass)

}

case class ApiInfo(
                    title: String,
                    description: String,
                    termsOfServiceUrl: String,
                    contact: String,
                    license: String,
                    licenseUrl: String)


trait AllowableValues

object AllowableValues {
  case object AnyValue extends AllowableValues
  case class AllowableValuesList[T](values: List[T]) extends AllowableValues
  case class AllowableRangeValues(values: Range) extends AllowableValues

  def apply(): AllowableValues = empty
  def apply[T](values: T*): AllowableValues = apply(values.toList)
  def apply[T](values: List[T]): AllowableValues = AllowableValuesList(values)
  def apply(values: Range): AllowableValues = AllowableRangeValues(values)
  def empty = AnyValue
}

case class Parameter(name: String,
                     `type`: DataType,
                     description: Option[String] = None,
                     notes: Option[String] = None,
                     paramType: ParamType.ParamType,
                     defaultValue: Option[String] = None,
                     allowableValues: AllowableValues = AllowableValues.AnyValue,
                     required: Boolean = true,
                     //                     allowMultiple: Boolean = false,
                     paramAccess: Option[String] = None,
                     position: Int = 0)

case class ModelProperty(`type`: DataType,
                         position: Int = 0,
                         required: Boolean = false,
                         description: Option[String] = None,
                         allowableValues: AllowableValues = AllowableValues.AnyValue,
                         items: Option[ModelRef] = None)


case class Model(id: String,
                 name: String,
                 qualifiedName: Option[String] = None,
                 description: Option[String] = None,
                 properties: List[(String, ModelProperty)] = Nil,
                 baseModel: Option[String] = None,
                 discriminator: Option[String] = None) {

  def setRequired(property: String, required: Boolean): Model = {
    val prop = properties.find(_._1 == property).get
    copy(properties = (property -> prop._2.copy(required = required)) :: properties)
  }
}


case class ModelRef(
                     `type`: String,
                     ref: Option[String] = None,
                     qualifiedType: Option[String] = None)

case class LoginEndpoint(url: String)
case class TokenRequestEndpoint(url: String, clientIdName: String, clientSecretName: String)
case class TokenEndpoint(url: String, tokenName: String)


trait AuthorizationType {
  def `type`: String
}
case class OAuth(
                  scopes: List[String],
                  grantTypes: List[GrantType]) extends AuthorizationType {
  override val `type` = "oauth2"
}
case class ApiKey(keyname: String, passAs: String = "header") extends AuthorizationType {
  override val `type` = "apiKey"
}

trait GrantType {
  def `type`: String
}
case class ImplicitGrant(
                          loginEndpoint: LoginEndpoint,
                          tokenName: String) extends GrantType {
  def `type` = "implicit"
}
case class AuthorizationCodeGrant(
                                   tokenRequestEndpoint: TokenRequestEndpoint,
                                   tokenEndpoint: TokenEndpoint) extends GrantType {
  def `type` = "authorization_code"
}
trait SwaggerOperation {
  def method: Method
  def responseClass: DataType
  def summary: String
  def nickname: String
  def notes: Option[String]
  def deprecated: Boolean
  def produces: List[String]
  def consumes: List[String]
  def protocols: List[String]
  def authorizations: List[String]
  def parameters: List[Parameter]
  def responseMessages: List[ResponseMessage[_]]
  def position: Int
}
case class Operation(method: Method,
                     responseClass: DataType,
                     summary: String,
                     nickname: String,
                     position: Int = 0,
                     notes: Option[String] = None,
                     deprecated: Boolean = false,
                     parameters: List[Parameter] = Nil,
                     responseMessages: List[ResponseMessage[_]] = Nil,
                     //                     supportedContentTypes: List[String] = Nil,
                     consumes: List[String] = Nil,
                     produces: List[String] = Nil,
                     protocols: List[String] = Nil,
                     authorizations: List[String] = Nil) extends SwaggerOperation


trait SwaggerEndpoint[T <: SwaggerOperation] {
  def path: String
  def description: Option[String]
  def operations: List[T]
}

case class Endpoint(path: String,
                    description: Option[String] = None,
                    operations: List[Operation] = Nil) extends SwaggerEndpoint[Operation]

trait ResponseMessage[T] {
  def code: Int
  def message: T
}
case class StringResponseMessage(code: Int, message: String) extends ResponseMessage[String]
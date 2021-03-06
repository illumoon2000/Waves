package com.wavesplatform.http

import com.wavesplatform.http.ApiMarshallers._
import com.wavesplatform.{TestWallet, TransactionGen, UtxPool}
import org.scalacheck.Shrink
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.PropertyChecks
import play.api.libs.json.{JsObject, Json}
import scorex.api.http.{ApiKeyNotValid, PaymentApiRoute}
import scorex.transaction.assets.TransferTransaction
import scorex.utils.Time

class PaymentRouteSpec extends RouteSpec("/payment")
  with MockFactory with PropertyChecks with RestAPISettingsHelper with TestWallet with TransactionGen {

  private val utx = stub[UtxPool]
  (utx.putIfNew _).when(*, *).onCall((t, _) => Right(t)).anyNumberOfTimes()
  private implicit def noShrink[A]: Shrink[A] = Shrink(_ => Stream.empty)

  "accepts payments" in {
    forAll(accountOrAliasGen.label("recipient"), positiveLongGen.label("amount"), smallFeeGen.label("fee")) {
      case (recipient, amount, fee) =>

        val timestamp = System.currentTimeMillis()
        val time = mock[Time]
        (time.getTimestamp _).expects().returns(timestamp).anyNumberOfTimes()

        val sender = testWallet.privateKeyAccounts().head
        val tx = TransferTransaction.create(None, sender, recipient, amount, timestamp, None, fee, Array())

        val route = PaymentApiRoute(restAPISettings, testWallet, utx, time).route

        val req = Json.obj("sender" -> sender.address, "recipient" -> recipient.stringRepr, "amount" -> amount, "fee" -> fee)

        Post(routePath(""), req) ~> route should produce(ApiKeyNotValid)
        Post(routePath(""), req) ~> api_key(apiKey) ~> route ~> check {
          val resp = responseAs[JsObject]

          (resp \ "id").as[String] shouldEqual tx.right.get.id.toString
          (resp \ "assetId").asOpt[String] shouldEqual None
          (resp \ "feeAsset").asOpt[String] shouldEqual None
          (resp \ "type").as[Int] shouldEqual 4
          (resp \ "fee").as[Int] shouldEqual fee
          (resp \ "amount").as[Long] shouldEqual amount
          (resp \ "timestamp").as[Long] shouldEqual tx.right.get.timestamp
          (resp \ "sender").as[String] shouldEqual sender.address
          (resp \ "recipient").as[String] shouldEqual recipient.stringRepr
        }
    }
  }
}

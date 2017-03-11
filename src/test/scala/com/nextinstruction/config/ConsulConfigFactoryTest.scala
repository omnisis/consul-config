package com.nextinstruction.config

import java.nio.charset.Charset

import com.ecwid.consul.transport.RawResponse
import com.ecwid.consul.v1.kv.model.GetBinaryValue
import com.ecwid.consul.v1.{ConsulClient, Response}
import com.typesafe.config._
import org.junit.runner.RunWith
import org.mockito.Mockito.{when, _}
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class ConsulConfigFactoryTest extends FlatSpec with Matchers {
  import ConsulConfigFactoryTest._

  import scala.collection.JavaConverters._
  

  "ConsulConfigFactory" should "try to load all given config paths" in {
    val client = mock(classOf[ConsulClient], withSettings()
          .defaultAnswer(RETURNS_DEFAULTS))

    when(client.getKVBinaryValue("conf1"))
      .thenReturn(fakeResponse(contentsOfFile("conf1.conf").mkString))

    when(client.getKVBinaryValue("conf2"))
      .thenReturn(fakeResponse(contentsOfFile("conf2.conf").mkString))

    val consulConfigFactory = new ConsulConfigFactory(client)

    val bootstrap = ConfigFactory.parseResources("application.conf")
    Seq("conf1","conf2") shouldEqual bootstrap.getStringList("consul.configkeys").asScala
    
    consulConfigFactory.doLoad(Thread.currentThread().getContextClassLoader, bootstrap)

    verify(client).getKVBinaryValue("conf1")
    verify(client).getKVBinaryValue("conf2")

  }


  it should "apply config values in first-come, first-server manner" in {
    val client = mock(classOf[ConsulClient], withSettings()
      .defaultAnswer(RETURNS_DEFAULTS))

    when(client.getKVBinaryValue("conf1"))
      .thenReturn(fakeResponse(contentsOfFile("conf1.conf").mkString))

    when(client.getKVBinaryValue("conf2"))
      .thenReturn(fakeResponse(contentsOfFile("conf2.conf").mkString))

    val consulConfigFactory = new ConsulConfigFactory(client)

    val config = ConfigFactory.load()
    Seq("conf1","conf2") shouldEqual config.getStringList("consul.configkeys").asScala

    val loadedConf = consulConfigFactory.doLoad(Thread.currentThread().getContextClassLoader, config)

    verify(client).getKVBinaryValue("conf1")
    verify(client).getKVBinaryValue("conf2")

    loadedConf.getString("app.name") shouldBe "CoolApp"
    loadedConf.getString("app.description") shouldBe "A Cool App"
    loadedConf.getString("app.env") shouldBe "dev"

  }


}

object  ConsulConfigFactoryTest {
  import scala.io.Source._

  def contentsOfFile(filename: String) : String = {
    val is = getClass.getClassLoader.getResourceAsStream(filename)
    if (is == null) {
      throw new IllegalStateException("Can't find file: " + filename)
    }
    fromInputStream(is).mkString
  }
  

  def fakeResponse(s: String) : Response[GetBinaryValue] = {
    val resp = new GetBinaryValue()
    resp.setValue(s.getBytes(Charset.forName("UTF-8")))
    new Response(resp, new RawResponse(200,"ok","",1L,false,System.currentTimeMillis()))
  }
  
}
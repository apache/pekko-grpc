package helloworld

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

class GreeterServiceImpl extends GreeterService {
  override def sayHello(in: HelloRequest): Future[HelloReply] = ???
  override def sayHelloA(in: a.HelloRequest): Future[HelloReply] = ???
  override def sayHelloB(in: b.HelloRequest): Future[HelloReply] = ???
}

package helloworld;

import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import helloworld.Helloworld.*;

class GreeterServiceImpl implements GreeterService {
  public CompletionStage<HelloReply> sayHello(HelloRequest request) {
    throw new UnsupportedOperationException();
  }
  public CompletionStage<HelloReply> sayHelloA(a.Other.HelloRequest request) {
    throw new UnsupportedOperationException();
  }
  public CompletionStage<HelloReply> sayHelloB(b.Other.HelloRequest request) {
    throw new UnsupportedOperationException();
  }
}

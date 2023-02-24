package ch.megard.akka.http.cors.javadsl.model;

import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher$;
import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.javadsl.model.headers.HttpOrigin;

public abstract class HttpOriginMatcher {

    public abstract boolean matches(HttpOrigin origin);

    public static HttpOriginMatcher ALL = ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher.$times$.MODULE$;

    public static HttpOriginMatcher create(HttpOrigin... origins) {
        return HttpOriginMatcher$.MODULE$.apply(Util.<HttpOrigin, org.apache.pekko.http.scaladsl.model.headers.HttpOrigin>convertArray(origins));
    }

    public static HttpOriginMatcher strict(HttpOrigin... origins) {
        return HttpOriginMatcher$.MODULE$.strict(Util.<HttpOrigin, org.apache.pekko.http.scaladsl.model.headers.HttpOrigin>convertArray(origins));
    }

}

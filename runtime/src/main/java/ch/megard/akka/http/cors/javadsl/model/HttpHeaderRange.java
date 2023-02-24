package ch.megard.akka.http.cors.javadsl.model;

import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange$;
import org.apache.pekko.http.impl.util.Util;


/**
 * @see HttpHeaderRanges for convenience access to often used values.
 */
public abstract class HttpHeaderRange {
    public abstract boolean matches(String header);

    public static HttpHeaderRange create(String... headers) {
        return HttpHeaderRange$.MODULE$.apply(Util.convertArray(headers));
    }
}

package daggerok

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.http.HttpServletRequest
import kotlin.Long.Companion.MAX_VALUE

val log = LoggerFactory.getLogger(Rest::class.java)!!

@Configuration
class SseConfig {

  @Bean
  fun appSseEmitter() = SseEmitter(MAX_VALUE)

  @Bean
  fun sseRepository(): ConcurrentHashMap<String, ConcurrentHashMap<String, SseEmitter>> {
    val feed = ConcurrentHashMap<String, ConcurrentHashMap<String, SseEmitter>>()
    val subscriptions = ConcurrentHashMap<String, SseEmitter>()
    subscriptions["app"] = appSseEmitter()
    feed.putIfAbsent("messages", subscriptions)
    return feed
  }
}

@RestController
@RequestMapping(produces = [(MediaType.TEXT_EVENT_STREAM_VALUE)])
class Rest(val sseRepository: ConcurrentHashMap<String, ConcurrentHashMap<String, SseEmitter>>) {

  @GetMapping(path = ["/sse"], produces = [(MediaType.TEXT_EVENT_STREAM_VALUE)])
  fun sse(httpRequest: HttpServletRequest,
          @RequestParam(required = false, name = "topic", defaultValue = "messages") topic: String): SseEmitter {

    sseRepository.putIfAbsent(topic, ConcurrentHashMap())

    val subscriptions = sseRepository[topic] as ConcurrentHashMap
    val sessionId = httpRequest.session.id ?: "stateless"
    val emitters = subscriptions
        .filter { it.key == sessionId }
        .map { it.value }

    if (emitters.isNotEmpty()) {
      return emitters.first()
    }

    println("new incoming connection:")
    httpRequest.headerNames
        .toList()
        .map { (it to httpRequest.getHeader(it)) }
        .onEach { println(it) }

    val subscription = SseEmitter(MAX_VALUE)
    subscription.onCompletion { subscriptions.remove(sessionId) }
    subscription.onTimeout { subscriptions.remove(sessionId) }
    subscriptions.putIfAbsent(sessionId, subscription)
    return subscription
  }

  // there is no sense do that with @Async annotated mappings
  private final inline fun tryOrRemove(action: () -> Unit, onFailure: () -> Unit) {
    try {
      action()
    } catch (t: Throwable) {
      onFailure()
    }
  }

  //@Async
  @PostMapping("/feed")
  fun sendMessage(httpRequest: HttpServletRequest,
                  @RequestBody message: Message,
                  @RequestParam(required = false, name = "topic", defaultValue = "messages") topic: String) {

    val sessionId = if (httpRequest.session == null) "stateless" else httpRequest.session.id
    val all = sseRepository[topic] as ConcurrentHashMap
    val other = all.filterNot { it.key == sessionId }

    other
        .forEach { client ->
          tryOrRemove({
            client.value.send(message.withSessionId(sessionId))
          }) {
            sseRepository.values.forEach { all ->
              all.remove(client.key)
              log.warn("removed session {}", client.key)
            }
          }
        }
  }
}

data class Message(val body: String? = null, val sessionId: String? = null)

fun Message.withSessionId(sessionId: String) =
    Message(body = this.body, sessionId = sessionId)

@Controller
class Web {

  @GetMapping(path = ["", "/"])
  fun index() = "index"
}

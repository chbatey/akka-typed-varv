package info.batey.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import org.immutables.value.Value;

public class HelloWorld {

   @Value.Immutable
   public interface Greet {
      String whom();
      ActorRef<Greeted> replyTo();
   }

   @Value.Immutable
   public interface Greeted {
      String whom();
      ActorRef<Greet> by();
   }

   public static final Behavior<Greet> greeter =
      Behaviors.setup(context ->
         Behaviors.receiveMessage(
            message -> {
               context.getLog().info("Hello {}!", message.whom());
               var reply = ImmutableGreeted.builder().whom(message.whom()).by(context.getSelf()).build();
               message.replyTo().tell(reply);
               return Behaviors.same();
            }));
}

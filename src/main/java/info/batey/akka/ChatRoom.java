package info.batey.akka;

import akka.actor.typed.Terminated;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import io.vavr.collection.List;
import org.immutables.value.Value;

import static io.vavr.Predicates.instanceOf;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static io.vavr.API.*;

public class ChatRoom {

   interface RoomCommand {}
   @Value.Immutable
   public interface GetSession extends RoomCommand {
      String screenName();
      ActorRef<SessionEvent> replyTo();

   }
   @Value.Immutable
   public interface PublishSessionMessage extends RoomCommand {
      String screenName();
      String message();
   }

   interface SessionEvent {}
   @Value.Immutable
   interface SessionGranted extends SessionEvent {
      ActorRef<PostMessage> handle();
   }
   @Value.Immutable
   interface SessionDenied extends SessionEvent {
      String reason();
   }
   @Value.Immutable
   public interface MessagePosted extends SessionEvent {
      String screenName();
      String message();
   }

   interface SessionCommand {}
   @Value.Immutable
   public interface PostMessage extends SessionCommand {
      String message();
   }
   @Value.Immutable
   interface NotifyClient extends SessionCommand {
      MessagePosted message();
   }


   public static Behavior<RoomCommand> behavior() {
      return chatRoom(List.empty());
   }

   private static Behavior<RoomCommand> chatRoom(List<ActorRef<SessionCommand>> sessions) {
      return Behaviors.setup(context -> Behaviors.receiveMessage(message -> Match(message).of(
         Case($(instanceOf(GetSession.class)), getSession -> {
            ActorRef<SessionEvent> client = getSession.replyTo();
            ActorRef<SessionCommand> ses =
               context.spawn(
                  session(context.getSelf(), getSession.screenName(), client),
                  URLEncoder.encode(getSession.screenName(), StandardCharsets.UTF_8));
            // narrow to only expose PostMessage
            client.tell(ImmutableSessionGranted.builder().handle(ses.narrow()).build());
            List<ActorRef<SessionCommand>> newSessions = sessions.prepend(ses);
            return chatRoom(newSessions);
         }),
         Case($(instanceOf(PublishSessionMessage.class)), pub -> {
            NotifyClient notification =
               ImmutableNotifyClient.builder().message((ImmutableMessagePosted.builder().screenName(pub.screenName()).message(pub.message()).build())).build();
            sessions.forEach(s -> s.tell(notification));
            return Behaviors.same();
         }))));
   }

   static Behavior<ChatRoom.SessionCommand> session(ActorRef<RoomCommand> room, String screenName, ActorRef<SessionEvent> client) {
      return Behaviors.setup(context -> Behaviors.receiveMessage(message -> Match(message).of(
         Case($(instanceOf(PostMessage.class)), post -> {
            // from client, publish to others via the room
            room.tell(ImmutablePublishSessionMessage.builder().screenName(screenName).message(post.message()).build());
            return Behaviors.same();
         }),
         Case($(instanceOf(NotifyClient.class)), notification -> {
            // published from the room
            client.tell(notification.message());
            return Behaviors.same();
         })
      )));
   }

   public abstract static class Gabbler {

      public static Behavior<ChatRoom.SessionEvent> behavior() {
         return Behaviors.setup(context -> Behaviors.receiveMessage(m -> Match(m).of(
            Case($(instanceOf(SessionDenied.class)), denied -> {
               context.getLog().info("cannot start chat room session: " + denied.reason());
               return Behaviors.stopped();
            }),
            Case($(instanceOf(SessionGranted.class)), granted -> {
               granted.handle().tell(ImmutablePostMessage.builder().message("Hello World!").build());
               return Behaviors.same();
            }),
            Case($(instanceOf(MessagePosted.class)), message -> {
               context.getLog().info(
                  "message has been posted by '" + message.screenName() + "': " + message.message());
               return Behaviors.stopped();
            })
         )));
      }
   }

   static ActorSystem<Void> runChatRoom() {
      Behavior<Void> main =
         Behaviors.setup(
            context -> {
               ActorRef<ChatRoom.RoomCommand> chatRoom =
                  context.spawn(ChatRoom.behavior(), "chatRoom");
               ActorRef<ChatRoom.SessionEvent> gabbler =
                  context.spawn(Gabbler.behavior(), "gabbler");
               context.watch(gabbler);
               chatRoom.tell(ImmutableGetSession.builder().screenName("ol’ Gabbler").replyTo( gabbler).build());

               return Behaviors.<Void>receiveSignal(
                  (c, sig) -> Match(sig).of(
                     Case($(instanceOf(Terminated.class)), t -> Behaviors.stopped()),
                     Case($(), Behaviors.unhandled())
                  ));
            });

      return ActorSystem.create(main, "ChatRoomDemo");
   }

   public static void main(String[] args) {
      runChatRoom();
   }
}

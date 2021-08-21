import SampleActor.Commands.TestMe
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

object SampleActor {

  private val entityType = "SampleActor"

  sealed trait Command
  object Commands {
    case class TestMe(isSuccess: Boolean, replyTo: ActorRef[TestMe.Result]) extends Command
    case object TestMe {
      sealed trait Result
      object Results {
        case object TestSuccess extends Result
        case object TestFail extends Result
      }
    }
  }

  sealed trait Event

  case class Snapshot(id: String)
  sealed trait State  {
    def snapshot: Snapshot
    def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State]
    def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State
  }

  case class EmptySampleActor(id: String) extends State {
    override def snapshot: Snapshot = throw new IllegalStateException(s"EmptyShortLink[$id] has not state snapshot yet.")

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State] = cmd match {
      case c: TestMe =>
        Effect.reply(c.replyTo)(if (c.isSuccess) TestMe.Results.TestSuccess else TestMe.Results.TestFail)
      case c =>
        context.log.warn("{}[id={}, state=Empty] received unknown command[{}].", entityType, id, c)
        Effect.stop()
          .thenNoReply()
    }

    override def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State = event match {
      case e =>
        context.log.warn(s"{}[id={}, state=Empty] received unexpected event[{}]", entityType, id, e)
        state
    }
  }

  def apply(id: String): Behavior[Command] = Behaviors.setup { implicit context =>
    context.log.debug2("Starting entity actor {}[id={}]", entityType, id)
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      PersistenceId.of("ShortLink", id),
      EmptySampleActor(id),
      (state, cmd) => {
        context.log.debug("{}[id={}] receives command {}", entityType, id, cmd)
        state.applyCommand(cmd)
      },
      (state, event) => {
        context.log.debug("{}[id={}] persists event {}", entityType, id, event)
        state.applyEvent(state, event)
      }
    )
  }
}
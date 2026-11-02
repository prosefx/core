package prosefx.core.commands;

import com.atlassian.prosemirror.state.*;
import java.util.function.Consumer;
import kotlin.Unit;
import kotlin.jvm.functions.*;

/** A ProseMirror command: with dispatch null it only answers whether it would apply. */
@FunctionalInterface
public interface Command {

    boolean apply(PMEditorState state, Consumer<Transaction> dispatch);

    default boolean applies(PMEditorState state) { return apply(state, null); }

    static Command chain(Command... commands) {
        return (state, dispatch) -> {
            for (var c : commands) if (c.apply(state, dispatch)) return true;
            return false;
        };
    }

    static Command fromKotlin(Function2<PMEditorState, Function1<? super Transaction, Unit>, Boolean> k) {
        return (state, dispatch) -> k.invoke(state, dispatch == null ? null : tr -> { dispatch.accept(tr); return Unit.INSTANCE; });
    }
}
